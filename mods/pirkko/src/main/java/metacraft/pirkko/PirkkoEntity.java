package metacraft.pirkko;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.data.InteractionEntityData;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Small cave monster, rendered with the same model as its matching collectible. */
public final class PirkkoEntity extends Monster implements PolymerEntity {
	private static final List<String> COLORS = new ArrayList<>();
	static {
		for (var color : DyeColor.values()) COLORS.add(color.getName());
		COLORS.add("laserviolett");
		COLORS.add("cerise");
	}
	/** Pirkkos near players never hit vanilla's distance despawn, so they also expire with age. */
	private static final int MAX_LIFETIME_TICKS = 10 * 60 * 20;
	private String color = COLORS.get(random.nextInt(COLORS.size()));
	private int lifetime;

	public static void register() {
		var key = ResourceKey.create(Registries.ENTITY_TYPE, Pirkko.ID);
		var type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				FabricEntityType.Builder.createMob(PirkkoEntity::new, MobCategory.MONSTER,
						settings -> settings.defaultAttributes(PirkkoEntity::attributes).spawnPlacement(
								SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
								PirkkoEntity::canSpawn))
						// Leave 26.2's allowedInPeaceful default enabled.
						.sized(0.6F, 0.6F).eyeHeight(0.6F * 5.5F / 8)
						.clientTrackingRange(8).updateInterval(1).noLootTable().build(key));
		PolymerEntityUtils.registerType(type);
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, type, 15, 1, 3);
	}

	private static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 4)
				.add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.ATTACK_DAMAGE, 1)
				.add(Attributes.FOLLOW_RANGE, 24);
	}

	static boolean canSpawn(EntityType<PirkkoEntity> type, ServerLevelAccessor level,
							EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return pos.getY() < level.getSeaLevel() && !level.canSeeSky(pos)
				&& Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
	}

	public PirkkoEntity(EntityType<? extends PirkkoEntity> type, Level level) {
		super(type, level);
		xpReward = 3;
		EntityAttachment.ofTicking(new Model(), this);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8));
		goalSelector.addGoal(4, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return level().getDifficulty() != Difficulty.PEACEFUL && super.canAttack(target);
	}

	@Override
	public void setTarget(LivingEntity target) {
		super.setTarget(level().getDifficulty() == Difficulty.PEACEFUL ? null : target);
	}

	@Override
	public void tick() {
		// Clear an existing chase before goals tick when difficulty changes live.
		if (level().getDifficulty() == Difficulty.PEACEFUL && getTarget() != null) {
			setTarget(null);
			getNavigation().stop();
			setAggressive(false);
		}
		super.tick();
		// Same exemptions as vanilla despawning: name-tagged, leashed or riding Pirkkos stay.
		if (!level().isClientSide() && isAlive() && !isPersistenceRequired() && !requiresCustomPersistence()
				&& ++lifetime >= MAX_LIFETIME_TICKS) {
			discard();
		}
	}

	@Override
	public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
		return level.getDifficulty() != Difficulty.PEACEFUL && super.isPreventingPlayerRest(level, player);
	}

	@Override
	public DamageSource createDamageSource() {
		// Keep normal mob-attack attribution, armor, shields and hurt effects,
		// but don't multiply player damage on Easy/Hard.
		return new DamageSource(super.createDamageSource().typeHolder(), this) {
			@Override
			public boolean scalesWithDifficulty() { return false; }
		};
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
		// A fixed bite: Strength, Weakness and equipment must not change its base damage.
		var source = createDamageSource();
		boolean hurt = target.hurtServer(level, source, 1.0F);
		if (hurt) {
			net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffects(level, target, source);
			setLastHurtMob(target);
			playAttackSound();
		}
		return hurt;
	}

	private Identifier modelId() {
		return Identifier.fromNamespaceAndPath("pirkko", "pirkko_" + color);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Color", color);
		output.putInt("Lifetime", lifetime);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		var saved = input.getStringOr("Color", color);
		if (COLORS.contains(saved)) color = saved;
		lifetime = input.getIntOr("Lifetime", 0);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		spawnAtLocation(level, BuiltInRegistries.ITEM.getValue(modelId()));
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.INTERACTION;
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		// Interaction has no living-entity metadata. Supply its exact client attack box instead.
		data.clear();
		data.add(SynchedEntityData.DataValue.create(InteractionEntityData.WIDTH, getBbWidth()));
		data.add(SynchedEntityData.DataValue.create(InteractionEntityData.HEIGHT, getBbHeight()));
		data.add(SynchedEntityData.DataValue.create(InteractionEntityData.RESPONSE, true));
	}

	private final class Model extends ElementHolder {
		private final ItemDisplayElement display = new ItemDisplayElement();
		private String lastColor;
		private double lastX = getX(), lastZ = getZ();
		private float speed, phase;

		private Model() {
			display.setItemDisplayContext(ItemDisplayContext.NONE);
			display.setInterpolationDuration(1);
			display.setTeleportDuration(1);
			display.setDisplaySize(1, 1);
			display.setShadowRadius(0.2F);
			display.setShadowStrength(0.8F);
			addElement(display);
			update();
		}

		@Override
		protected void onTick() { update(); }

		private void update() {
			if (!color.equals(lastColor)) {
				var stack = new ItemStack(Items.FLOWER_POT);
				stack.set(DataComponents.ITEM_MODEL, modelId());
				display.setItem(stack);
				lastColor = color;
			}
			double dx = getX() - lastX, dz = getZ() - lastZ;
			lastX = getX();
			lastZ = getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			// Ignore teleports and airborne movement; ease back upright when standing still.
			float target = onGround() && isAlive() && distance < 1 ? (float) Math.min(distance * 8, 1) : 0;
			speed += (target - speed) * 0.4F;
			phase += speed * 0.55F;
			float roll = (float) Math.sin(phase) * speed * 0.18F;
			if (deathTime > 0) roll = Math.min(deathTime / 15F, 1) * (float) Math.PI / 2;
			// Item-display geometry is centered on (8,8,8). Move its base to the
			// origin before rolling, and turn the model's Z-aligned arms across the body.
			display.setTransformation(new Matrix4f().rotateY((float) Math.toRadians(-yBodyRot))
					.rotateZ(roll).translate(0, 0.5F, 0).rotateY((float) Math.PI / 2));
			display.startInterpolationIfDirty();
		}
	}
}
