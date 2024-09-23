package se.datasektionen.mc.metacraft_core.entity.entities.player_mob;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Dynamic;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.pathing.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.entity.TridentUser;
import se.datasektionen.mc.metacraft_core.mixin.AccessorEntityNavigation;
import se.datasektionen.mc.metacraft_core.mixin.AccessorMobEntity;
import se.datasektionen.mc.metacraft_core.mixin.AccessorPlayerEntity;
import se.datasektionen.mc.metacraft_core.util.helper.EntityAIHelper;
import se.datasektionen.mc.metacraft_core.util.helper.ServerDefaultSkinHelper;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorServerChunkLoadingManager;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PlayerMob extends HostileEntity implements PolymerEntity, CrossbowUser, TridentUser {

	private static final double BASE_SPEED = 0.4;

	protected static final TrackedData<Byte> PLAYER_MODEL_PARTS = DataTracker.registerData(PlayerMob.class, TrackedDataHandlerRegistry.BYTE);
	protected static final TrackedData<NbtCompound> LEFT_SHOULDER_ENTITY = DataTracker.registerData(PlayerMob.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	protected static final TrackedData<NbtCompound> RIGHT_SHOULDER_ENTITY = DataTracker.registerData(PlayerMob.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

	private static final String PROFILE = "profile";
	private static final String VISIBLE_SKIN_PARTS = "visible_skin_parts";
	private static final String SHOULDER_ENTITY_LEFT = "ShoulderEntityLeft";
	private static final String SHOULDER_ENTITY_RIGHT = "ShoulderEntityRight";
	private static final String CAN_WANDER = "can_wander";
	private GameProfile profile;

	private Set<ServerPlayerEntity> sendRemovePacketTo = new HashSet<>();

	private FakePlayer fakePlayer;

	private boolean canWander = true;

	private boolean shouldRespawnClient = false;

	private final SwimNavigation waterNavigation = new SwimNavigation(this, getWorld()) {
		@Override
		protected PathNodeNavigator createPathNodeNavigator(int range) {
			this.nodeMaker = new WaterPathNodeMaker(true);
			return new PathNodeNavigator(this.nodeMaker, range);
		}

		@Override
		protected boolean isAtValidPosition() {
			return true;
		}
	};
	private final MobNavigation landNavigation = new MobNavigation(this, getWorld());

	public PlayerMob(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
		this.moveControl = new PlayerMoveControl(this);
	}

	public boolean canWander() {
		return canWander;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(PLAYER_MODEL_PARTS, getVisiblePartsByte(EnumSet.allOf(PlayerModelPart.class)));
		builder.add(LEFT_SHOULDER_ENTITY, new NbtCompound());
		builder.add(RIGHT_SHOULDER_ENTITY, new NbtCompound());
	}

	protected Brain.Profile<PlayerMob> createBrainProfile() {
		return PlayerBrain.createBrainProfile();
	}

	@Override
	public Brain<PlayerMob> getBrain() {
		return (Brain<PlayerMob>) super.getBrain();
	}

	public static DefaultAttributeContainer.Builder createPlayerAttributes() {
		return MobEntity.createMobAttributes().add(
				EntityAttributes.GENERIC_ATTACK_DAMAGE, 1
		).add(
				EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_SPEED
		);
	}

	private void replaceNavigation(EntityNavigation newNavigation) {
		if (this.navigation == newNavigation) return;
		if (navigation.getTargetPos() != null) {
			var path = newNavigation.findPathTo(navigation.getTargetPos(), ((AccessorEntityNavigation) navigation).getCurrentDistance());
			var speed = ((AccessorEntityNavigation) navigation).getSpeed();
			newNavigation.startMovingAlong(path, speed);
		}
		this.navigation.stop();
		this.navigation = newNavigation;
	}

	private void readShoulderEntities(NbtCompound nbt) {
		if (nbt.contains(SHOULDER_ENTITY_LEFT, NbtElement.COMPOUND_TYPE)) {
			this.setShoulderEntityLeft(nbt.getCompound(SHOULDER_ENTITY_LEFT));
		}
		if (nbt.contains(SHOULDER_ENTITY_RIGHT, NbtElement.COMPOUND_TYPE)) {
			this.setShoulderEntityRight(nbt.getCompound(SHOULDER_ENTITY_RIGHT));
		}
	}

	private NbtCompound removeUnsafeNBT(NbtCompound nbt) {
		var newNbt = nbt.copy();
		newNbt.remove(UUID_KEY);
		return newNbt;
	}

	public void copySkinFromPlayer(ServerPlayerEntity player) {
		setLeftHanded(player.getMainArm() == Arm.LEFT);
		setVisibleSkinParts(Arrays.stream(PlayerModelPart.values()).filter(player::isPartVisible).collect(Collectors.toSet()));
		setSkin(ServerDefaultSkinHelper.getOrDefault(player.getGameProfile()));
	}

	public void copyFromPlayerData(NbtCompound nbt) {
		this.readNbt(removeUnsafeNBT(nbt));
		getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(BASE_SPEED);
		NbtList nbtList = nbt.getList("Inventory", NbtElement.COMPOUND_TYPE);
		int selectedSlot = nbt.getInt("SelectedItemSlot");
		for (var entry : nbtList) {
			handleItemEntry((NbtCompound) entry, selectedSlot);
		}
		readShoulderEntities(nbt);
		var id = nbt.getUuid(UUID_KEY);
		var player = getServer().getPlayerManager().getPlayer(id);
		if (player != null) {
			copySkinFromPlayer(player);
			setCustomName(player.getName());
		} else {
			new ProfileComponent(Optional.empty(), Optional.of(id), new PropertyMap()).getFuture().thenAcceptAsync(
					profile -> setSkin(profile.gameProfile()), this.getServer()
			);
		}
	}

	private Optional<EquipmentSlot> getFromSlot(int slot, int selectedSlot) {
		return switch (slot) {
			case 100 -> Optional.of(EquipmentSlot.FEET);
			case 101 -> Optional.of(EquipmentSlot.LEGS);
			case 102 -> Optional.of(EquipmentSlot.CHEST);
			case 103 -> Optional.of(EquipmentSlot.HEAD);
			case 150 -> Optional.of(EquipmentSlot.OFFHAND);
			default -> {
				if (slot == selectedSlot) {
					yield Optional.of(EquipmentSlot.MAINHAND);
				}
				yield Optional.empty();
			}
		};
	}

	private void handleItemEntry(NbtCompound stackNBT, int selectedSlot) {
		int slot = stackNBT.getByte("Slot") & 0xFF;
		ItemStack.fromNbt(this.getRegistryManager(), stackNBT).ifPresent(stack -> {
			getFromSlot(slot, selectedSlot).ifPresent(equipmentSlot -> {
				this.equipStack(equipmentSlot, stack);
			});
		});
	}

	@Override
	public void travel(Vec3d movementInput) {
		if (this.isLogicalSideForUpdatingMovement() && this.isTouchingWater() && shouldSwim()) {
			this.updateVelocity(0.01f, movementInput);
			this.move(MovementType.SELF, this.getVelocity());
			this.setVelocity(this.getVelocity().multiply(0.9));
		} else {
			super.travel(movementInput);
		}
	}

	@Override
	public void tick() {
		super.tick();
		updatePose();

		if (shouldRespawnClient) {
			if (profile != null) {
				var manager = ((ServerChunkManager) this.getWorld().getChunkManager()).chunkLoadingManager;
				List<ServerPlayerEntity> players = List.of();
				var tracker = ((AccessorServerChunkLoadingManager) manager).getEntityTrackers().get(this.getId());
				if (tracker != null) {
					var listeners = ((AccessorServerChunkLoadingManager.EntityTracker) tracker).getListeners();
					players = listeners.stream().map(PlayerAssociatedNetworkHandler::getPlayer).toList();
					tracker.stopTracking();
					listeners.clear(); //Necessary because stopTracking does not clear listeners.
				}
				removePlayerEntryFrom(sendRemovePacketTo);
				sendRemovePacketTo.addAll(players);
				resetFakePlayer();
				if (getServer().getPlayerManager().getPlayer(profile.getId()) == null) {
					getServer().getPlayerManager().sendToAll(createPlayerInitPacket());
				}
				if (tracker != null) {
					tracker.updateTrackedStatus(players);
				}
			}
			shouldRespawnClient = false;
		} else if (!sendRemovePacketTo.isEmpty()) {
			removePlayerEntryFrom(sendRemovePacketTo);
			sendRemovePacketTo.clear();
		}
	}

	protected boolean canChangeIntoPose(EntityPose pose) {
		return this.getWorld().isSpaceEmpty(this, this.getDimensions(pose).getBoxAt(this.getPos()).contract(1.0E-7));
	}

	protected void updatePose() {
		if (!this.canChangeIntoPose(EntityPose.SWIMMING)) {
			return;
		}
		EntityPose entityPose = this.isFallFlying() ? EntityPose.FALL_FLYING : (this.isSleeping() ? EntityPose.SLEEPING : (this.isSwimming() ? EntityPose.SWIMMING : (this.isUsingRiptide() ? EntityPose.SPIN_ATTACK : (this.isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING))));
		EntityPose entityPose2 = this.hasVehicle() || this.canChangeIntoPose(entityPose) ? entityPose : (this.canChangeIntoPose(EntityPose.CROUCHING) ? EntityPose.CROUCHING : EntityPose.SWIMMING);
		this.setPose(entityPose2);
	}

	@Override
	public void updateSwimming() {
		if (!this.getWorld().isClient) {
			if (this.canMoveVoluntarily() && this.isTouchingWater() && shouldSwim()) {
				replaceNavigation(waterNavigation);
				this.setSwimming(true);
			} else {
				replaceNavigation(landNavigation);
				this.setSwimming(false);
			}
		}
	}

	protected boolean shouldSwim() {
		var target = navigation.getTargetPos();
		if (target != null) {
			if (getWorld().getBlockState(target).getFluidState().isIn(FluidTags.WATER)) {
				return true;
			} else {
				var pos = BlockPos.ofFloored(getEyePos()).up();
				return getWorld().getBlockState(pos).getFluidState().isIn(FluidTags.WATER);
			}
		}
		return true;
	}

	@Override
	protected void mobTick() {
		PlayerBrain.tick(this);
		super.mobTick();
	}

	@Override
	protected Brain<?> deserializeBrain(Dynamic<?> dynamic) {
		return PlayerBrain.create(this, this.createBrainProfile().deserialize(dynamic));
	}

	@Override
	public boolean isAngryAt(net.minecraft.entity.player.PlayerEntity player) {
		return this.getTarget() == player;
	}

	@Override
	public SoundCategory getSoundCategory() {
		return SoundCategory.PLAYERS;
	}

	@Override
	public float getPathfindingFavor(BlockPos pos, WorldView world) {
		return 0.0f;
	}

	@Override
	public EntityType<?> getPolymerEntityType(ServerPlayerEntity player) {
		return EntityType.PLAYER;
	}

	@Override
	protected SoundEvent getSwimSound() {
		return SoundEvents.ENTITY_PLAYER_SWIM;
	}

	@Override
	protected SoundEvent getSplashSound() {
		return SoundEvents.ENTITY_PLAYER_SPLASH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PLAYER_DEATH;
	}

	@Override
	public LivingEntity.FallSounds getFallSounds() {
		return new LivingEntity.FallSounds(SoundEvents.ENTITY_PLAYER_SMALL_FALL, SoundEvents.ENTITY_PLAYER_BIG_FALL);
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}

	@Override
	protected void updateDespawnCounter() {}

	@Override
	public boolean canUseRangedWeapon(RangedWeaponItem weapon) {
		return weapon instanceof BowItem || weapon instanceof CrossbowItem;
	}

	@Override
	public void setCharging(boolean charging) {

	}

	@Deprecated //This is never used, just here because it's a mandatory override from CrossbowUser.
	@Override
	public void postShoot() {}

	@Override
	public void activateRiptide(int riptideTicks, float riptideAttackDamage, ItemStack stack) {
		this.riptideTicks = riptideTicks;
		this.riptideAttackDamage = riptideAttackDamage;
		this.riptideStack = stack;
		if (!this.getWorld().isClient) {
			this.dropShoulderEntities();
			this.setLivingFlag(LivingEntity.USING_RIPTIDE_FLAG, true);
		}
	}

	protected float getDamageAgainst(Entity target, float baseDamage, DamageSource damageSource) {
		return EnchantmentHelper.getDamage((ServerWorld) this.getWorld(), this.getWeaponStack(), target, damageSource, baseDamage);
	}

	@Override
	protected void attackLivingEntity(LivingEntity target) {
		super.attackLivingEntity(target);
		if (this.isUsingRiptide()) {
			if (!target.isAttackable()) {
				return;
			}
			if (target.handleAttack(this)) {
				return;
			}

			ItemStack itemStack = this.getWeaponStack();
			DamageSource damageSource = this.getDamageSources().mobAttack(this);
			float damage = this.getDamageAgainst(target, this.riptideAttackDamage, damageSource);

			if (damage > 0) {
				Vec3d oldVelocity = target.getVelocity();
				damage += itemStack.getItem().getBonusAttackDamage(target, this.riptideAttackDamage, damageSource);
				if (target.damage(damageSource, damage)) {
					float k = this.getKnockbackAgainst(target, damageSource);
					target.takeKnockback(k * 0.5f, MathHelper.sin(this.getYaw() * ((float)Math.PI / 180)), -MathHelper.cos(this.getYaw() * ((float)Math.PI / 180)));
					this.setVelocity(this.getVelocity().multiply(0.6, 1.0, 0.6));

					if (target instanceof ServerPlayerEntity && target.velocityModified) {
						((ServerPlayerEntity)target).networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(target));
						target.velocityModified = false;
						target.setVelocity(oldVelocity);
					}

					playSound(SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, 1.0f, 1.0f);

					this.onAttacking(target);

					EnchantmentHelper.onTargetDamaged((ServerWorld) getWorld(), target, damageSource);
				}
			}
		}
	}

	@Override
	@NotNull
	public ItemStack getWeaponStack() {
		if (this.isUsingRiptide() && this.riptideStack != null) {
			return this.riptideStack;
		}
		return super.getWeaponStack();
	}

	private void dropShoulderEntity(NbtCompound entityNbt) {
		if (!this.getWorld().isClient && !entityNbt.isEmpty()) {
			EntityType.getEntityFromNbt(entityNbt, this.getWorld()).ifPresent(entity -> {
				entity.setPosition(this.getX(), this.getY() + (double)0.7f, this.getZ());
				((ServerWorld)this.getWorld()).tryLoadEntity(entity);
			});
		}
	}

	public NbtCompound getShoulderEntityLeft() {
		return this.dataTracker.get(LEFT_SHOULDER_ENTITY);
	}

	protected void setShoulderEntityLeft(NbtCompound entityNbt) {
		this.dataTracker.set(LEFT_SHOULDER_ENTITY, entityNbt);
	}

	public NbtCompound getShoulderEntityRight() {
		return this.dataTracker.get(RIGHT_SHOULDER_ENTITY);
	}

	protected void setShoulderEntityRight(NbtCompound entityNbt) {
		this.dataTracker.set(RIGHT_SHOULDER_ENTITY, entityNbt);
	}

	protected void dropShoulderEntities() {
		if (!getShoulderEntityLeft().isEmpty() || !getShoulderEntityRight().isEmpty()) {
			this.dropShoulderEntity(this.getShoulderEntityLeft());
			this.setShoulderEntityLeft(new NbtCompound());
			this.dropShoulderEntity(this.getShoulderEntityRight());
			this.setShoulderEntityRight(new NbtCompound());
		}
	}

	public boolean handleShoot(Hand hand, LivingEntity target, float pullProgress) {
		ItemStack stack = this.getStackInHand(hand);
		Item item = stack.getItem();
		float speed = 1.6f;
		float divergence = 14 - this.getWorld().getDifficulty().getId() * 4;
		switch (item) {
			case CrossbowItem crossbowItem -> {
				crossbowItem.shootAll(this.getWorld(), this, hand, stack, speed, divergence, this.getTarget());
				return true;
			}
			case BowItem bow -> {
				ItemStack arrow = this.getProjectileType(stack);
				PersistentProjectileEntity persistentProjectileEntity = this.createArrowProjectile(arrow, pullProgress, stack);
				EntityAIHelper.shootProjectile(
						this, persistentProjectileEntity, target,
						SoundEvents.ENTITY_ARROW_SHOOT, speed, divergence
				);
				return true;
			}
			case TridentItem trident -> {
				return throwTrident(stack, target, speed, divergence, true);
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		if (!handleShoot(Hand.MAIN_HAND, target, pullProgress)) {
			handleShoot(Hand.OFF_HAND, target, pullProgress);
		}
	}

	protected PersistentProjectileEntity createArrowProjectile(ItemStack arrow, float damageModifier, @Nullable ItemStack shotFrom) {
		return ProjectileUtil.createArrowProjectile(this, arrow, damageModifier, shotFrom);
	}

	private void removePlayerEntryFrom(Collection<ServerPlayerEntity> players) {
		if (getServer().getPlayerManager().getPlayer(profile.getId()) == null) {
			for (ServerPlayerEntity player : players) {
				player.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(profile.getId())));
			}
		}
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		sendRemovePacketTo.add(player);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean bl = super.damage(source, amount);
		if (this.getWorld().isClient) {
			return false;
		}
		if (bl && source.getAttacker() instanceof LivingEntity) {
			PlayerBrain.onAttacked(this, (LivingEntity) source.getAttacker());
		}
		return bl;
	}

	private GameProfile adaptProfile(GameProfile profile) {
		var name = getName().getString();
		if (!profile.getId().equals(getUuid()) || !profile.getName().equals(name)) {
			if (name.length() > 16) {
				name = name.substring(0, 16);
			}
			var newProfile = new GameProfile(
					getUuid(), name
			);
			newProfile.getProperties().putAll(profile.getProperties());
			return newProfile;
		}
		return profile;
	}

	private void respawnForClients() {
		if (profile == null) return;
		shouldRespawnClient = true;
	}

	@Override
	public void setCustomName(@Nullable Text name) {
		boolean changed = !Objects.equals(this.getCustomName(), name);
		super.setCustomName(name);
		if (changed && profile != null) {
			setSkin(profile);
		}
	}

	public void setSkin(GameProfile profile) {
		profile = adaptProfile(profile);
		if (profile.equals(this.profile) && profile.getProperties().equals(this.profile.getProperties())) return;
		this.profile = profile;
		respawnForClients();
	}

	public void addVisibleSkinPart(PlayerModelPart... parts) {
		var currentParts = getVisibleSkinParts();
		for (var part : parts) {
			if (!currentParts.contains(part)) {
				currentParts.add(part);
				setVisibleSkinParts(currentParts);
			}
		}
	}

	public void removeVisibleSkinPart(PlayerModelPart... parts) {
		var currentParts = getVisibleSkinParts();
		for (var part : parts) {
			if (currentParts.contains(part)) {
				currentParts.remove(part);
				setVisibleSkinParts(currentParts);
			}
		}
	}

	private static byte getVisiblePartsByte(Set<PlayerModelPart> parts) {
		return (byte) parts.stream().mapToInt(PlayerModelPart::getBitFlag).reduce(0, (lhs, rhs) -> lhs | rhs);
	}

	public void setVisibleSkinParts(Set<PlayerModelPart> parts) {
		dataTracker.set(
				PLAYER_MODEL_PARTS, getVisiblePartsByte(parts)
		);
	}

	public Set<PlayerModelPart> getVisibleSkinParts() {
		var parts = dataTracker.get(PLAYER_MODEL_PARTS);
		var partSet = EnumSet.allOf(PlayerModelPart.class);
		partSet.removeIf(
				part -> (part.getBitFlag() & parts) == 0
		);
		return partSet;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		ProfileComponent.CODEC.encodeStart(
				getRegistryManager().getOps(NbtOps.INSTANCE), new ProfileComponent(profile)
		).resultOrPartial(METAcraftCore.LOGGER::error).ifPresent(
				profile -> nbt.put(PROFILE, profile)
		);
		ExtraCodecs.MODEL_PART_SET_CODEC.encodeStart(
				getRegistryManager().getOps(NbtOps.INSTANCE), getVisibleSkinParts()
		).resultOrPartial(METAcraftCore.LOGGER::error).ifPresent(
				parts -> nbt.put(VISIBLE_SKIN_PARTS, parts)
		);
		if (!this.getShoulderEntityLeft().isEmpty()) {
			nbt.put(SHOULDER_ENTITY_LEFT, this.getShoulderEntityLeft());
		}
		if (!this.getShoulderEntityRight().isEmpty()) {
			nbt.put(SHOULDER_ENTITY_RIGHT, this.getShoulderEntityRight());
		}
		nbt.putBoolean(CAN_WANDER, canWander);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains(PROFILE)) {
			ProfileComponent.CODEC.parse(
					getRegistryManager().getOps(NbtOps.INSTANCE), nbt.get(PROFILE)
			).resultOrPartial(METAcraftCore.LOGGER::error).ifPresent(
				profileComponent -> profileComponent.getFuture().thenAcceptAsync(
						profile -> setSkin(profile.gameProfile()), this.getServer()
				)
			);
		}
		if (nbt.contains(VISIBLE_SKIN_PARTS)) {
			ExtraCodecs.MODEL_PART_SET_CODEC.parse(
					getRegistryManager().getOps(NbtOps.INSTANCE), nbt.get(VISIBLE_SKIN_PARTS)
			).resultOrPartial(METAcraftCore.LOGGER::error).ifPresent(
					this::setVisibleSkinParts
			);
		} else {
			setVisibleSkinParts(EnumSet.allOf(PlayerModelPart.class));
		}
		if (nbt.contains(CAN_WANDER)) {
			canWander = nbt.getBoolean(CAN_WANDER);
		}
		readShoulderEntities(nbt);
	}

	private Packet<?> createPlayerInitPacket() {
		return PlayerListS2CPacket.entryFromPlayer(List.of(fakePlayer));
	}

	private void resetFakePlayer() {
		fakePlayer = new FakePlayer((ServerWorld) getWorld(), profile) {};
	}

	private void initProfile() {
		if (profile == null) {
			var list = getServer().getPlayerManager().getPlayerList();
			var player = list.get(getWorld().getRandom().nextInt(list.size()));
			profile = player.getGameProfile();
		}
		profile = adaptProfile(profile);
		if (shouldRespawnClient || fakePlayer == null) {
			resetFakePlayer();
			shouldRespawnClient = false;
		}
	}

	@Override
	public void onBeforeSpawnPacket(ServerPlayerEntity player, Consumer<Packet<?>> packetConsumer) {
		initProfile();
		player.networkHandler.sendPacket(createPlayerInitPacket());
	}

	@Override
	public void modifyRawTrackedData(List<DataTracker.SerializedEntry<?>> data, ServerPlayerEntity player, boolean initial) {
		if (initial) {
			data.add(
				DataTracker.SerializedEntry.of(
						AccessorPlayerEntity.getModelParts(), dataTracker.get(PLAYER_MODEL_PARTS)
				)
			);
		}
		for (int i = 0; i < data.size(); i++) {
			replace(
					data, i, AccessorMobEntity.getMobFlags(), AccessorPlayerEntity.getMainArm(),
					flags -> (byte) (isLeftHanded() ? Arm.LEFT.getId() : Arm.RIGHT.getId())
			);
			replace(data, i, PLAYER_MODEL_PARTS, AccessorPlayerEntity.getModelParts());
			replace(data, i, RIGHT_SHOULDER_ENTITY, AccessorPlayerEntity.getRightShoulderEntity());
			replace(data, i, LEFT_SHOULDER_ENTITY, AccessorPlayerEntity.getLeftShoulderEntity());
		}
	}

	private static <T> void replace(List<DataTracker.SerializedEntry<?>> data, int current, TrackedData<? extends T> from, TrackedData<T> to) {
		replace(data, current, from, to, t -> t);
	}

	private static <T, U> void replace(List<DataTracker.SerializedEntry<?>> data, int current, TrackedData<T> from, TrackedData<U> to, Function<T, U> converter) {
		var existing = data.get(current);
		if (existing.id() == from.id() && existing.handler() == from.dataType()) {
			data.remove(current);
			data.add(current, DataTracker.SerializedEntry.of(to, converter.apply((T) existing.value())));
		}
	}

	public static class PlayerMoveControl extends MoveControl {
		private final PlayerMob player;

		public PlayerMoveControl(PlayerMob player) {
			super(player);
			this.player = player;
		}

		@Override
		public void tick() {
			if (this.player.isTouchingWater() && player.shouldSwim()) {
				BlockPos target = player.getNavigation().getTargetPos();
				if (target != null && target.getY() >= this.player.getBlockPos().getY()) {
					this.player.setVelocity(this.player.getVelocity().add(0.0, 0.002, 0.0));
				}
				if (this.state != MoveControl.State.MOVE_TO || this.player.getNavigation().isIdle()) {
					this.player.setMovementSpeed(0.0f);
					return;
				}
				double d = this.targetX - this.player.getX();
				double e = this.targetY - this.player.getY();
				double f = this.targetZ - this.player.getZ();
				double g = Math.sqrt(d * d + e * e + f * f);
				e /= g;
				float h = (float)(MathHelper.atan2(f, d) * 57.2957763671875) - 90.0f;
				this.player.setYaw(this.wrapDegrees(this.player.getYaw(), h, 90.0f));
				this.player.bodyYaw = this.player.getYaw();
				float i = (float)(this.speed * this.player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
				float j = MathHelper.lerp(0.125f, this.player.getMovementSpeed(), i);
				this.player.setMovementSpeed(j);
				this.player.setVelocity(this.player.getVelocity().add((double)j * d * 0.005, (double)j * e * 0.1, (double)j * f * 0.005));
			} else {
				if (!this.player.isOnGround()) {
					this.player.setVelocity(this.player.getVelocity().add(0.0, -0.008, 0.0));
				}
				super.tick();
			}
		}
	}
}
