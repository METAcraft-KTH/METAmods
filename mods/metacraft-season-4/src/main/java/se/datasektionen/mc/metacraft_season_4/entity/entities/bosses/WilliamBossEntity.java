package se.datasektionen.mc.metacraft_season_4.entity.entities.bosses;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.serialization.Dynamic;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ProjectileItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import net.minecraft.util.math.floatprovider.UniformFloatProvider;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.entity.ai.tasks.ImprovedRangedApproachTask;
import se.datasektionen.mc.metacraft_core.entity.ai.tasks.SmartShootAttackTask;
import se.datasektionen.mc.metacraft_core.entity.ai.tasks.SmartStrafeAttackTask;
import se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerBrain;
import se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerMob;
import se.datasektionen.mc.metacraft_core.util.helper.EntityAIHelper;
import se.datasektionen.mc.metacraft_lib.condition.conditions.NotInWall;
import se.datasektionen.mc.metacraft_lib.entity.EntityParameters;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.metacraft_season_4.boss.ChangeTickSpeed;
import se.datasektionen.mc.metacraft_season_4.boss.PlayerShadows;
import se.datasektionen.mc.metacraft_season_4.entity.Season4Entities;
import se.datasektionen.mc.metacraft_season_4.status_effects.Season4StatusEffects;
import se.metacraft.bosses.boss.AutoAttackingBoss;
import se.metacraft.bosses.boss.attacks.*;
import se.metacraft.bosses.boss.attacks.target.MoveToGround;
import se.metacraft.bosses.entity.entities.ItemSpawnerWithTarget;
import se.metacraft.bosses.util.DoubleTeamHandler;

import java.util.*;

public class WilliamBossEntity extends GenericBossPlayer implements AutoAttackingBoss {

	private static final GameProfile SKIN = new GameProfile(UUID.randomUUID(), "William");

	static {
		SKIN.getProperties().put(
				"textures", new Property(
						"textures",
						"ewogICJ0aW1lc3RhbXAiIDogMTc0OTQwMTk2MDM1NSwKICAicHJvZmlsZUlkIiA6ICI0Y2M0NmE0ODRlZWI0MTczOTYwNGY4ODg2MTk0ZjAyZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJBY3VhZHJhZ29uMTAwIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZkYWI0ZGY4NTdkZDE0NTFlY2IwMmFkZWJhMDUxOTkwMGUxNzg2MDhlMDQwYjNkNDM0ZjIxOTEwMzIxYmEyZGUiCiAgICB9LAogICAgIkNBUEUiIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzU2OWI3ZjJhMWQwMGQyNmYzMGVmZTNmOWFiOWFjODE3YjFlNmQzNWY0ZjNjZmIwMzI0ZWYyZDMyODIyM2QzNTAiCiAgICB9CiAgfQp9",
						"faxgZ2WgqelChe4dPglq61Eo2bMFlpfHqAlvVtNnpGRPB5sSUYmXkYXojjzUOnpIUKZCzTs6Mmro5BRBKGGB075lNii5p9XyoHE5NXj1kVM+N11nUmPPI3bTnpj/7Qdp2X/JNbbHo4rlZJrzP2HHkgfSmQ22rwbsn57Nelj9AxaA2ngXfK342OS92FyerJnNV1084VooY1Mx32YL6SKf9xszs++zWghFb66/u70J6F/OSnRonZYuMglfvgI76K53rCrckK1Vu/Ucaz4VlbUCgNEmEY27sgETxJ5XsNPqsTwP97WXgvox8Vnyxn+U/0fYxDig0pxe4m5wcp/nvYN79Fv5fCWTX4Pq0+ykOlJVf3ktJFsyNxo0rDKhnz0dNJGhdnM0guRZUNecbhI0nXSfn0SKkX6n7exJZzCHqE7qfIc8yVlPVjzVLlaR3Keo4edkbZOkMGtm2Tu2bbAljRQLUES09hvpKdBeOhaHUHXP5MRKYnmxdqTI7KDjXgoNjSQknS9L9u/vTeM6Qz4XfsGhsEtdmL0JEn3JK+tsnZzR4krLoWSAGLFcobG7cu1QXas7ffE4+SHopRTakRJ/5ORdiC9XrAziRfwjikn3gR+qeJx6JRi7Ww4G6BuzRb8mEhP/Oxe6sH8sC4TwZbJmAeAHU+hbAAs7Al6sMjrOOGTdzWk="
				)
		);
	}

	public WilliamBossEntity(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	protected GameProfile getDefaultSkin() {
		return SKIN;
	}

	@Override
	protected Text getDefaultName() {
		return Text.literal("William");
	}

	public static DefaultAttributeContainer.Builder createBossAttributes() {
		return PlayerMob.createPlayerAttributes().add(
				EntityAttributes.MAX_HEALTH, 512
		);
	}

	private static final UniformIntProvider VERTICAL_RANGE = UniformIntProvider.create(0, 5);

	private static final UniformIntProvider HORIZONTAL_RANGE = UniformIntProvider.create(-10, 10);

	@Override
	protected DataPool<Attack> createDefaultAttacks(RegistryWrapper.WrapperLookup lookup) {
		return DataPool.<Attack>builder().add(
				new SpawnSpecifiedEntities(
						DataPool.<EntityHelper.SpawnEntry>builder().add(
								SpawnForEachTarget.createEntry(
										SpawnForEachTarget.createEntityNBTFrom(
												EntityType.GHAST,
												SpawnForEachTarget.createNBTFromMap(
														Map.of(
																"PreventReturnInstakill", true,
																"IgnoreYCheck", true
														)
												)
										),
										Optional.of(NotInWall.getInstance()),
										SpawnReason.TRIGGERED,
										20, 20
								)
						).add(
								SpawnForEachTarget.createEntry(
										SpawnForEachTarget.createEntityNBTFrom(
												EntityType.CREEPER
										),
										15, 15
								)
						).add(
								SpawnForEachTarget.createEntry(
										SpawnForEachTarget.createEntityNBTFrom(
												EntityType.VEX
										),
										Optional.empty(),
										SpawnReason.TRIGGERED,
										20, 20
								)
						).add(
								SpawnForEachTarget.createEntry(
										SpawnForEachTarget.createEntityNBTFrom(
												EntityType.BOGGED,
												SpawnForEachTarget.createNBTFromMap(
														Map.of(
																EntityParameters.PREVENT_ENTER_VEHICLE, true,
																EntityParameters.SURVIVES_SUNLIGHT, true
														)
												)
										),
										15, 15
								)
						).add(
								SpawnForEachTarget.createEntry(
										SpawnForEachTarget.createEntityNBTFrom(
												EntityType.STRAY,
												SpawnForEachTarget.createNBTFromMap(
														Map.of(
																EntityParameters.PREVENT_ENTER_VEHICLE, true,
																EntityParameters.SURVIVES_SUNLIGHT, true
														)
												)
										),
										15, 15
								)
						).add(
								SpawnForEachTarget.createEntry(
										SpawnForEachTarget.createEntityNBTFrom(
												EntityType.SKELETON,
												SpawnForEachTarget.createNBTFromMap(
														Map.of(
																EntityParameters.PREVENT_ENTER_VEHICLE, true,
																EntityParameters.SURVIVES_SUNLIGHT, true
														)
												)
										),
										15, 15
								)
						).add(
								SpawnForEachTarget.createEntry(
										SpawnForEachTarget.createEntityNBTFrom(
												EntityType.RAVAGER,
												SpawnForEachTarget.createNBTFromMap(
														Map.of("phantom_entity", true)
												)
										),
										15, 15
								)
						).build(),
						UniformIntProvider.create(
								5, 10
						),
						ConstantFloatProvider.create(1.5f),
						List.of(SpawnForEachTarget.PLAYER_PREDICATE),
						50,
						true,
						Optional.of(100)
				)
		).add(
				new PlayerShadows(UniformIntProvider.create(10, 10), ConstantIntProvider.create(10))
		).add(
				new ChangeTickSpeed(UniformIntProvider.create(400, 600), UniformFloatProvider.create(10, 30))
		).add(
				new TeleportAttack(
						new MoveToGround(UniformFloatProvider.create(0, 50)),
						Optional.empty()
				)
		).add(
				new DoubleTeamAttack(
						new DoubleTeamHandler.Settings(
								ConstantIntProvider.create(5),
								5,
								UniformFloatProvider.create(-5, 5),
								0.2,
								new NbtCompound(),
								Optional.of(true)
						)
				)
		).add(
				new SpawnForEachTarget(
						DataPool.<EntityHelper.SpawnEntry>builder().add(
								ItemSpawnerWithTarget.createSpawnEntry(
										new ItemStack(
												Items.FIREWORK_STAR.getRegistryEntry(), 1,
												ComponentChanges.builder().add(
														DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,
														true
												).build()
										),
										HORIZONTAL_RANGE,
										VERTICAL_RANGE,
										new ItemSpawnerWithTarget.ProjectileOverride(
												EntityHelper.SpawnEntry.createEntityNBTFrom(
														Season4Entities.MAGIC_PROJECTILE
												),
												ProjectileItem.Settings.DEFAULT.power(),
												ProjectileItem.Settings.DEFAULT.uncertainty()
										),
										lookup
								), 3
						).build(),
						List.of(SpawnForEachTarget.PLAYER_PREDICATE),
						ConstantIntProvider.create(1),
						true
				)
		).build();
	}

	@Override
	protected Brain<?> deserializeBrain(Dynamic<?> dynamic) {
		return WilliamBrain.create(this, this.createBrainProfile().deserialize(dynamic));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (!source.isOf(DamageTypes.OUT_OF_WORLD) && !this.isInvulnerableTo(world, source) && amount > Season4Entities.MAX_ATTACK_DAMAGE && source.getAttacker() instanceof LivingEntity l) {
			l.addStatusEffect(new StatusEffectInstance(Season4StatusEffects.SMALLIFY,  1200, 4));
			l.addStatusEffect(new StatusEffectInstance(Season4StatusEffects.HEALTH_REDUCTION,  1200, 11));
			return false;
		}
		return super.damage(world, source, amount);
	}

	@Override
	public boolean handleShoot(Hand hand, LivingEntity target, float pullProgress) {
		if (super.handleShoot(hand, target, pullProgress)) return true;
		var stack = this.getStackInHand(hand);
		float speed = 1.6f;
		float divergence = 14 - this.getWorld().getDifficulty().getId() * 4;
		if (stack.isOf(Items.BLAZE_ROD)) {
			var projectile = Season4Entities.MAGIC_PROJECTILE.create(getWorld(), SpawnReason.TRIGGERED);
			projectile.setOwner(this);
			projectile.setPos(getX(), getEyeY(), getZ());
			projectile.setCloudEffects(PollyBossEntity.EXTRA_POOL_CLOUD);
			projectile.setHitEffects(PollyBossEntity.EXTRA_POOL_HIT);
			projectile.setCloudEffectCount(UniformIntProvider.create(3, 5));
			projectile.setHitEffectCount(UniformIntProvider.create(1, 2));
			EntityAIHelper.shootProjectile(
					this, projectile, target,
					SoundEvents.ENTITY_EVOKER_CAST_SPELL, speed, divergence
			);
			this.swingHand(hand);
			return true;
		}
		return false;
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
		var data = super.initialize(world, difficulty, spawnReason, entityData);
		if (this.getMainHandStack().isEmpty()) {
			this.setStackInHand(
					Hand.MAIN_HAND,
					new ItemStack(
							Items.BLAZE_ROD.getRegistryEntry(), 1,
							ComponentChanges.builder().add(
									DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true
							).add(
									DataComponentTypes.ITEM_MODEL, Items.COMMAND_BLOCK.getComponents().get(DataComponentTypes.ITEM_MODEL)
							).build()
					)
			);
		}
		if (this.getOffHandStack().isEmpty()) {
			this.setStackInHand(
					Hand.OFF_HAND,
					new ItemStack(Items.CLOCK)
			);
		}
		return data;
	}

	@Override
	public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
		return super.isInvulnerableTo(world, source) || source.isIn(DamageTypeTags.IS_EXPLOSION);
	}

	public static class WilliamBrain extends PlayerBrain {

		protected static Brain<?> create(PlayerMob player, Brain<PlayerMob> brain) {
			addIdleActivities(brain);
			addCoreActivities(brain);
			addFightActivities(player, brain);
			brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
			brain.setDefaultActivity(Activity.IDLE);
			brain.resetPossibleActivities();
			return brain;
		}

		private static void addFightActivities(PlayerMob player, Brain<PlayerMob> brain) {
			brain.setTaskList(Activity.FIGHT, 10, ImmutableList.of(
					ForgetAttackTargetTask.create((world, target) -> !PlayerBrain.isPreferredAttackTarget(world, player, target)),
					TaskTriggerer.runIf(PlayerBrain::isHoldingCrossbow, AttackTask.create(5, 0.75f)),
					TaskTriggerer.runIf(
							PlayerBrain::allowSetMovePos,
							(SingleTickTask<MobEntity>) ImprovedRangedApproachTask.create(1.0f, WilliamBrain::rangeOverride)
					), TaskTriggerer.runIf(
							WilliamBrain::shouldAttackPhysical,
							MeleeAttackTask.create(20)
					), new CrossbowAttackTask<>(),
					new SmartShootAttackTask<>(WilliamBrain::isSmartProjectileWeapon, 20, WilliamBrain::rangeOverride),
					new SmartStrafeAttackTask<>(1, 8)
			), MemoryModuleType.ATTACK_TARGET);
		}

		protected static boolean isSmartProjectileWeapon(ItemStack stack) {
			return PlayerBrain.isSmartProjectileWeapon(stack) || stack.isOf(Items.BLAZE_ROD);
		}

		private static final OptionalInt WAND_RANGE = OptionalInt.of(15);

		protected static OptionalInt rangeOverride(ItemStack stack) {
			return stack.isOf(Items.BLAZE_ROD) ? WAND_RANGE : OptionalInt.empty();
		}

		protected static boolean shouldAttackPhysical(PlayerMob player) {
			return PlayerBrain.shouldAttackPhysical(player) && !player.getMainHandStack().isOf(Items.BLAZE_ROD);
		}

	}
}
