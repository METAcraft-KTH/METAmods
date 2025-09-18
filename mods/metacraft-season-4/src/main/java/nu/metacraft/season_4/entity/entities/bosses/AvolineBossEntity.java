package nu.metacraft.season_4.entity.entities.bosses;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Dynamic;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.explosion.AdvancedExplosionBehavior;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.entity.ai.tasks.ImprovedRangedApproachTask;
import nu.metacraft.core.entity.ai.tasks.SmartShootAttackTask;
import nu.metacraft.core.entity.ai.tasks.SmartStrafeAttackTask;
import nu.metacraft.core.entity.entities.player_mob.PlayerBrain;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import nu.metacraft.core.music.ManageableServerBossBar;
import nu.metacraft.core.util.helper.EntityAIHelper;
import nu.metacraft.lib.condition.conditions.NotInWall;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.season_4.Season4;
import nu.metacraft.season_4.boss.AvolineMultiTNT;
import nu.metacraft.season_4.entity.Season4Entities;
import nu.metacraft.season_4.mixin.AccessorTntEntity;
import nu.metacraft.bosses.boss.AutoAttackingBoss;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.SpawnForEachTarget;
import nu.metacraft.bosses.boss.attacks.SpawnSpecifiedEntities;

import java.util.*;

public class AvolineBossEntity extends GenericBossPlayer implements AutoAttackingBoss {

	private static final GameProfile SKIN = new GameProfile(
			UUID.randomUUID(), "Avoline", new PropertyMap(
				ImmutableMultimap.of(
						"textures", new Property(
								"textures",
								"ewogICJ0aW1lc3RhbXAiIDogMTc0NTcxNDM3ODY5MCwKICAicHJvZmlsZUlkIiA6ICJmNzM0MmExODMxZDA0ZDA5ODc4Y2ViOTVmOTUxYTllMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJOb3RNMWtzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2QyOWE2MGVmMGNlMzFhZTc3NWZmMDAzNmZkZTc3MTE1NzkxMzJiOGEzNGFiNTBjYTMwZmUzY2FjODA4NzE2ZTQiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==",
								"xDMCRuEiecnI2ZX2Qw13IuE29P+h+wcBSedY5FhMZJ7fAIgxPZG/y67fiBOCotgF6hS3o6sj44BDBCiSVk6zwg3kwSfWV/vQjrRa5r86FPEjsMNmzn5xLmXA/WWfTNsIbl95ojDNGQn1lWj3xFDDpzPvKPpviRs1hK6OADuc9WRs/96L2cN88n8H35e2epx0MD/PSdVzlSA68IHcEwdSaSp7K72c/x7NvhJQLpkftRMlYAIzIlofvNn4TKpY+WUfnDN/aXajAcdx/TE1q9zl2IMoN3GMFetfC3GHKJKldhhcjKXk5bmYjTE2SjJx4STQd2VNM708r4Z6CcFnIQm9QSBtsqHGirlRwBcDGpkmdv2CfhfUXTWPvpxf4aj3W3GZaVMRcphTV84u3elcZ1DlhP6+RbHrMcMNGTaWZ8PUexeyqIf7RX5x44emvEEOXF02+VYAYTKnwWZ4BUYZhVGembaHUJM0YiIslTXMWgH03NilD7Fb6+gtCZvO1Bv8RISUIXIQisXres7/zzwjnt7CrY/Mn9va6J02wn2rw0HMi9ErEXRC8uS/eUEhn417yDYG8I03NhrlvzMULYNhyz6NWbxGDtqlT5JB9hcEn7uVmxoZIeQdQJoXVqDssjBLXKp/SFoUIqFTQMJJM+WXCQFusOGL8M0goyMGHgYL9ArI3ik="
						)
				)
			)
	);

	public AvolineBossEntity(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	protected ProfileComponent getDefaultSkin() {
		return ProfileComponent.ofStatic(SKIN);
	}

	@Override
	protected Text getDefaultName() {
		return Text.literal("Avoline");
	}

	public static DefaultAttributeContainer.Builder createBossAttributes() {
		return PlayerMob.createPlayerAttributes().add(
				EntityAttributes.MAX_HEALTH, 512
		).add(
				EntityAttributes.FOLLOW_RANGE, 100
		).add(
				EntityAttributes.ARMOR, 5
		);
	}

	@Override
	protected Pool<Attack> createDefaultAttacks(RegistryWrapper.WrapperLookup lookup) {
		return Pool.<Attack>builder().add(
				new SpawnSpecifiedEntities(
						Pool.<EntityHelper.SpawnEntry>builder().add(
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
				AvolineMultiTNT.getInstance()
		).add(
				TELEPORT
		).build();
	}

	@Override
	protected Brain<?> deserializeBrain(Dynamic<?> dynamic) {
		return AvolineBrain.create(this, this.createBrainProfile().deserialize(dynamic));
	}

	@Override
	public void writeCustomData(WriteView nbt) {
		super.writeCustomData(nbt);
	}

	@Override
	public void readCustomData(ReadView nbt) {
		super.readCustomData(nbt);
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) && !this.isInvulnerableTo(world, source) && amount > Season4Entities.MAX_ATTACK_DAMAGE && source.getAttacker() != null) {
			world.createExplosion(
					this, world.getDamageSources().explosion(source.getSource(), this),
					new AdvancedExplosionBehavior(false, true, Optional.of(10.0f), Optional.empty()),
					source.getAttacker().getX(), source.getAttacker().getY(), source.getAttacker().getZ(),
					10, true, World.ExplosionSourceType.MOB
			);
		}
		return super.damage(world, source, amount);
	}

	public static TntEntity createTNTFlyingTowards(Entity source, Entity target) {
		return createTNTFlyingTowards(source, target, 1.6f, 14 - source.getEntityWorld().getDifficulty().getId() * 4);
	}

	@Override
	protected ManageableServerBossBar createDefaultBossBar() {
		return new ManageableServerBossBar(
				getDisplayName(), BossBar.Color.RED, BossBar.Style.NOTCHED_6
		);
	}

	public static TntEntity createTNTFlyingTowards(Entity source, Entity target, float speed, float divergence) {
		var projectile = new TntEntity(source.getEntityWorld(), source.getX(), source.getEyeY(), source.getZ(), source instanceof LivingEntity living ? living : null);
		var direction = EntityAIHelper.getDirection(projectile, target);
		var velocity = EntityAIHelper.calculateVelocity(
				direction.getX(), direction.getY(), direction.getZ(),
				speed, divergence, source.getRandom()
		);
		projectile.setVelocity(velocity);
		projectile.setFuse(
				MathHelper.floor(projectile.distanceTo(target) / speed)
		);
		((AccessorTntEntity) projectile).setExplosionPower(2);
		return projectile;
	}

	@Override
	public boolean handleShoot(Hand hand, LivingEntity target, float pullProgress) {
		if (super.handleShoot(hand, target, pullProgress)) return true;
		var stack = this.getStackInHand(hand);
		if (stack.isOf(Items.BLAZE_ROD)) {
			getEntityWorld().spawnEntity(createTNTFlyingTowards(this, target));
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
									DataComponentTypes.ITEM_MODEL, Season4.getID("tnt_launcher")
							).build()
					)
			);
		}
		return data;
	}

	@Override
	public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
		return super.isInvulnerableTo(world, source) || source.isIn(DamageTypeTags.IS_EXPLOSION);
	}

	public static class AvolineBrain extends PlayerBrain {

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
							(SingleTickTask<MobEntity>) ImprovedRangedApproachTask.create(1.0f, AvolineBrain::rangeOverride)
					), TaskTriggerer.runIf(
							AvolineBrain::shouldAttackPhysical,
							MeleeAttackTask.create(20)
					), new CrossbowAttackTask<>(),
					new SmartShootAttackTask<>(AvolineBrain::isSmartProjectileWeapon, 20, AvolineBrain::rangeOverride),
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
