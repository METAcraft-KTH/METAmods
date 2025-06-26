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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ProjectileItem;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.MathHelper;
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
import se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerBrain;
import se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerMob;
import se.datasektionen.mc.metacraft_core.util.helper.EntityAIHelper;
import se.datasektionen.mc.metacraft_lib.entity.EntityParameters;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.TeleportHelper;
import se.datasektionen.mc.metacraft_season_4.Season4;
import se.datasektionen.mc.metacraft_season_4.boss.InventoryShuffleAttack;
import se.datasektionen.mc.metacraft_season_4.boss.PlayerShuffleAttack;
import se.datasektionen.mc.metacraft_season_4.entity.Season4Entities;
import se.datasektionen.mc.metacraft_season_4.entity.ai.FlightWithStrafeMoveControl;
import se.datasektionen.mc.metacraft_season_4.entity.ai.tasks.FlyingStrafeTask;
import se.datasektionen.mc.metacraft_season_4.entity.entities.MagicProjectile;
import se.datasektionen.mc.metacraft_season_4.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_season_4.mixin.AccessorLivingEntity;
import se.datasektionen.mc.metacraft_season_4.mixin.AccessorPlayerEntity;
import se.datasektionen.mc.metacraft_season_4.status_effects.Season4StatusEffects;
import se.datasektionen.mc.metacraft_season_4.util.DialogueHelper;
import se.metacraft.bosses.boss.AutoAttackingBoss;
import se.metacraft.bosses.boss.attacks.*;
import se.metacraft.bosses.boss.attacks.target.MoveToGround;
import se.metacraft.bosses.entity.entities.ItemSpawnerWithTarget;
import se.metacraft.bosses.util.StatusEffectEntry;

import java.util.*;
import java.util.function.Consumer;

public class GiocatBossEntity extends GenericBossPlayer implements AutoAttackingBoss {

	private static final GameProfile SKIN = new GameProfile(UUID.randomUUID(), "Giocat");

	static {
		SKIN.getProperties().put(
				"textures", new Property(
						"textures",
						"ewogICJ0aW1lc3RhbXAiIDogMTc0ODAyODY0MzM1OSwKICAicHJvZmlsZUlkIiA6ICI1ODc5MjNlNDkxMzM0ZDMzYWE4ZjQ3ZWJkZTljOTc3MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJFbGV2ZW5mb3VyMTAiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWEwN2Y2MjkyNTA0Y2E5MWVmM2NiYmM4YTcwNWRiOTc0OGVkNTM5YTdkZTVhMTIzYTdhOTU3YTg3Yjk2Y2RkNSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
						"hx54A3anDfCzZXZhITe0MIdOlqfO44ZX+by0RoW0L1cEiFc2Z76Ej8qqB+VKanqjyBs/OkO9lRRkyQCSrtPN+jwN2FEF2EJ0/viMoTFsd6w0ofquLaRj+BT/cqiniJdBlXjwZjXNz9HmCDChfwyZa4+i0b9SVV79DuCngteiZ2SjuyeMRZ0AhI3YvueGd7dUyakzLp6Xi6gpuu+OvlboQrCAtLc4YWQUxO3j+ZHj2BdxF23QOYnFSpD5nm6w9esTUHku96gvJ49IT58bArGqmB5PnH56ZbRzxWWfBxtM+gsSt/wFgS5lABoNTBR0oTwrdTGIn4u3WSMp9nmifUSsspG3bxJ+XW726YxUVxTHIgtvAczA1xb6k+9Th9NJf+Tp3A7h2LGNTzdrFvkwp0PulRqKmY8bvYG8RgS94P0kRNWfadX4JO7DfU/y0fw4YuuYp51Ys1TTzaFaB2bv72n6R6nzvoi9phFg+42FZ5+Y3tzCbVci2MzunUTzyXfANpoT/pzT+2DfkTswpT6ldSiBLVa/CX1tAX9nt39aR/7r6dscDTp3axf7v60Z/eTWmWxBBEOxF+SvWR7tI10fTBsrtsFQXIdrduNsLkP/8+ddL0WOowTefpA6WqC1DQr0GMHFuFvDSEcZgaXyHdZFjdvdSvXEz0jyzK0nxi6HNqEGzok="
				)
		);
	}

	public GiocatBossEntity(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
		this.moveControl = new FlightWithStrafeMoveControl(this, 10, false);
	}

	public static DefaultAttributeContainer.Builder createBossAttributes() {
		return PlayerMob.createPlayerAttributes().add(
				EntityAttributes.MAX_HEALTH, 512
		).add(
				EntityAttributes.FLYING_SPEED, PlayerMob.BASE_SPEED
		);
	}

	@Override
	protected GameProfile getDefaultSkin() {
		return SKIN;
	}

	@Override
	protected Text getDefaultName() {
		return Text.literal("Giocat");
	}

	private static final UniformIntProvider VERTICAL_RANGE = UniformIntProvider.create(0, 5);

	private static final UniformIntProvider HORIZONTAL_RANGE = UniformIntProvider.create(-10, 10);

	@Override
	protected Pool<Attack> createDefaultAttacks(RegistryWrapper.WrapperLookup lookup) {
		return Pool.<Attack>builder().add(
				new MultiAttack(
						InventoryShuffleAttack.createSimple(
								true, true, false,
								false, false
						),
						PlayerShuffleAttack.getInstance(),
						new SendMessageAttack(
								DialogueHelper.makeDialogue(Text.literal("Shuffle, shuffle!")),
								false
						)
				)
		).add(
				new SpawnForEachTarget(
						Pool.<EntityHelper.SpawnEntry>builder().add(
								ItemSpawnerWithTarget.createSpawnEntry(
										new ItemStack(Items.FIRE_CHARGE),
										HORIZONTAL_RANGE,
										VERTICAL_RANGE,
										new ItemSpawnerWithTarget.ProjectileOverride(
												EntityHelper.SpawnEntry.createEntityNBTFrom(
														EntityType.SMALL_FIREBALL
												),
												ProjectileItem.Settings.DEFAULT.power(),
												ProjectileItem.Settings.DEFAULT.uncertainty()
										),
										lookup
								)
						).add(
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
				StatusEffectAttack.create(
						Season4StatusEffects.SMALLIFY, UniformIntProvider.create(500, 600),
						ConstantIntProvider.create(4), SpawnForEachTarget.PLAYER_PREDICATE,
						ConstantLootNumberProvider.create(0.5f)
				)
		).add(
				new TeleportAttack(
						new MoveToGround(UniformFloatProvider.create(0, 50)),
						Optional.empty()
				)
		).add(
				new SpawnSpecifiedEntities(
						Pool.<EntityHelper.SpawnEntry>builder().add(
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
																"phantom_entity", true,
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
																"phantom_entity", true,
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
																"phantom_entity", true,
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
		).build();
	}

	@Override
	public boolean canHaveStatusEffect(StatusEffectInstance effect) {
		return super.canHaveStatusEffect(effect) && effect.getEffectType().value().getCategory() != StatusEffectCategory.HARMFUL;
	}

	@Override
	protected Brain<?> deserializeBrain(Dynamic<?> dynamic) {
		return GiocatBrain.create(this, this.createBrainProfile().deserialize(dynamic));
	}

	private boolean swapOnDamaged(ServerWorld world, Entity attacker, float amount, Consumer<Entity> redirector) {
		if (random.nextInt(Season4Entities.MAX_ATTACK_DAMAGE - Math.min(Season4Entities.MAX_ATTACK_DAMAGE-1, MathHelper.floor(amount))) == 0) {
			var players = getTargets(EntityType.PLAYER, e -> e != attacker);
			var player = players.isEmpty() ? attacker : players.get(random.nextInt(players.size()));
			if (player != null) {
				PlayerPosition target = PlayerPosition.fromEntity(player);
				PlayerPosition self = PlayerPosition.fromEntity(this);
				player.teleportTo(
						TeleportHelper.fromPlayerPos(world, self)
				);
				this.teleportTo(
						TeleportHelper.fromPlayerPos(world, target)
				);
				redirector.accept(player);
				addAttack(
						new SendMessageAttack(
								DialogueHelper.makeDialogue(Text.literal("Wow, that's messed up!")),
								false
						)
				);
				return true;
			}
		}
		return false;
	}

	public static float estimatePlayerDamage(PlayerEntity p, Entity target) {
		float f = p.isUsingRiptide() ? ((AccessorLivingEntity) p).getRiptideAttackDamage() : (float)p.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
		ItemStack itemStack = p.getWeaponStack();
		DamageSource damageSource = Optional.ofNullable(itemStack.getItem().getDamageSource(p)).orElse(p.getDamageSources().playerAttack(p));
		float g = ((AccessorPlayerEntity) p).callGetDamageAgainst(target, f, damageSource) - f;
		float h = p.getAttackCooldownProgress(0.5F);
		f *= 0.2F + h * h * 0.8F;
		g *= h;

		if (f > 0.0F || g > 0.0F) {
			boolean bl = h > 0.9F;
			f += itemStack.getItem().getBonusAttackDamage(target, f, damageSource);
			boolean bl3 = bl && p.fallDistance > 0.0F && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater() && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && target instanceof LivingEntity && !p.isSprinting();
			if (bl3) {
				f *= 1.5F;
			}

			return f + g;
		}
		return 0;
	}

	private static void handleThroughFriendlyFire(Entity target, Consumer<Entity> applier) {
		if (target instanceof ServerPlayerEntity) {
			((ServerPlayerEntityExtensions) target).metacraft_season_4$setAttackedThroughFriendlyFire(true);
		}
		applier.accept(target);
		if (target instanceof ServerPlayerEntity) {
			((ServerPlayerEntityExtensions) target).metacraft_season_4$setAttackedThroughFriendlyFire(false);
		}
	}

	private static void attackThroughFriendlyFire(PlayerEntity player, Entity target) {
		handleThroughFriendlyFire(
				target, player::attack
		);
	}

	private static void damageThroughFriendlyFire(ServerWorld world, DamageSource source, float amount, Entity target) {
		handleThroughFriendlyFire(
				target, t -> t.damage(world, source, amount)
		);
	}

	@Override
	public boolean handleAttack(Entity attacker) {
		if (attacker instanceof PlayerEntity p) {
			var amount = estimatePlayerDamage(p, this);
			if (swapOnDamaged(getServerWorld(), attacker, amount, target -> attackThroughFriendlyFire(p, target))) {
				return true;
			}
		}
		return super.handleAttack(attacker);
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) && !this.isInvulnerableTo(world, source) && swapOnDamaged(world, source.getAttacker(), amount, e -> damageThroughFriendlyFire(world, source, amount, e))) {
			return false;
		}
		return super.damage(world, source, amount);
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
	public boolean handleShoot(Hand hand, LivingEntity target, float pullProgress) {
		if (super.handleShoot(hand, target, pullProgress)) return true;
		var stack = this.getStackInHand(hand);
		float speed = 1.6f;
		float divergence = 14 - this.getWorld().getDifficulty().getId() * 4;
		if (stack.isOf(Items.BLAZE_ROD)) {
			var projectile = Season4Entities.MAGIC_PROJECTILE.create(getWorld(), SpawnReason.TRIGGERED);
			projectile.setOwner(this);
			projectile.setPos(getX(), getEyeY(), getZ());
			projectile.setHitEffects(
					MagicProjectile.createHitDefaults().add(
							StatusEffectEntry.create(
									StatusEffects.LEVITATION,
									MagicProjectile.MID,
									MagicProjectile.VARIES
							)
					).build()
			);
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
									DataComponentTypes.ITEM_MODEL, Season4.getID("fire")
							).build()
					)
			);
		}
		return data;
	}

	@Override
	public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
		return super.isInvulnerableTo(world, source) || source.isIn(DamageTypeTags.WITCH_RESISTANT_TO);
	}

	public static class GiocatBrain extends PlayerBrain {

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
							(SingleTickTask<MobEntity>) ImprovedRangedApproachTask.create(1.0f, GiocatBrain::rangeOverride)
					), TaskTriggerer.runIf(
							GiocatBrain::shouldAttackPhysical,
							MeleeAttackTask.create(20)
					), new CrossbowAttackTask<>(),
					new SmartShootAttackTask<>(GiocatBrain::isSmartProjectileWeapon, 20, GiocatBrain::rangeOverride),
					new FlyingStrafeTask(1, 8, 2, false)
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
