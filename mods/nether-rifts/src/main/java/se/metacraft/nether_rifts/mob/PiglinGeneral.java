package se.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.api.AnimatedEntity;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import se.metacraft.nether_rifts.mixin.PiglinAiInvoker;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * The Piglin General, the Rift Captain: the artist's model at {@link #MODEL_SCALE}× (a box 1.3 across and 3.3 tall),
 * with the config's {@code mobs.general} stats. He walks after his target: his brain's attack target while it is one to
 * fight, else the nearest player who can fight within {@link #TARGET_RANGE} blocks. His moves (the swing, the spin, the
 * roar) are played through {@link #play}, one at a time, over his idle and walk.
 *
 * <p>He is a piglin to other piglins ({@link AbstractPiglin}), as the War Drummer is: they share their anger with him,
 * and when he is hit they turn on the attacker. His brain only remembers, with no behaviours, so he never hunts,
 * admires, barters, dances or picks anything up. A rift's General keeps the rift's rules ({@code RiftMobs}).
 */
public class PiglinGeneral extends AbstractPiglin implements AnimatedEntity {

	/** The model's scale, as tools/import_general.py drew it: keep the two the same. */
	public static final double MODEL_SCALE = 1.5;

	/** He goes after the nearest player who can fight this close, with no attack target of his own. */
	public static final double TARGET_RANGE = 24;
	/** Moving more than this a tick (blocks, across), he walks. */
	private static final double WALKING = 0.01;
	/** His moves play over his walk (5) and idle (0); his death over them all. */
	private static final int MOVE_PRIORITY = 10;
	private static final int DEATH_PRIORITY = 20;
	/** The death animation's length (2.76 s): his body stays this long, where vanilla's goes after 20 ticks. */
	public static final int DEATH_TICKS = 56;

	/** Memory only, as the drummer's: anger from other piglins, his piglins near, and the target he walks after. */
	private static final Brain.Provider<PiglinGeneral> BRAIN = Brain.provider(
			List.of(MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ANGRY_AT, MemoryModuleType.UNIVERSAL_ANGER),
			List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.PIGLIN_BRUTE_SPECIFIC_SENSOR),
			general -> List.of()
	);

	private final HeldItems.Holder<PiglinGeneral> holder;
	private boolean walking = false;
	private double lastX, lastZ;
	/** The move animation playing (one {@link #play} started), or null; while one plays, his walk and idle wait. */
	@Nullable
	private String move;

	public PiglinGeneral(EntityType<? extends PiglinGeneral> type, Level level) {
		super(type, level);
		this.holder = new HeldItems.Holder<>(this, RiftModels.PIGLIN_GENERAL);
		EntityAttachment.ofTicking(this.holder, this);
		this.holder.getAnimator().playAnimation("idle", 0, true);
		setCanPickUpLoot(false);
		getNavigation().setCanOpenDoors(false);
		// a rift spawns him from NBT, which skips finalizeSpawn; a saved General's own stats load over these
		applyTuning();
	}

	public static AttributeSupplier.Builder createAttributes() {
		var defaults = NetherRiftsConfig.GeneralTuning.DEFAULT;
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, defaults.health())
				.add(Attributes.ARMOR, defaults.armour())
				.add(Attributes.ARMOR_TOUGHNESS, defaults.toughness())
				.add(Attributes.MOVEMENT_SPEED, defaults.speed())
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, TARGET_RANGE);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		var data = super.finalizeSpawn(level, difficulty, reason, groupData);
		applyTuning();
		return data;
	}

	/** The config's health, armour, toughness and speed, as his attributes' bases, at full health. */
	private void applyTuning() {
		var tuning = tuning();
		setBase(Attributes.MAX_HEALTH, tuning.health());
		setBase(Attributes.ARMOR, tuning.armour());
		setBase(Attributes.ARMOR_TOUGHNESS, tuning.toughness());
		setBase(Attributes.MOVEMENT_SPEED, tuning.speed());
		setHealth(getMaxHealth());
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		var instance = getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	/** His tuning: the config's {@code mobs.general}. */
	public NetherRiftsConfig.GeneralTuning tuning() {
		return NetherRiftsConfig.getInstance().mobs().general();
	}

	/** The General's tuning, the same in every level: the config's {@code mobs.general}. */
	public static NetherRiftsConfig.GeneralTuning tuning(ServerLevel level) {
		return NetherRiftsConfig.getInstance().mobs().general();
	}

	@Override
	public HeldItems.Holder<PiglinGeneral> getHolder() {
		return holder;
	}

	/**
	 * Plays one of his move animations at {@link #MOVE_PRIORITY}, over his idle and walk, in place of the move playing
	 * (if any). The model says how it ends: {@code spin} loops and {@code spin_windup} holds, till {@link #stopMove} or
	 * the next move; the others play once.
	 */
	public void play(String animation) {
		var animator = holder.getAnimator();
		if (move != null) {
			animator.stopAnimation(move);
		}
		move = animation;
		animator.playAnimation(animation, MOVE_PRIORITY, true, frame -> {}, player -> {
			if (animation.equals(move)) {
				move = null;
			}
		});
	}

	/** Stops the move animation playing, if any: his walk and idle take over. */
	public void stopMove() {
		if (move != null) {
			holder.getAnimator().stopAnimation(move);
			move = null;
		}
	}

	/** The move animation playing, or null. */
	@Nullable
	public String move() {
		return move;
	}

	@Override
	protected Brain<PiglinGeneral> makeBrain(Brain.Packed packed) {
		return BRAIN.makeBrain(this, packed);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Brain<PiglinGeneral> getBrain() {
		return (Brain<PiglinGeneral>) super.getBrain();
	}

	/** Public, for the tests: a rift's General never turns zombie. */
	@Override
	public boolean isImmuneToZombification() {
		return super.isImmuneToZombification();
	}

	@Override
	protected boolean canHunt() {
		return false;
	}

	/** Never set: his arms are his model's. */
	@Override
	public PiglinArmPose getArmPose() {
		return PiglinArmPose.DEFAULT;
	}

	@Override
	protected void playConvertedSound() {
		makeSound(SoundEvents.PIGLIN_CONVERTED_TO_ZOMBIFIED);
	}

	/** He never picks anything up: no General barters. */
	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setCanPickUpLoot(false);
	}

	/** Hit by anything but a piglin, he gets angry at the attacker and calls his piglins in, as a piglin does. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof AbstractPiglin)) {
			PiglinAiInvoker.netherRifts$maybeRetaliate(level, this, attacker);
		}
		return hurt;
	}

	/** His move stops and he falls: {@code death} holds its last pose till his body goes ({@link #DEATH_TICKS}). */
	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) {
			stopMove();
			holder.getAnimator().stopAnimation("walk");
			walking = false;
			holder.getAnimator().playAnimation("death", DEATH_PRIORITY, true);
		}
	}

	/** Vanilla's, with his body kept till the death animation ends. */
	@Override
	protected void tickDeath() {
		deathTime++;
		if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
			level().broadcastEntityEvent(this, (byte) 60);
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			RiftModels.hurtTint(this, holder);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PIGLIN_BRUTE_ANGRY;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PIGLIN_BRUTE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PIGLIN_BRUTE_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.PIGLIN_BRUTE_STEP, 0.15f, 0.8f);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new ChaseGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		getBrain().tick(level, this);
		// turns zombie here, out of the nether (not a rift's General)
		super.customServerAiStep(level);
		if (isRemoved()) {
			return;
		}
		pickTarget(level);
		// moved since the last AI step (this tick's own move comes after it)
		boolean moving = Mth.lengthSquared(getX() - lastX, getZ() - lastZ) > WALKING * WALKING;
		lastX = getX();
		lastZ = getZ();
		if (move == null && moving != walking) {
			walking = moving;
			if (moving) {
				holder.getAnimator().playAnimation("walk", 5, true);
			} else {
				holder.getAnimator().stopAnimation("walk");
			}
		}
	}

	/**
	 * Keeps his brain's attack target while it is one to fight (alive, not in creative or spectator, within
	 * {@link #TARGET_RANGE}), else takes the nearest player who can fight that close, else none.
	 */
	private void pickTarget(ServerLevel level) {
		var brain = getBrain();
		var current = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		if (current != null && current.isAlive() && canAttack(current) && distanceToSqr(current) <= TARGET_RANGE * TARGET_RANGE) {
			return;
		}
		var player = level.getNearestPlayer(getX(), getY(), getZ(), TARGET_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
		if (player != null) {
			brain.setMemory(MemoryModuleType.ATTACK_TARGET, player);
		} else {
			brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	/** Walks after his target ({@link #getTarget}, his brain's), looking at it. No hit: his moves are his attacks. */
	private static final class ChaseGoal extends Goal {

		/** Ticks between new paths to the target. */
		private static final int REPATH_EVERY = 10;

		private final PiglinGeneral general;
		private int repath;

		ChaseGoal(PiglinGeneral general) {
			this.general = general;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			var target = general.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void stop() {
			general.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			var target = general.getTarget();
			if (target == null) {
				return;
			}
			general.getLookControl().setLookAt(target, 30f, 30f);
			if (--repath <= 0) {
				repath = REPATH_EVERY;
				general.getNavigation().moveTo(target, 1.0);
			}
		}
	}
}
