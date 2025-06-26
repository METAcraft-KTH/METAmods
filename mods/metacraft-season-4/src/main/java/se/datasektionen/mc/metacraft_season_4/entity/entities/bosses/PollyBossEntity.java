package se.datasektionen.mc.metacraft_season_4.entity.entities.bosses;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.ai.brain.task.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ProjectileItem;
import net.minecraft.loot.condition.EntityPropertiesLootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.*;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import net.minecraft.util.math.floatprovider.UniformFloatProvider;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.explosion.AdvancedExplosionBehavior;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftMemoryModules;
import se.datasektionen.mc.metacraft_core.entity_ref.SelfRef;
import se.datasektionen.mc.metacraft_core.music.ManageableServerBossBar;
import se.datasektionen.mc.metacraft_core.position_ref.AtEntityRef;
import se.datasektionen.mc.metacraft_core.position_ref.RandomRangeNoGravity;
import se.datasektionen.mc.metacraft_core.position_ref.WithTries;
import se.datasektionen.mc.metacraft_core.util.helper.BossBarHelper;
import se.datasektionen.mc.metacraft_core.util.helper.EntityAIHelper;
import se.datasektionen.mc.metacraft_lib.condition.entity_sub_predicates.HealthPredicate;
import se.datasektionen.mc.metacraft_lib.entity.EntityParameters;
import se.datasektionen.mc.metacraft_lib.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.ViewHelper;
import se.datasektionen.mc.metacraft_season_4.Season4;
import se.datasektionen.mc.metacraft_season_4.boss.ChangeTickSpeed;
import se.datasektionen.mc.metacraft_season_4.entity.Season4Entities;
import se.datasektionen.mc.metacraft_season_4.entity.ai.FlightWithStrafeMoveControl;
import se.datasektionen.mc.metacraft_season_4.entity.ai.Season4MemoryModules;
import se.datasektionen.mc.metacraft_season_4.entity.ai.Season4Sensors;
import se.datasektionen.mc.metacraft_season_4.entity.ai.tasks.FlyingStrafeTask;
import se.datasektionen.mc.metacraft_season_4.entity.ai.tasks.SimpleShootTask;
import se.datasektionen.mc.metacraft_season_4.entity.entities.MagicProjectile;
import se.datasektionen.mc.metacraft_season_4.extensions.LivingEntityExtensions;
import se.datasektionen.mc.metacraft_season_4.mixin.AccessorParrotEntity;
import se.datasektionen.mc.metacraft_season_4.status_effects.Season4StatusEffects;
import se.metacraft.bosses.boss.AutoAttackingBoss;
import se.metacraft.bosses.boss.attacks.*;
import se.metacraft.bosses.boss.attacks.target.PositionRefTarget;
import se.metacraft.bosses.entity.entities.ItemSpawnerWithTarget;
import se.metacraft.bosses.util.DoubleTeamHandler;
import se.metacraft.bosses.util.StatusEffectEntry;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.*;

public class PollyBossEntity extends ParrotEntity implements PolymerEntity, AutoAttackingBoss, RangedAttackMob {

	private static final String ATTACKS = "attacks";
	private static final Codec<Pool<Attack>> ATTACK_POOL_CODEC = Pool.createCodec(Attack.REGISTRY_CODEC);

	protected final AttackContainer container = new AttackContainer(this);
	protected Pool<Attack> attacks;

	private final InteractionElement sleepingHitbox = InteractionElement.redirect(this);
	private final ElementHolder holder = new ElementHolder();

	private BlockPos prevPos;
	private int stuckDelay = 0;

	private static final Attack TELEPORT = new TeleportAttack(
			new PositionRefTarget(
					new WithTries(
							new RandomRangeNoGravity(
									new AtEntityRef(
											SelfRef.getInstance(),
											Vec3d.ZERO, Vec3d.ZERO, false
									),
									EntityType.PARROT.getDimensions().scaled(16).getBoxAt(Vec3d.ZERO),
									Optional.empty(),
									UniformFloatProvider.create(10, 15)
							),
							15
					)
			),
			Optional.empty()
	);

	public PollyBossEntity(EntityType<? extends PollyBossEntity> entityType, World world) {
		super(entityType, world);
		this.moveControl = new FlightWithStrafeMoveControl(this, 10, false);
		EntityAttachment.ofTicking(holder, this);
	}

	public static final int RAM_COOLDOWN = 1000;

	private LivingEntity heldEntity;
	private int heldTimeSeconds = 0;
	private int insideWallTicks = 0;
	private float healthOnPickup = -1;

	public static final double DEFAULT_SCALE = 16;

	public static DefaultAttributeContainer.Builder createBossPollyAttributes() {
		return AnimalEntity.createAnimalAttributes().add(
				EntityAttributes.MAX_HEALTH, 1024
		).add(
				EntityAttributes.FLYING_SPEED, 2
		).add(
				EntityAttributes.MOVEMENT_SPEED, 2
		).add(
				EntityAttributes.ATTACK_DAMAGE, 5
		).add(
				EntityAttributes.ARMOR, 30
		).add(
				EntityAttributes.ARMOR_TOUGHNESS, 20
		).add(
				EntityAttributes.SCALE, DEFAULT_SCALE
		).add(
				EntityAttributes.FOLLOW_RANGE, 100
		);
	}

	@Override
	public void modifyRawEntityAttributeData(List<EntityAttributesS2CPacket.Entry> data, ServerPlayerEntity player, boolean initial) {
		if (this.getAttributeBaseValue(EntityAttributes.SCALE) == DEFAULT_SCALE && this.getAttributeInstance(EntityAttributes.SCALE).getModifiers().isEmpty()) {
			data.add(
					new EntityAttributesS2CPacket.Entry(
							EntityAttributes.SCALE,
							DEFAULT_SCALE, List.of()
					)
			);
		}
	}

	private static final Identifier HELD = Season4.getID("held");

	public void setHeldEntity(LivingEntity entity) {
		if (this.heldEntity != null) {
			var inst = this.heldEntity.getAttributeInstance(EntityAttributes.GRAVITY);
			if (inst != null) {
				inst.removeModifier(HELD);
			}
		}
		this.heldTimeSeconds = 0;
		this.healthOnPickup = this.getHealth();
		this.heldEntity = entity;
		if (entity != null) {
			entity.stopRiding();
			var inst = entity.getAttributeInstance(EntityAttributes.GRAVITY);
			if (inst != null) {
				inst.addTemporaryModifier(
						new EntityAttributeModifier(
								Season4.getID("held"), -1,
								EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
						)
				);
			}
		}
	}

	public void stun() {
		if (getBrain().hasMemoryModule(Season4MemoryModules.STUNNED)) return;
		var player = getPlayerTargets().stream().filter(p -> p != getHeldEntity()).min(
				Comparator.comparing(p -> p.squaredDistanceTo(this.getPos()))
		);
		if (player.isPresent()) {
			setVelocity(
					player.get().getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter()).normalize()
			);
		} else {
			setVelocity(Vec3d.ZERO);
		}
		setHeldEntity(null);
		getBrain().remember(Season4MemoryModules.STUNNED, Unit.INSTANCE, 200);
		getBrain().forget(MemoryModuleType.ATTACK_TARGET);
		getBrain().forget(Season4MemoryModules.SWOOP_TARGET);
		getBrain().forget(METAcraftMemoryModules.IS_SMART_SHOOTING);
		setSidewaysSpeed(0);
		getNavigation().stop();
		updateStunned();
	}

	@Override
	protected Box calculateDefaultBoundingBox(Vec3d pos) {
		if (getBrain() != null && getBrain().hasMemoryModule(Season4MemoryModules.STUNNED)) {
			var actualPos = pos.add(sleepingHitbox.getOffset());
			var rad = sleepingHitbox.getWidth()/2;
			return new Box(
					actualPos.getX() - rad, actualPos.getY(), actualPos.getZ() - rad,
					actualPos.getX() + rad,
					actualPos.getY() + sleepingHitbox.getHeight(),
					actualPos.getZ() + rad
			);
		}
		return super.calculateDefaultBoundingBox(pos);
	}

	private void updateStunned() {
		if (getBrain().hasMemoryModule(Season4MemoryModules.STUNNED)) {
			if (sleepingHitbox.getHolder() != holder) {
				sleepingHitbox.setOffset(Direction.WEST.getDoubleVector().multiply(getWidth()/2));
				sleepingHitbox.setHeight(getWidth());
				sleepingHitbox.setWidth(getWidth());
				holder.addElement(sleepingHitbox);
				refreshPosition();
			}
			if (getPose() != EntityPose.SLEEPING) {
				setPose(EntityPose.SLEEPING);
			}
			rotate(0, 0);
			setBodyYaw(0);
			this.lastBodyYaw = 0;
		} else {
			if (getPose() == EntityPose.SLEEPING) {
				setPose(EntityPose.STANDING);
			}
			if (sleepingHitbox.getHolder() == holder) {
				holder.removeElement(sleepingHitbox);
			}
		}
	}

	public LivingEntity getHeldEntity() {
		return heldEntity;
	}

	@Override
	protected Text getDefaultName() {
		return Text.literal("Temporal Polly");
	}

	@Override
	protected Brain<?> deserializeBrain(Dynamic<?> dynamic) {
		return PollyBrain.create(this, this.createBrainProfile().deserialize(dynamic));
	}

	@Override
	protected Brain.Profile<PollyBossEntity> createBrainProfile() {
		return Brain.createProfile(PollyBrain.MEMORY_MODULES, PollyBrain.SENSORS);
	}

	@Override
	protected void initGoals() {

	}

	@Override
	public Brain<PollyBossEntity> getBrain() {
		return (Brain<PollyBossEntity>) this.brain;
	}

	@Override
	public boolean canTarget(EntityType<?> target) {
		return super.canTarget(target) && (this.getScoreboardTeam() != null || target == EntityType.PLAYER);
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		return super.canTarget(target) && target != getHeldEntity();
	}

	protected boolean shouldContinueHoldingEntity() {
		return getHeldEntity().isAlive() &&
				getHeldEntity().getWorld() == this.getWorld() &&
				(!(getHeldEntity() instanceof ServerPlayerEntity p) || !p.isDisconnected()) &&
				!getHeldEntity().isInCreativeMode() &&
				!getHeldEntity().isSpectator();
	}

	private void healFromAttack(ServerWorld world, float amount) {
		if (getHeldEntity() == null) return;
		var source = world.getDamageSources().indirectMagic(this, this);
		if (getHeldEntity().damage(world, source, amount)) {
			this.heal(((LivingEntityExtensions) getHeldEntity()).metacraft_season_4$getPrevDamageAmount());
		}
	}

	public boolean surviveWith1HP(DamageSource source) {
		return !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	@Override
	public boolean metacraft_season_4$surviveDeath(DamageSource source) {
		if (surviveWith1HP(source)) {
			setHeldEntity(null);
			setHealth(1);
			return true;
		}
		return false;
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (surviveWith1HP(source) && getHealth() <= 1) {
			return false;
		}
		if (getHeldEntity() != null) {
			if (source.getAttacker() == getHeldEntity()) {
				healFromAttack(world, amount);
				return false;
			}
			if (source.getAttacker() instanceof Ownable o && o.getOwner() == getHeldEntity()) {
				healFromAttack(world, amount);
				return false;
			}
		}
		if (super.damage(world, source, amount)) {
			if (getHeldEntity() != null && (amount > 20 || getHealth() - healthOnPickup > 100)) {
				world.createExplosion(
						source.getAttacker(), world.getDamageSources().explosion(source.getSource(), source.getAttacker()),
						new AdvancedExplosionBehavior(
								false, true, Optional.empty(), Optional.empty()
						),
						getX(), getBoundingBox().getCenter().getY(), getZ(),
						10, false, World.ExplosionSourceType.TRIGGER
				);
				stun();
			}
			return true;
		}
		return false;
	}

	private void updateHeldEntity(ServerWorld world) {
		if (getHeldEntity() != null) {
			if (shouldContinueHoldingEntity()) {
				getHeldEntity().setPosition(
						this.getX(), this.getY()-getHeldEntity().getHeight(), this.getZ()
				);
				world.getChunkManager().sendToNearbyPlayers(
						this, EntityPositionS2CPacket.create(
								getHeldEntity().getId(), new PlayerPosition(getHeldEntity().getPos(), Vec3d.ZERO, 0, 0),
								Sets.union(PositionFlag.ROT, PositionFlag.DELTA), false
						)
				);
				getHeldEntity().fallDistance = 0;
				if (getHeldEntity() instanceof LivingEntity living && age % 20 == 0) {
					healFromAttack(world, 2);
					if (random.nextInt(3) == 0) {
						living.addStatusEffect(
								new StatusEffectInstance(
										Season4StatusEffects.SMALLIFY, 1200,
										Integer.min(
												Optional.ofNullable(living.getStatusEffect(Season4StatusEffects.SMALLIFY)).map(
														StatusEffectInstance::getAmplifier
												).orElse(-1) + 1,
												8
										)
								)
						);
					}
					living.addStatusEffect(
							new StatusEffectInstance(
									Season4StatusEffects.HEALTH_REDUCTION, 1200,
									Integer.min(
											Optional.ofNullable(living.getStatusEffect(Season4StatusEffects.HEALTH_REDUCTION)).map(
													StatusEffectInstance::getAmplifier
											).orElse(-1) + 1,
											40
									)
							)
					);
					if (heldTimeSeconds > 60) {
						setHeldEntity(null);
						return;
					}
					heldTimeSeconds++;
				}
				if (getHeldEntity().isInsideWall()) {
					insideWallTicks++;
					if (insideWallTicks > 20) {
						getHeldEntity().requestTeleport(
								this.getX(), this.getY(), this.getZ()
						);
						setHeldEntity(null);
					}
				} else {
					insideWallTicks = 0;
				}
			} else {
				if (getHeldEntity() instanceof ServerPlayerEntity p && p.isDisconnected()) {
					if (p.networkHandler.getLatency() < 1000) {
						heal(20);
					}
				}
				setHeldEntity(null);
			}
		}
	}

	@Override
	public void mobTick(ServerWorld world) {
		getBrain().tick(world, this);
		PollyBrain.updateActivities(this);
		super.mobTick(world);

		if (!getBrain().hasMemoryModule(Season4MemoryModules.STUNNED)) {

			if (getBlockPos().equals(prevPos)) {
				stuckDelay++;
				if (stuckDelay > 200) {
					getBrain().forget(Season4MemoryModules.SWOOP_TARGET);
					addAttack(TELEPORT);
					stuckDelay = 0;
				}
			} else {
				prevPos = getBlockPos();
				stuckDelay = 0;
			}

			container.tickAttackDelay();
		}
		container.tickAttacks();

		updateHeldEntity(world);

		updateStunned();
	}

	@Override
	public int getMaxAttacks() {
		return 10;
	}

	@Override
	public AttackContainer getAttacks() {
		return container;
	}

	@Override
	public Attack.BossContext<?> getContext(Attack attack) {
		return new Attack.BossContext<>(this, random);
	}

	@Override
	public int getNewAttackDelay(Attack chosen) {
		return 500;
	}

	@Override
	public Optional<Attack> chooseAttack() {
		return attacks.getOrEmpty(random);
	}

	@Override
	public Entity getAsEntity() {
		return this;
	}

	@Override
	public void writeCustomData(WriteView nbt) {
		super.writeCustomData(nbt);
		nbt.put(
				ATTACKS,
				ATTACK_POOL_CODEC,
				attacks
		);
		container.writeNBT(nbt);
	}

	@Override
	public void readCustomData(ReadView nbt) {
		super.readCustomData(ViewHelper.filtered(nbt, Set.of("Owner")));
		attacks = nbt.read(ATTACKS, ATTACK_POOL_CODEC).orElse(Pool.empty());
		container.readNBT(nbt);
	}

	@Override
	public boolean isReadyToSitOnPlayer() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isInSittingPose() {
		return false;
	}

	@Override
	public boolean isTamed() {
		return false;
	}

	@Override
	public LazyEntityReference<LivingEntity> getOwnerReference() {
		return null;
	}

	@Override
	public boolean isOwner(LivingEntity entity) {
		return false;
	}

	@Override
	public void setTamed(boolean tamed, boolean updateAttributes) {

	}

	@Override
	public void setInSittingPose(boolean inSittingPose) {

	}

	@Override
	public void setOwner(@Nullable LazyEntityReference<LivingEntity> owner) {

	}

	@Override
	public void setOwner(@Nullable LivingEntity owner) {

	}

	@Override
	public void setTamedBy(PlayerEntity player) {

	}

	private static final UniformIntProvider VERTICAL_RANGE = UniformIntProvider.create(0, 10);

	private static final UniformIntProvider HORIZONTAL_RANGE = UniformIntProvider.create(-20, 20);

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		return ActionResult.PASS;
	}

	public static final Pool<StatusEffectEntry> EXTRA_POOL_HIT = MagicProjectile.createHitDefaults().add(
			StatusEffectEntry.create(Season4StatusEffects.SMALLIFY, UniformIntProvider.create(100, 200), UniformIntProvider.create(4, 7))
	).add(
			StatusEffectEntry.create(Season4StatusEffects.HEALTH_REDUCTION, UniformIntProvider.create(100, 200), UniformIntProvider.create(4, 7))
	).build();

	public static final Pool<MagicProjectile.EffectEntry> EXTRA_POOL_CLOUD = MagicProjectile.createCloudDefaults().add(
			MagicProjectile.EffectEntry.create(StatusEffectEntry.create(Season4StatusEffects.SMALLIFY, UniformIntProvider.create(100, 200), UniformIntProvider.create(0, 3)))
	).add(
			MagicProjectile.EffectEntry.create(StatusEffectEntry.create(Season4StatusEffects.HEALTH_REDUCTION, UniformIntProvider.create(100, 200), UniformIntProvider.create(0, 3)))
	).build();

	protected Pool<Attack> createDefaultAttacks(RegistryWrapper.WrapperLookup lookup) {
		return Pool.<Attack>builder().add(
				new ChangeTickSpeed(UniformIntProvider.create(10, 20), UniformFloatProvider.create(10, 40))
		).add(
				new SpawnForEachTarget(
						Pool.<EntityHelper.SpawnEntry>builder().add(
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
													Season4Entities.MAGIC_PROJECTILE,
													EntityHelper.SpawnEntry.createNBTFromMap(
														Map.of(
															MagicProjectile.HIT_EFFECTS,
															MagicProjectile.createEffectPool(
																lookup, EXTRA_POOL_HIT
															),
															MagicProjectile.CLOUD_EFFECTS,
															MagicProjectile.createCloudEffectPool(
																	lookup, EXTRA_POOL_CLOUD
															),
															MagicProjectile.HIT_EFFECT_COUNT,
															MagicProjectile.createIntProvider(
																	lookup, UniformIntProvider.create(3, 5)
															),
															MagicProjectile.CLOUD_EFFECT_COUNT,
															MagicProjectile.createIntProvider(
																	lookup, UniformIntProvider.create(1, 2)
															)
														)
													)
												),
												ProjectileItem.Settings.DEFAULT.power(),
												ProjectileItem.Settings.DEFAULT.uncertainty()
										),
										lookup
								)
						).build(),
						List.of(SpawnForEachTarget.PLAYER_PREDICATE),
						ConstantIntProvider.create(1),
						true
				), 50
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
																EntityParameters.SURVIVES_SUNLIGHT, true
														)
												)
										),
										15, 15
								)
						).add(
								SpawnForEachTarget.createEntry(
										SpawnForEachTarget.createEntityNBTFrom(
												EntityType.RAVAGER
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
				), 50
		).add(
				new ConditionalAttack(
						new SpawnSpecifiedEntities(
								Pool.<EntityHelper.SpawnEntry>builder().add(
										SpawnForEachTarget.createEntry(
												SpawnForEachTarget.createEntityNBTFrom(
														EntityType.WARDEN
												),
												Optional.empty(),
												SpawnReason.TRIGGERED,
												20, 20
										)
								).add(
										SpawnForEachTarget.createEntry(
												SpawnForEachTarget.createEntityNBTFrom(
														EntityType.WITHER
												),
												20, 20
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
						),
						new EntityPropertiesLootCondition(
								Optional.of(
										EntityPredicate.Builder.create().typeSpecific(
												new HealthPredicate(
														NumberRange.DoubleRange.atMost(0.5),
														true
												)
										).build()
								),
								LootContext.EntityTarget.THIS
						)
				)
		).add(
				TELEPORT
		).build();
	}

	protected ManageableServerBossBar createDefaultBossBar() {
		return new ManageableServerBossBar(
				getDisplayName(), BossBar.Color.BLUE, BossBar.Style.NOTCHED_20
		);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
		var data = super.initialize(world, difficulty, spawnReason, entityData);
		((EntityExtensions) this).metacraft_lib$setPreventEnterVehicle(true);
		((EntityExtensions) this).metacraft_lib$setHideUUIDInTooltip(true);
		this.getBrain().remember(MemoryModuleType.RAM_COOLDOWN_TICKS, RAM_COOLDOWN);
		((AccessorParrotEntity) this).callSetVariant(Variant.YELLOW_BLUE);
		if (data instanceof DoubleTeamHandler h) {
			attacks = Pool.empty();
			this.setLeftHanded(h.primary() instanceof MobEntity m ? m.isLeftHanded() : this.isLeftHanded());
		} else {
			this.attacks = createDefaultAttacks(world.getRegistryManager());
			if (BossBarHelper.getBossBar(this).isEmpty()) {
				BossBarHelper.setBossBar(
						this, createDefaultBossBar()
				);
			}
		}
		return data;
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityType.PARROT;
	}

	public Optional<LivingEntity> getHurtBy() {
		return this.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HURT_BY).map(DamageSource::getAttacker).filter((attacker) -> attacker instanceof LivingEntity).map((livingAttacker) -> (LivingEntity)livingAttacker);
	}

	@Override
	protected float getOffGroundSpeed() {
		return 0.1f * this.getMovementSpeed();
	}

	@Override
	public float metacraft_season_4$updateYDrag(float drag) {
		return getBrain().hasMemoryModule(Season4MemoryModules.STUNNED) ? 0.98f : drag;
	}

	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		float speed = 1.6f;
		float divergence = 14 - this.getWorld().getDifficulty().getId() * 4;
		var projectile = Season4Entities.MAGIC_PROJECTILE.create(getWorld(), SpawnReason.TRIGGERED);
		projectile.setOwner(this);
		projectile.setPos(getX(), getEyeY(), getZ());
		projectile.setHitEffects(EXTRA_POOL_HIT);
		projectile.setCloudEffects(EXTRA_POOL_CLOUD);
		projectile.setHitEffectCount(UniformIntProvider.create(3, 5));
		projectile.setHitEffectCount(UniformIntProvider.create(1, 2));
		EntityAIHelper.shootProjectile(
				this, projectile, target,
				SoundEvents.ENTITY_GHAST_SHOOT, speed, divergence, 10
		);
	}

	public static class PollyBrain {

		static final List<SensorType<? extends Sensor<? super PollyBossEntity>>> SENSORS = ImmutableList.of(
				SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY, SensorType.NEAREST_PLAYERS, Season4Sensors.TARGET_ENTITY_SENSOR
		);
		static final List<MemoryModuleType<?>> MEMORY_MODULES = ImmutableList.of(
				MemoryModuleType.LOOK_TARGET, MemoryModuleType.VISIBLE_MOBS, MemoryModuleType.NEAREST_ATTACKABLE,
				MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET,
				MemoryModuleType.WALK_TARGET, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY,
				MemoryModuleType.PATH, Season4MemoryModules.SWOOP_TARGET, MemoryModuleType.RAM_COOLDOWN_TICKS,
				Season4MemoryModules.STUNNED, METAcraftMemoryModules.IS_SMART_SHOOTING
		);

		protected static Brain<?> create(PollyBossEntity polly, Brain<PollyBossEntity> brain) {
			addCoreTasks(brain);
			addIdleTasks(brain);
			addFightTasks(polly, brain);
			addRamTasks(brain);
			addRestTasks(polly, brain);
			brain.setCoreActivities(Set.of(Activity.CORE));
			brain.setDefaultActivity(Activity.FIGHT);
			brain.resetPossibleActivities();
			return brain;
		}

		private static void addCoreTasks(Brain<PollyBossEntity> brain) {
			brain.setTaskList(
					Activity.CORE, 0, ImmutableList.of(
							new StayAboveWaterTask<>(0.8f),
							new UpdateLookControlTask(45, 90),
							new TickCooldownTask(MemoryModuleType.RAM_COOLDOWN_TICKS) {

								private boolean hasHeld(LivingEntity e) {
									if (e instanceof PollyBossEntity b) {
										return b.getHeldEntity() != null;
									}
									return false;
								}

								@Override
								protected void keepRunning(ServerWorld world, LivingEntity entity, long time) {
									if (!hasHeld(entity)) {
										super.keepRunning(world, entity, time);
									}
								}
							}
					)
			);
		}

		private static void addIdleTasks(Brain<PollyBossEntity> brain) {
			brain.setTaskList(
					Activity.IDLE,
					ImmutableList.of(
							Pair.of(0, UpdateAttackTargetTask.create((world, polly) -> polly.getBrain().getOptionalRegisteredMemory(MemoryModuleType.NEAREST_ATTACKABLE))),
							Pair.of(1, UpdateAttackTargetTask.create((world, polly) -> polly.getHurtBy()))
							//Pair.of(3, new RandomTask<>(ImmutableList.of(Pair.of(new WaitTask(20, 100), 1),Pair.of(StrollTask.create(0.6f), 2))))
					)
			);
		}

		private static void addRamTasks(Brain<PollyBossEntity> brain) {
			brain.setTaskList(
					Activity.RAM,
					ImmutableList.of(
							Pair.of(0, new PollySwoopTask())
					),
					ImmutableSet.of(
							Pair.of(MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryModuleState.VALUE_ABSENT),
							Pair.of(Season4MemoryModules.SWOOP_TARGET, MemoryModuleState.VALUE_PRESENT)
					)
			);
		}

		private static void addFightTasks(PollyBossEntity polly, Brain<PollyBossEntity> brain) {
			brain.setTaskList(
					Activity.FIGHT,
					ImmutableList.of(
						Pair.of(0, ForgetAttackTargetTask.create(Sensor.hasTargetBeenAttackableRecently(polly, 100).negate()::test)),
						Pair.of(1, new FlyingStrafeTask(1, 16, 10, false)),
						Pair.of(2, new SimpleShootTask<>(50, 50, i -> {
							if (i == 20) {
								polly.playSound(SoundEvents.ENTITY_GHAST_WARN, 10, 0.75f);
							}
						})),
						Pair.of(3, TaskTriggerer.task(
							ctx -> ctx.point((world, entity, time) -> {
								return entity.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).map(target -> {
									if (entity.getY() - target.getY() > 7 && polly.getHeldEntity() == null) {
										entity.getBrain().remember(Season4MemoryModules.SWOOP_TARGET, target);
										return true;
									}
									return false;
								}).orElse(false);
							})
						))
					),
					ImmutableSet.of(
						Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryModuleState.VALUE_PRESENT),
						Pair.of(MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT)
					)
			);
		}

		private static void addRestTasks(PollyBossEntity polly, Brain<PollyBossEntity> brain) {
			brain.setTaskList(
					Activity.REST, ImmutableList.of(),
					ImmutableSet.of(Pair.of(Season4MemoryModules.STUNNED, MemoryModuleState.VALUE_PRESENT))
			);
		}

		static void updateActivities(PollyBossEntity polly) {
			polly.getBrain().resetPossibleActivities(ImmutableList.of(Activity.REST, Activity.RAM, Activity.FIGHT, Activity.IDLE));
		}

		public static class PollySwoopTask extends MultiTickTask<PollyBossEntity> {

			public PollySwoopTask() {
				super(ImmutableMap.of(
						Season4MemoryModules.SWOOP_TARGET, MemoryModuleState.VALUE_PRESENT,
						MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryModuleState.VALUE_ABSENT
				));
			}

			@Override
			protected boolean shouldKeepRunning(ServerWorld serverWorld, PollyBossEntity polly, long l) {
				return polly.getBrain().hasMemoryModule(Season4MemoryModules.SWOOP_TARGET);
			}

			@Override
			protected void keepRunning(ServerWorld world, PollyBossEntity polly, long l) {
				polly.getBrain().getOptionalRegisteredMemory(Season4MemoryModules.SWOOP_TARGET).ifPresent(
						target -> {
							polly.getMoveControl().moveTo(target.getX(), target.getBodyY(0.5), target.getZ(), 1);
							if (!target.isAlive() || target.isInCreativeMode() || target.isSpectator()) {
								polly.getBrain().forget(Season4MemoryModules.SWOOP_TARGET);
								return;
							}
							if (polly.getBoundingBox().expand(1).intersects(target.getBoundingBox())) {
								polly.getBrain().remember(MemoryModuleType.RAM_COOLDOWN_TICKS, RAM_COOLDOWN);
								polly.getBrain().forget(Season4MemoryModules.SWOOP_TARGET);
								polly.setHeldEntity(target);
							}
						}
				);
			}

		}
	}
}
