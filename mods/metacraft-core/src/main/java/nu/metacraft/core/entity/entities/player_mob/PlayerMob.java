package nu.metacraft.core.entity.entities.player_mob;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.entity.entities.player_mob.renderer.MannequinRenderer;
import nu.metacraft.core.entity.entities.player_mob.renderer.PlayerRenderer;
import nu.metacraft.core.entity.entities.player_mob.renderer.PlayerRendererType;
import nu.metacraft.core.mixin.AvatarAccessor;
import nu.metacraft.core.util.SynchedDataHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.entity.PoseLockable;
import nu.metacraft.core.entity.TridentUser;
import nu.metacraft.core.entity.ai.METAcraftMemoryModules;
import nu.metacraft.core.mixin.PathNavigationAccessor;
import nu.metacraft.core.mixin.MobAccessor;
import nu.metacraft.core.util.helper.EntityAIHelper;
import nu.metacraft.core.util.helper.ServerDefaultSkinHelper;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PlayerMob extends Monster implements PolymerEntity, CrossbowAttackMob, TridentUser, PoseLockable {

	public static final double BASE_SPEED = 0.4;

	protected static final EntityDataAccessor<Byte> PLAYER_MODEL_PARTS = SynchedEntityData.defineId(PlayerMob.class, EntityDataSerializers.BYTE);
	public static final EntityDataAccessor<OptionalInt> LEFT_SHOULDER_ENTITY = SynchedEntityData.defineId(PlayerMob.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
	public static final EntityDataAccessor<OptionalInt> RIGHT_SHOULDER_ENTITY = SynchedEntityData.defineId(PlayerMob.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
	public static final EntityDataAccessor<ResolvableProfile> PLAYER_SKIN = SynchedEntityData.defineId(PlayerMob.class, EntityDataSerializers.RESOLVABLE_PROFILE);
	public static final EntityDataAccessor<Optional<Component>> BELOW_NAME = SynchedEntityData.defineId(PlayerMob.class, EntityDataSerializers.OPTIONAL_COMPONENT);

	private static final String RENDERER = "renderer";
	private static final String PROFILE = "profile";
	private static final String VISIBLE_SKIN_PARTS = "visible_skin_parts";
	private static final String SHOULDER_ENTITY_LEFT = "ShoulderEntityLeft";
	private static final String SHOULDER_ENTITY_RIGHT = "ShoulderEntityRight";
	private static final String CAN_WANDER = "can_wander";
	private static final String DESCRIPTION = "description";

	private PlayerRenderer renderer = new MannequinRenderer(this);

	private CompoundTag leftShoulderNbt = new CompoundTag();
	private CompoundTag rightShoulderNbt = new CompoundTag();

	private boolean canWander = getDefaultCanWander();

	private boolean lockPose = false;

	private final WaterBoundPathNavigation waterNavigation = new WaterBoundPathNavigation(this, level()) {
		@Override
		protected PathFinder createPathFinder(int range) {
			this.nodeEvaluator = new SwimNodeEvaluator(true);
			return new PathFinder(this.nodeEvaluator, range);
		}

		@Override
		protected boolean canUpdatePath() {
			return true;
		}
	};
	private final GroundPathNavigation landNavigation = new GroundPathNavigation(this, level());

	public PlayerMob(EntityType<? extends Monster> entityType, Level world) {
		super(entityType, world);
		this.moveControl = new PlayerMoveControl(this);
		this.landNavigation.setCanFloat(true);
		this.landNavigation.setCanOpenDoors(true);
	}

	public boolean canWander() {
		return canWander;
	}

	public void setCanWander(boolean canWander) {
		this.canWander = canWander;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PLAYER_MODEL_PARTS, getVisiblePartsByte(EnumSet.allOf(PlayerModelPart.class)));
		builder.define(LEFT_SHOULDER_ENTITY, OptionalInt.empty());
		builder.define(RIGHT_SHOULDER_ENTITY, OptionalInt.empty());
		builder.define(PLAYER_SKIN, getDefaultSkin());
		builder.define(BELOW_NAME, Optional.empty());
	}

	@Override
	public Brain<PlayerMob> getBrain() {
		return (Brain<PlayerMob>) super.getBrain();
	}

	public static AttributeSupplier.Builder createPlayerAttributes() {
		return Mob.createMobAttributes().add(
				Attributes.ATTACK_DAMAGE, 1
		).add(
				Attributes.MOVEMENT_SPEED, BASE_SPEED
		).add(
				Attributes.WATER_MOVEMENT_EFFICIENCY, 0.3
		);
	}

	private void replaceNavigation(PathNavigation newNavigation) {
		if (this.navigation == newNavigation) return;
		if (navigation.getTargetPos() != null) {
			var path = newNavigation.createPath(navigation.getTargetPos(), ((PathNavigationAccessor) navigation).getReachRange());
			var speed = ((PathNavigationAccessor) navigation).getSpeedModifier();
			newNavigation.moveTo(path, speed);
		}
		this.navigation.stop();
		this.navigation = newNavigation;
	}

	private void readShoulderEntities(ValueInput nbt) {
		nbt.read(SHOULDER_ENTITY_LEFT, CompoundTag.CODEC).ifPresentOrElse(newLeft -> {
			if (!getShoulderEntityLeft().equals(newLeft)) {
				this.setShoulderEntityLeft(newLeft);
			}
		}, () -> {
			this.setShoulderEntityLeft(new CompoundTag());
		});
		nbt.read(SHOULDER_ENTITY_RIGHT, CompoundTag.CODEC).ifPresentOrElse(newRight -> {
			if (!getShoulderEntityRight().equals(newRight)) {
				this.setShoulderEntityRight(newRight);
			}
		}, () -> {
			this.setShoulderEntityRight(new CompoundTag());
		});
	}

	private CompoundTag removeUnsafeNBT(CompoundTag nbt) {
		var newNbt = nbt.copy();
		newNbt.remove(TAG_UUID);
		return newNbt;
	}

	public void copySkinFromPlayer(ServerPlayer player) {
		setLeftHanded(player.getMainArm() == HumanoidArm.LEFT);
		setVisibleSkinParts(Arrays.stream(PlayerModelPart.values()).filter(player::isModelPartShown).collect(Collectors.toSet()));
		setSkin(ResolvableProfile.createResolved(ServerDefaultSkinHelper.getOrDefault(player.getGameProfile())));
	}

	public void copyFromPlayerData(CompoundTag nbt) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:PlayerMob#copyFromPlayerData", METAcraftCore.LOGGER)) {
			var readView = TagValueInput.create(logging, registryAccess(), removeUnsafeNBT(nbt));
			this.load(readView);
			readShoulderEntities(readView);
		}
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BASE_SPEED);
		ListTag nbtList = nbt.getListOrEmpty("Inventory");
		int selectedSlot = nbt.getIntOr("SelectedItemSlot", 0);
		for (var entry : nbtList) {
			handleItemEntry((CompoundTag) entry, selectedSlot);
		}
		var id = nbt.read(TAG_UUID, UUIDUtil.AUTHLIB_CODEC);
		var player = id.map(value -> level().getServer().getPlayerList().getPlayer(value)).orElse(null);
		if (player != null) {
			copySkinFromPlayer(player);
			setCustomName(player.getName());
		} else if (id.isPresent()) {
			var resolver = level().getServer().services().profileResolver();
			Util.nonCriticalIoPool().execute(() -> {
				resolver.fetchById(id.get()).ifPresent(profile -> {
					level().getServer().execute(() -> setSkin(ResolvableProfile.createResolved(profile)));
				});
			});
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

	private void handleItemEntry(CompoundTag stackNBT, int selectedSlot) {
		int slot = stackNBT.getByteOr("Slot", (byte) 0) & 0xFF;
		ItemStack.CODEC.parse(
				registryAccess().createSerializationContext(NbtOps.INSTANCE), stackNBT
		).resultOrPartial(METAcraftCore.LOGGER::error).ifPresent(stack -> {
			getFromSlot(slot, selectedSlot).ifPresent(equipmentSlot -> {
				this.setItemSlot(equipmentSlot, stack);
			});
		});
	}

	@Override
	public void travel(Vec3 movementInput) {
		if (this.isLocalInstanceAuthoritative() && this.isInWater() && shouldSwim()) {
			this.moveRelative(0.02f, movementInput);
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
		} else {
			super.travel(movementInput);
		}
	}

	@Override
	public void tick() {
		super.tick();
		updatePose();
		renderer.tick();
	}

	protected boolean canChangeIntoPose(Pose pose) {
		return this.level().noCollision(this, this.getDimensions(pose).makeBoundingBox(this.position()).deflate(1.0E-7));
	}

	protected void updatePose() {
		if (lockPose) return;
		if (!this.canChangeIntoPose(Pose.SWIMMING)) {
			return;
		}
		Pose entityPose = this.isFallFlying() ? Pose.FALL_FLYING : (this.isSleeping() ? Pose.SLEEPING : (this.isSwimming() ? Pose.SWIMMING : (this.isAutoSpinAttack() ? Pose.SPIN_ATTACK : (this.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING))));
		Pose entityPose2 = this.isPassenger() || this.canChangeIntoPose(entityPose) ? entityPose : (this.canChangeIntoPose(Pose.CROUCHING) ? Pose.CROUCHING : Pose.SWIMMING);
		this.setPose(entityPose2);
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return AvatarAccessor.getPoseDimensions().getOrDefault(pose, AvatarAccessor.getStandingDimensions());
	}

	@Override
	public ImmutableList<Pose> getDismountPoses() {
		return ImmutableList.of(Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING);
	}

	public ResolvableProfile getSkinData() {
		return renderer.getSkinData();
	}

	@Override
	public void updateSwimming() {
		if (!this.level().isClientSide()) {
			if (this.isEffectiveAi() && this.isInWater() && shouldSwim()) {
				replaceNavigation(waterNavigation);
				this.setSwimming(true);
			} else {
				replaceNavigation(landNavigation);
				this.setSwimming(false);
			}
		}
	}

	protected boolean shouldSwim() {
		var eyePos = BlockPos.containing(getEyePosition()).above();
		boolean isCurrentlyInWater = level().getBlockState(eyePos).getFluidState().is(FluidTags.WATER);
		if (getBrain().hasMemoryValue(METAcraftMemoryModules.RECOVERING_BREATH)) {
			return isCurrentlyInWater;
		}
		var target = navigation.getTargetPos();
		if (target != null) {
			var f = level().getBlockState(target).getFluidState();
			if (f.is(FluidTags.WATER) && f.isSource()) {
				return true;
			} else {
				return isCurrentlyInWater;
			}
		}
		return true;
	}

	@Override
	protected void customServerAiStep(ServerLevel world) {
		PlayerBrain.tick(world, this);
		super.customServerAiStep(world);
	}

	@Override
	protected @NonNull Brain<?> makeBrain(Brain.@NonNull Packed packed) {
		return PlayerBrain.createBrainProfile().makeBrain(this, packed);
	}

	@Override
	public boolean isPreventingPlayerRest(ServerLevel world, Player player) {
		return this.getTarget() == player;
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.PLAYERS;
	}

	@Override
	public float getWalkTargetValue(BlockPos pos, LevelReader world) {
		return 0.0f;
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return renderer.getPolymerEntityType(context);
	}

	@Override
	protected SoundEvent getSwimSound() {
		return SoundEvents.PLAYER_SWIM;
	}

	@Override
	protected SoundEvent getSwimSplashSound() {
		return SoundEvents.PLAYER_SPLASH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	public LivingEntity.Fallsounds getFallSounds() {
		return new LivingEntity.Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
	}

	@Override
	protected void updateNoActionTime() {}

	@Override
	public boolean canUseNonMeleeWeapon(ItemStack stack) {
		var item = stack.getItem();
		return item instanceof BowItem || item instanceof CrossbowItem;
	}

	@Override
	public void setChargingCrossbow(boolean charging) {

	}

	@Deprecated //This is never used, just here because it's a mandatory override from CrossbowUser.
	@Override
	public void onCrossbowAttackPerformed() {}

	@Override
	public void activateRiptide(int riptideTicks, float riptideAttackDamage, ItemStack stack) {
		this.autoSpinAttackTicks = riptideTicks;
		this.autoSpinAttackDmg = riptideAttackDamage;
		this.autoSpinAttackItemStack = stack;
		if (!this.level().isClientSide()) {
			this.dropShoulderEntities();
			this.setLivingEntityFlag(LivingEntity.LIVING_ENTITY_FLAG_SPIN_ATTACK, true);
		}
	}

	protected float getDamageAgainst(Entity target, float baseDamage, DamageSource damageSource) {
		return EnchantmentHelper.modifyDamage((ServerLevel) this.level(), this.getWeaponItem(), target, damageSource, baseDamage);
	}

	@Override
	protected void doAutoAttackOnTouch(LivingEntity target) {
		super.doAutoAttackOnTouch(target);
		if (this.isAutoSpinAttack()) {
			if (!target.isAttackable()) {
				return;
			}
			if (target.skipAttackInteraction(this)) {
				return;
			}

			ItemStack itemStack = this.getWeaponItem();
			DamageSource damageSource = this.damageSources().mobAttack(this);
			float damage = this.getDamageAgainst(target, this.autoSpinAttackDmg, damageSource);

			if (damage > 0) {
				Vec3 oldVelocity = target.getDeltaMovement();
				damage += itemStack.getItem().getAttackDamageBonus(target, this.autoSpinAttackDmg, damageSource);
				if (target.hurtServer((ServerLevel) this.level(), damageSource, damage)) {
					float k = this.getKnockback(target, damageSource);
					target.knockback(
							k * 0.5f,
							Mth.sin(this.getYRot() * ((float)Math.PI / 180)),
							-Mth.cos(this.getYRot() * ((float)Math.PI / 180)),
							damageSource, damage
					);
					this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));

					if (target instanceof ServerPlayer && target.hurtMarked) {
						((ServerPlayer)target).connection.send(new ClientboundSetEntityMotionPacket(target));
						target.hurtMarked = false;
						target.setDeltaMovement(oldVelocity);
					}

					playSound(SoundEvents.PLAYER_ATTACK_WEAK, 1.0f, 1.0f);

					this.setLastHurtMob(target);

					EnchantmentHelper.doPostAttackEffects((ServerLevel) level(), target, damageSource);
				}
			}
		}
	}

	@Override
	@NotNull
	public ItemStack getWeaponItem() {
		if (this.isAutoSpinAttack() && this.autoSpinAttackItemStack != null) {
			return this.autoSpinAttackItemStack;
		}
		return super.getWeaponItem();
	}

	private void dropShoulderEntity(CompoundTag entityNbt) {
		if (!this.level().isClientSide() && !entityNbt.isEmpty()) {
			try (var logging = LoggingErrorReporter.create(() -> "metacraft:PlayerMob#dropShoulderEntity", METAcraftCore.LOGGER)) {
				var readView = TagValueInput.create(logging, registryAccess(), entityNbt);
				EntityType.create(readView, this.level(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false)).ifPresent(entity -> {
					entity.setPos(this.getX(), this.getY() + (double)0.7f, this.getZ());
					((ServerLevel)this.level()).addWithUUID(entity);
				});
			}
		}
	}

	public CompoundTag getShoulderEntityLeft() {
		return leftShoulderNbt;
	}

	public void setShoulderEntityLeft(CompoundTag entityNbt) {
		leftShoulderNbt = entityNbt;
		renderer.onSetShoulderEntityLeft(entityNbt);
	}

	public CompoundTag getShoulderEntityRight() {
		return rightShoulderNbt;
	}

	public void setShoulderEntityRight(CompoundTag entityNbt) {
		rightShoulderNbt = entityNbt;
		renderer.onSetShoulderEntityRight(entityNbt);
	}

	protected void dropShoulderEntities() {
		if (!getShoulderEntityLeft().isEmpty() || !getShoulderEntityRight().isEmpty()) {
			this.dropShoulderEntity(this.getShoulderEntityLeft());
			this.setShoulderEntityLeft(new CompoundTag());
			this.dropShoulderEntity(this.getShoulderEntityRight());
			this.setShoulderEntityRight(new CompoundTag());
		}
	}

	public boolean handleShoot(InteractionHand hand, LivingEntity target, float pullProgress) {
		ItemStack stack = this.getItemInHand(hand);
		Item item = stack.getItem();
		float speed = 1.6f;
		float divergence = 14 - this.level().getDifficulty().getId() * 4;
		switch (item) {
			case CrossbowItem crossbowItem -> {
				crossbowItem.performShooting(this.level(), this, hand, stack, speed, divergence, this.getTarget());
				return true;
			}
			case BowItem bow -> {
				ItemStack arrow = this.getProjectile(stack);
				AbstractArrow persistentProjectileEntity = this.createArrowProjectile(arrow, pullProgress, stack);
				EntityAIHelper.shootProjectile(
						this, persistentProjectileEntity, target,
						SoundEvents.ARROW_SHOOT, speed, divergence
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
	public void performRangedAttack(LivingEntity target, float pullProgress) {
		if (!handleShoot(InteractionHand.MAIN_HAND, target, pullProgress)) {
			handleShoot(InteractionHand.OFF_HAND, target, pullProgress);
		}
	}

	protected AbstractArrow createArrowProjectile(ItemStack arrow, float damageModifier, @Nullable ItemStack shotFrom) {
		return ProjectileUtil.getMobArrow(this, arrow, damageModifier, shotFrom);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		renderer.startSeenByPlayer(player);
	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		boolean bl = super.hurtServer(world, source, amount);
		if (this.level().isClientSide()) {
			return false;
		}
		if (bl && source.getEntity() instanceof LivingEntity) {
			PlayerBrain.onAttacked(world, this, (LivingEntity) source.getEntity());
		}
		return bl;
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		var prev = this.getCustomName();
		super.setCustomName(name);
		renderer.onSetCustomName(name, prev);
	}

	public void setSkin(ResolvableProfile profile) {
		renderer.setSkin(profile);
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
		return (byte) parts.stream().mapToInt(PlayerModelPart::getMask).reduce(0, (lhs, rhs) -> lhs | rhs);
	}

	public void setVisibleSkinParts(Set<PlayerModelPart> parts) {
		entityData.set(
				PLAYER_MODEL_PARTS, getVisiblePartsByte(parts)
		);
	}

	public Set<PlayerModelPart> getVisibleSkinParts() {
		var parts = entityData.get(PLAYER_MODEL_PARTS);
		var partSet = EnumSet.allOf(PlayerModelPart.class);
		partSet.removeIf(
				part -> (part.getMask() & parts) == 0
		);
		return partSet;
	}

	public void setDescription(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Component> description) {
		renderer.setDescription(description);
	}

	public Optional<Component> getDescription() {
		return renderer.getDescription();
	}

	@Override
	public void addAdditionalSaveData(ValueOutput nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.store(RENDERER, PlayerRendererType.CODEC, renderer.getType());
		if (renderer.getSkinData() != null) {
			nbt.store(PROFILE, ResolvableProfile.CODEC, renderer.getSkinData());
		}
		nbt.store(VISIBLE_SKIN_PARTS, METACodecs.MODEL_PART_SET_CODEC, getVisibleSkinParts());
		if (!this.getShoulderEntityLeft().isEmpty()) {
			nbt.store(SHOULDER_ENTITY_LEFT, CompoundTag.CODEC, this.getShoulderEntityLeft().copy());
		}
		if (!this.getShoulderEntityRight().isEmpty()) {
			nbt.store(SHOULDER_ENTITY_RIGHT, CompoundTag.CODEC, this.getShoulderEntityRight().copy());
		}
		nbt.putBoolean(CAN_WANDER, canWander);
		getDescription().ifPresent(desc -> nbt.store(DESCRIPTION, ComponentSerialization.CODEC, desc));
	}

	@Override
	public void readAdditionalSaveData(ValueInput nbt) {
		var type = nbt.read(RENDERER, PlayerRendererType.CODEC).orElse(PlayerRendererType.MANNEQUIN);
		if (type != renderer.getType()) {
			renderer.reset();
			renderer = type.createRenderer(this, getDefaultSkin());
			renderer.reinitialize();
		}
		super.readAdditionalSaveData(nbt);
		nbt.read(PROFILE, ResolvableProfile.CODEC).ifPresentOrElse(
				this::setSkin,
				() -> setSkin(getDefaultSkin())
		);
		nbt.read(VISIBLE_SKIN_PARTS, METACodecs.MODEL_PART_SET_CODEC).ifPresentOrElse(
				this::setVisibleSkinParts,
				() -> setVisibleSkinParts(EnumSet.allOf(PlayerModelPart.class))
		);
		canWander = nbt.getBooleanOr(CAN_WANDER, true);
		readShoulderEntities(nbt);
		setDescription(nbt.read(DESCRIPTION, ComponentSerialization.CODEC));
	}

	protected ResolvableProfile getDefaultSkin() {
		var list = level().getServer().getPlayerList().getPlayers();
		if (list.isEmpty()) {
			return ResolvableProfile.createResolved(new GameProfile(UUID.randomUUID(), "Default"));
		} else {
			var player = list.get(level().getRandom().nextInt(list.size()));
			return ResolvableProfile.createResolved(player.getGameProfile());
		}
	}

	protected boolean getDefaultCanWander() {
		return true;
	}

	@Override
	public void onBeforeSpawnPacket(ServerPlayer player, Consumer<Packet<?>> packetConsumer) {
		renderer.onBeforeSpawnPacket(player, packetConsumer);
	}

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		super.onRemoval(reason);
		renderer.reset();
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		if (initial) {
			data.add(
				SynchedEntityData.DataValue.create(
						AvatarAccessor.getModelParts(), entityData.get(PLAYER_MODEL_PARTS)
				)
			);
		}
		for (int i = 0; i < data.size(); i++) {
			SynchedDataHelper.replace(
					data, i, MobAccessor.getMobFlags(), AvatarAccessor.getMainArm(),
					flags -> isLeftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT
			);
			SynchedDataHelper.replace(data, i, PLAYER_MODEL_PARTS, AvatarAccessor.getModelParts());
		}
		renderer.modifyRawTrackedData(data, player, initial);
	}

	@Override
	public void setLockPose(boolean lockPose) {
		this.lockPose = lockPose;
	}

	public static class PlayerMoveControl extends MoveControl {
		private final PlayerMob player;

		public PlayerMoveControl(PlayerMob player) {
			super(player);
			this.player = player;
		}

		@Override
		public void tick() {
			if (this.player.isInWater() && player.shouldSwim()) {
				BlockPos target = player.getNavigation().getTargetPos();
				if (target != null && target.getY() >= this.player.blockPosition().getY()) {
					this.player.setDeltaMovement(this.player.getDeltaMovement().add(0.0, 0.002, 0.0));
				}
				if (this.operation != MoveControl.Operation.MOVE_TO || this.player.getNavigation().isDone()) {
					this.player.setSpeed(0.0f);
					return;
				}
				double d = this.wantedX - this.player.getX();
				double e = this.wantedY - this.player.getY();
				double f = this.wantedZ - this.player.getZ();
				double g = Math.sqrt(d * d + e * e + f * f);
				e /= g;
				float h = (float)(Mth.atan2(f, d) * 57.2957763671875) - 90.0f;
				this.player.setYRot(this.rotlerp(this.player.getYRot(), h, 90.0f));
				this.player.yBodyRot = this.player.getYRot();
				float i = (float)(this.speedModifier * this.player.getAttributeValue(Attributes.MOVEMENT_SPEED));
				float j = Mth.lerp(0.125f, this.player.getSpeed(), i);
				this.player.setSpeed(j);
				this.player.setDeltaMovement(this.player.getDeltaMovement().add((double)j * d * 0.005, (double)j * e * 0.1, (double)j * f * 0.005));
			} else {
				super.tick();
			}
		}
	}
}
