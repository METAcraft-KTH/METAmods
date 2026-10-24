package se.metacraft.pirkko;

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
import net.minecraft.world.DifficultyInstance;
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

import java.util.List;
import java.util.Map;


/** Small cave monster, rendered with the same model as its matching collectible. */
public final class PirkkoEntity extends Monster implements PolymerEntity {
	/** Pirkkos near players never hit vanilla's distance despawn, so they also expire with age. */
	private static final int MAX_LIFETIME_TICKS = 10 * 60 * 20;
	/**
	 * How warm each color feels, from -1 (coldest) to 1 (warmest). Spawning in a biome picks colors whose
	 * warmth is close to the biome's temperature, so cerise is common in the cold and laserviolett in the heat.
	 */
	private static final float ANY_COLOR_CHANCE = 0.1F;
	private static final Map<String, Float> WARMTH = Map.ofEntries(
			Map.entry("cerise", -1.0F), Map.entry("blue", -0.8F), Map.entry("light_blue", -0.7F),
			Map.entry("cyan", -0.6F), Map.entry("white", -0.5F), Map.entry("purple", -0.35F),
			Map.entry("light_gray", -0.2F), Map.entry("gray", -0.1F), Map.entry("black", 0.0F),
			Map.entry("green", 0.15F), Map.entry("lime", 0.3F), Map.entry("pink", 0.4F),
			Map.entry("magenta", 0.5F), Map.entry("brown", 0.55F), Map.entry("yellow", 0.65F),
			Map.entry("orange", 0.75F), Map.entry("red", 0.9F), Map.entry("laserviolett", 1.0F));
	private String color = PirkkoHeads.COLORS.get(random.nextInt(PirkkoHeads.COLORS.size()));
	private int lifetime;

	public static void register() {
		var key = ResourceKey.create(Registries.ENTITY_TYPE, Pirkko.ID);
		var type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				FabricEntityType.Builder.createMob(PirkkoEntity::new, MobCategory.MONSTER,
						settings -> settings.defaultAttributes(PirkkoEntity::attributes).spawnPlacement(
								SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
								PirkkoEntity::canSpawn))
						// Leave 26.2's allowedInPeaceful default enabled.
						.sized(10F / 16, 14F / 16).eyeHeight(14F / 16 * 5.5F / 8)
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
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
										EntitySpawnReason reason, SpawnGroupData groupData) {
		// A small share of spawns ignores temperature, so every color can turn up anywhere.
		if (random.nextFloat() < ANY_COLOR_CHANCE) {
			color = PirkkoHeads.COLORS.get(random.nextInt(PirkkoHeads.COLORS.size()));
			return super.finalizeSpawn(level, difficulty, reason, groupData);
		}
		// Caves have their own biomes, so read the climate from the surface above. Vanilla temperatures run
		// from about -0.5 (frozen) to 2.0 (desert); plains, at 0.8, are neutral.
		var surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, blockPosition());
		float biome = level.getBiome(surface).value().getBaseTemperature();
		float target = Math.max(-1, Math.min(1, biome - 0.8F));
		double total = 0;
		var weights = new double[PirkkoHeads.COLORS.size()];
		for (int i = 0; i < weights.length; i++) {
			float distance = WARMTH.get(PirkkoHeads.COLORS.get(i)) - target;
			total += weights[i] = Math.exp(-distance * distance / 0.08);
		}
		double roll = random.nextDouble() * total;
		for (int i = 0; i < weights.length; i++) {
			if ((roll -= weights[i]) <= 0 || i == weights.length - 1) {
				color = PirkkoHeads.COLORS.get(i);
				break;
			}
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
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
		if (PirkkoHeads.COLORS.contains(saved)) color = saved;
		lifetime = input.getIntOr("Lifetime", 0);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		spawnAtLocation(level, PirkkoHeads.stack(color));
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
			display.setShadowRadius(0.3F);
			display.setShadowStrength(0.8F);
			addElement(display);
			update();
		}

		@Override
		protected void onTick() { update(); }

		private void update() {
			if (!color.equals(lastColor)) {
				var stack = new ItemStack(Items.PAPER);
				stack.set(DataComponents.ITEM_MODEL, PirkkoHeads.id(color));
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
			// Item-display geometry is centered on (8,8,8). Move its base to the origin, roll about the base,
			// and turn the model's Z-aligned arms across the body.
			display.setTransformation(new Matrix4f().rotateY((float) Math.toRadians(-yBodyRot))
					.rotateZ(roll).translate(0, 0.5F, 0).rotateY((float) Math.PI / 2));
			display.startInterpolationIfDirty();
		}
	}
}
