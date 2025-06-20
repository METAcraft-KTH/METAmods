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
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.*;
import net.minecraft.loot.condition.EntityPropertiesLootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.Vec3d;
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
import se.datasektionen.mc.metacraft_core.entity_ref.SelfRef;
import se.datasektionen.mc.metacraft_core.position_ref.AtEntityRef;
import se.datasektionen.mc.metacraft_core.position_ref.RandomRangeWithGravity;
import se.datasektionen.mc.metacraft_core.position_ref.WithTries;
import se.datasektionen.mc.metacraft_core.util.helper.EntityAIHelper;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.metacraft_season_4.Season4;
import se.datasektionen.mc.metacraft_season_4.boss.DevinDisguiseAttack;
import se.datasektionen.mc.metacraft_season_4.boss.InventoryShuffleAttack;
import se.datasektionen.mc.metacraft_season_4.boss.PlayerShuffleAttack;
import se.datasektionen.mc.metacraft_season_4.boss.Season4Attacks;
import se.datasektionen.mc.metacraft_season_4.entity.Season4Entities;
import se.datasektionen.mc.metacraft_season_4.util.DialogueHelper;
import se.metacraft.bosses.boss.AutoAttackingBoss;
import se.metacraft.bosses.boss.attacks.*;
import se.metacraft.bosses.boss.attacks.target.PositionRefTarget;
import se.metacraft.bosses.condition.entity_sub_predicate.BossPredicateType;
import se.metacraft.bosses.entity.entities.ItemSpawnerWithTarget;
import se.metacraft.bosses.util.DoubleTeamHandler;

import java.util.*;

public class DevinBossEntity extends GenericBossPlayer implements AutoAttackingBoss {

	private static final GameProfile SKIN = new GameProfile(UUID.randomUUID(), "Devin");

	static {
		SKIN.getProperties().put(
				"textures", new Property(
						"textures",
						"ewogICJ0aW1lc3RhbXAiIDogMTczOTkyMDY0MTMxNiwKICAicHJvZmlsZUlkIiA6ICI0OTY5YTVlZTYxMTY0MDBkYTM4YzhmZjRiMWJhZTZiZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWFjdFpJUCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xZGFkNDc2NjM4ODhmOWE5ZWI3MjNlNjlkZWE1NDMwZGY2YjEzNWNjMzYzMjQ2YWFlMGRiYzkzNTM5NGM1OWI0IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
						"P4xZ7nvXEE4JeS5ia47k+k5owkwia/5bE0OBFyHRx2FVgqbXskrlvK53xtnNW0G+9letTZbTr/p8ypRA+yEhmVyqKoUPle0m/qk6O/AgDh7LIwfGUN2ys49XnFUW/wYCv6Dl58PfegHkuoU4Gxkqk4PQFRxD22zqRc07eOXVatPWuH0l2q7FeKPyVnfPVNBIM1wzfl+R44IjOT6AbvQMK7X/bYL019teheVFhe5aWd/vsw3NsrxwyYOdloXbbBsmjBTQd3cX1bKccWBrhegR1pN5Nzz41QK7FInn3NuB1rw0m/6+AhMmJpvSsnhITwz+BxKH+lbE0wXVSexRkBijJYGFUh52HEbg7Q8MV7JtnxZOD+rimt51+BkerGjhhkUweWLusNfAg02+LEg1ZRH8+LK7eWTdhn9kGQVu/xUBMJ2jOy2M8CY/toZiAvnXBI/gF8Kj38wtdKYwSMdiS1YCxW4CKQkhRc6RWINyg7AtCgPU3iVZ8x28SQc68roRA9Mdu5/cDUArHhTt9LXeb2WMKqT0spSlrvjEGFcmIynil8Bu6or/41d86W9n29I02C6dMqzPRpQ13sazSsK06eIU0pfxVfogGL6/7TrAjYBaNtDKRUMrLuECzQiZnER10is3XMlLPje4+UzzWHHTeU0RoiycRuXsZmpIXzPys+qxfjw="
				)
		);
	}

	public DevinBossEntity(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	private static final UniformIntProvider VERTICAL_RANGE = UniformIntProvider.create(0, 5);

	private static final UniformIntProvider HORIZONTAL_RANGE = UniformIntProvider.create(-10, 10);

	@Override
	protected GameProfile getDefaultSkin() {
		return SKIN;
	}

	@Override
	protected Text getDefaultName() {
		return Text.literal("Devin");
	}

	public static DefaultAttributeContainer.Builder createBossAttributes() {
		return PlayerMob.createPlayerAttributes().add(
				EntityAttributes.MAX_HEALTH, 512
		);
	}

	@Override
	protected DataPool<Attack> createDefaultAttacks(RegistryWrapper.WrapperLookup lookup) {
		return DataPool.<Attack>builder().add(
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
		).add(
				new ConditionalAttack(
						new MultiAttack(
								new DevinDisguiseAttack(),
								new SendMessageAttack(
										DialogueHelper.makeDialogue(
												Text.literal("One of you has been kidnapped, and I've disguised myself among you.")
										), false
								),
								new SendTitleAttack(
										Text.literal("b").styled(
												s -> s.withFont(METAcraftLib.getID("textures"))
										), Optional.empty(),
										new SendTitleAttack.Times(5, 50, 50)
								)
						),
						new EntityPropertiesLootCondition(
								Optional.of(EntityPredicate.Builder.create().typeSpecific(
										new BossPredicateType(
												List.of(new BossPredicateType.EntityEntry(
														NumberRange.IntRange.atLeast(2),
														SpawnForEachTarget.PLAYER_PREDICATE
												)),
												List.of(), List.of(),
												List.of(Season4Attacks.DEVIN_DISGUISE.getKey().orElseThrow())
										)
								).build()),
								LootContext.EntityTarget.THIS
						)
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
				PlayerShuffleAttack.getInstance()
		).add(
				InventoryShuffleAttack.createSimple(
						true, true,
						false, false, false
				)
		).add(
				TELEPORT
		).build();
	}

	@Override
	public boolean canHaveStatusEffect(StatusEffectInstance effect) {
		return super.canHaveStatusEffect(effect) && effect.getEffectType().value().getCategory() != StatusEffectCategory.HARMFUL;
	}

	@Override
	protected Brain<?> deserializeBrain(Dynamic<?> dynamic) {
		return DevinBrain.create(this, this.createBrainProfile().deserialize(dynamic));
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) && !this.isInvulnerableTo(world, source) && amount > Season4Entities.MAX_ATTACK_DAMAGE && source.getAttacker() instanceof LivingEntity l) {
			l.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA,  1200, 0));
			return false;
		}
		return super.damage(world, source, amount);
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
	public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
		return super.isInvulnerableTo(world, source) || source.isIn(DamageTypeTags.WITCH_RESISTANT_TO);
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
		setLeftHanded(false);
		if (this.getMainHandStack().isEmpty()) {
			this.setStackInHand(
					Hand.MAIN_HAND,
					new ItemStack(
							Items.BLAZE_ROD.getRegistryEntry(), 1,
							ComponentChanges.builder().add(
									DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true
							).add(
									DataComponentTypes.ITEM_MODEL, Season4.getID("wand")
							).build()
					)
			);
		}
		if (data instanceof DoubleTeamHandler) {
			var targets = getPlayerTargets();
			if (!targets.isEmpty()) {
				var target = targets.get(random.nextInt(targets.size()));
				this.getBrain().remember(MemoryModuleType.ANGRY_AT, target.getUuid());
			}
		}
		return data;
	}

	@Override
	public boolean canBeSpectated(ServerPlayerEntity player) {
		return super.canBeSpectated(player) && DevinDisguiseAttack.canBeSpectated(this, player);
	}

	public static class DevinBrain extends PlayerBrain {

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
							(SingleTickTask<MobEntity>) ImprovedRangedApproachTask.create(1.0f, DevinBrain::rangeOverride)
					), TaskTriggerer.runIf(
							DevinBrain::shouldAttackPhysical,
							MeleeAttackTask.create(20)
					), new CrossbowAttackTask<>(),
					new SmartShootAttackTask<>(DevinBrain::isSmartProjectileWeapon, 20, DevinBrain::rangeOverride),
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
