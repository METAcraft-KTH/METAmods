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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import se.metacraft.nether_rifts.mixin.PiglinAiInvoker;
import se.metacraft.nether_rifts.raid.RiftMobs;
import se.metacraft.nether_rifts.raid.RiftRaid;
import se.metacraft.nether_rifts.rift.RiftData;
import se.metacraft.nether_rifts.rift.Stage;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Piglin General, the Rift Captain: the artist's model at {@link #MODEL_SCALE}× (a box 1.3 across and 3.3 tall),
 * with the config's {@code mobs.general} stats. He walks after his target: his brain's attack target while it is one to
 * fight, else the nearest player who can fight within {@link #TARGET_RANGE} blocks. His moves (the swing, the spin, the
 * roar) are chosen one at a time by his {@link GeneralTactics}, and played through {@link #play} over his idle and walk;
 * during one he stands still, but for the spin's drift. Spinning, he turns projectiles back ({@link #REFLECTION});
 * dizzy, he takes more damage. As his health first drops below each of the config's {@code roar_at} shares he roars,
 * and a rift's General calls his guard through it ({@code RiftRaid.spawnGuard}) at the players who hurt him
 * ({@link #attackers}). Like a wither, he never despawns for distance.
 *
 * <p>He is a piglin to other piglins ({@link AbstractPiglin}), as the War Drummer is: they share their anger with him,
 * and when he is hit they turn on the attacker. His brain only remembers, with no behaviours, so he never hunts,
 * admires, barters, dances or picks anything up, and he never turns zombie. A rift's General keeps the rift's rules
 * ({@code RiftMobs}).
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
	/** A projectile he turns back goes back at its shooter this far off at most; past it, back the way it came. */
	public static final double REFLECT_RANGE = 64;
	/** The death animation's length (2.76 s): his body stays this long, where vanilla's goes after 20 ticks. */
	public static final int DEATH_TICKS = 56;
	/** How long a mob he sets on someone stays angry at them, in ticks: a piglin's own anger (vanilla's 30 s). */
	public static final int ANGER_TICKS = 600;

	/** Memory only, as the drummer's: anger from other piglins, his piglins near, and the target he walks after. */
	private static final Brain.Provider<PiglinGeneral> BRAIN = Brain.provider(
			List.of(MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ANGRY_AT, MemoryModuleType.UNIVERSAL_ANGER),
			List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.PIGLIN_BRUTE_SPECIFIC_SENSOR),
			general -> List.of()
	);

	private final HeldItems.Holder<PiglinGeneral> holder;
	private final GeneralTactics tactics = new GeneralTactics(this);
	private boolean walking = false;
	private double lastX, lastZ;
	/** The move animation playing (one {@link #play} started), or null; while one plays, his walk and idle wait. */
	@Nullable
	private String move;
	/** The players who have hurt him while he lives, first first; not saved. */
	private final Set<UUID> attackers = new LinkedHashSet<>();
	/** How many of his {@code roar_at} thresholds he has roared for (queued counts): saved, so a restart doesn't give them back; those still to play are saved too (roars_queued). */
	private int roarsUsed = 0;

	public PiglinGeneral(EntityType<? extends PiglinGeneral> type, Level level) {
		super(type, level);
		this.holder = new HeldItems.Holder<>(this, RiftModels.PIGLIN_GENERAL);
		EntityAttachment.ofTicking(this.holder, this);
		this.holder.getAnimator().playAnimation("idle", 0, true);
		setCanPickUpLoot(false);
		setImmuneToZombification(true);
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

	/** His fight: the move he is in, one at a time. */
	public GeneralTactics tactics() {
		return tactics;
	}

	/** Whether he is spinning ({@link GeneralTactics.Move#SPIN}): projectiles turn back off him. Never once he is dying. */
	public boolean spinning() {
		return !isDeadOrDying() && tactics.move() == GeneralTactics.Move.SPIN;
	}

	/** Whether he is dizzy after a spin ({@link GeneralTactics.Move#DIZZY}): he takes more damage. */
	public boolean dizzy() {
		return !isDeadOrDying() && tactics.move() == GeneralTactics.Move.DIZZY;
	}

	/** A projectile hit him at this game time: his spin may come early. */
	public void noteProjectileHit(long gameTime) {
		tactics.noteProjectileHit(gameTime);
	}

	/** The players who have hurt him while he lives, in the order they first did. */
	public Set<UUID> attackers() {
		return Collections.unmodifiableSet(attackers);
	}

	/** How many roars he has had (or has queued) of his {@code roar_at} thresholds. */
	public int roarsUsed() {
		return roarsUsed;
	}

	/**
	 * Halfway through a roar ({@link GeneralTactics#ROAR_SPAWN_TICK}): if he is alive and his rift is raiding, his guard
	 * comes through it. A General of no rift (a spawn egg's, a summoned one) or whose rift is gone calls nobody.
	 */
	public void callGuard(ServerLevel level) {
		if (!isAlive()) {
			return;
		}
		RiftMobs.riftOf(this).flatMap(id -> RiftData.get(level).byId(id))
				.filter(rift -> rift.stage() == Stage.RAID)
				.ifPresent(rift -> RiftRaid.spawnGuard(level, rift, this));
	}

	/**
	 * Sets a mob on {@code target} so that it sticks: as its brain's attack target and its anger (for
	 * {@link #ANGER_TICKS}), where its brain has those memories (a piglin's drops a target it isn't angry at), and as its
	 * goals' target; a mount's riders too.
	 */
	public static void provoke(Mob mob, LivingEntity target) {
		var brain = mob.getBrain();
		if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
			brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
		}
		if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)) {
			brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), ANGER_TICKS);
		}
		mob.setTarget(target);
		for (var rider : mob.getPassengers()) {
			if (rider instanceof Mob riding) {
				provoke(riding, target);
			}
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

	/** He never turns zombie, wherever he came from (a rift, a spawn egg, /summon), whatever a save says. */
	@Override
	public boolean isImmuneToZombification() {
		return true;
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
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("roars_used", roarsUsed);
		// counted in roars_used as they queued: unsaved, a restart before they play would lose their guard
		output.putInt("roars_queued", tactics.roarsPending());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setCanPickUpLoot(false);
		roarsUsed = input.getIntOr("roars_used", 0);
		tactics.setRoarsQueued(Math.min(input.getIntOr("roars_queued", 0), roarsUsed));
	}

	/**
	 * Dizzy, he takes {@code dizzy_damage_taken} times the damage; a projectile's hit brings his spin on early. Hit by
	 * anything but a piglin, he gets angry at the attacker and calls his piglins in, as a piglin does. A player who hurts
	 * him is one of his {@link #attackers}. Left alive below roar thresholds he hasn't roared for, he roars for each
	 * ({@link #queueRoars}). His own raid's hits never land, nor count toward any of that.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// before the rest: LivingEntityMixin's check comes only with super's
		if (RiftMobs.spares(this, source)) {
			return false;
		}
		if (dizzy()) {
			damage *= (float) tuning().dizzyDamageTaken();
		}
		if (source.getDirectEntity() instanceof Projectile) {
			noteProjectileHit(level.getGameTime());
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof AbstractPiglin)) {
			PiglinAiInvoker.netherRifts$maybeRetaliate(level, this, attacker);
		}
		if (hurt && source.getEntity() instanceof Player player) {
			attackers.add(player.getUUID());
		}
		if (!isDeadOrDying()) {
			queueRoars();
		}
		return hurt;
	}

	/**
	 * A roar for each {@code roar_at} threshold (highest first) his health is now below and he hasn't roared for: one
	 * hit across two queues two, played back to back.
	 */
	private void queueRoars() {
		var at = tuning().roar().at().stream().sorted(Comparator.reverseOrder()).toList();
		float share = getHealth() / getMaxHealth();
		while (roarsUsed < at.size() && share < at.get(roarsUsed)) {
			roarsUsed++;
			tactics.queueRoar();
		}
	}

	/** Spinning, he turns every projectile that reaches him back ({@link #REFLECTION}); else as any entity. */
	@Override
	public ProjectileDeflection deflection(Projectile projectile) {
		return spinning() ? REFLECTION : super.deflection(projectile);
	}

	/**
	 * Who owns a projectile he turned back, from its shooter's: he does, so its hit is his, except where a projectile's
	 * owner is more than who it hurts. A trident stays its thrower's, to pick up or come back by loyalty; a fishing hook
	 * its rod's; an ender pearl its thrower's, as it would carry its owner, him, to where it lands.
	 */
	@Nullable
	public EntityReference<Entity> ownerOfReflected(Projectile projectile, @Nullable EntityReference<Entity> shooter) {
		return projectile instanceof ThrownTrident || projectile instanceof FishingHook || projectile instanceof ThrownEnderpearl
				? shooter
				: EntityReference.of(this);
	}

	/**
	 * The spin's deflection: the projectile goes back at its shooter's eyes, at the speed it came, if the shooter is
	 * alive in its level within {@link #REFLECT_RANGE} blocks; else (a dispenser's, a shooter gone, dead or elsewhere)
	 * straight back the way it came. Its new owner is {@link #ownerOfReflected}'s (ProjectileReflectionMixin, as
	 * vanilla puts the old one back after a deflection), and it passes through him after.
	 */
	public static final ProjectileDeflection REFLECTION = (projectile, general, random, power) -> {
		var velocity = projectile.getDeltaMovement();
		var back = projectile.getOwner() instanceof LivingEntity shooter && shooter != general && shooter.isAlive()
				&& shooter.level() == projectile.level() && shooter.distanceToSqr(projectile) <= REFLECT_RANGE * REFLECT_RANGE
				? shooter.getEyePosition().subtract(projectile.position()).normalize().scale(velocity.length())
				: velocity.reverse();
		projectile.setDeltaMovement(back);
		projectile.needsSync = true;
	};

	/** He never despawns for being far from players, as a wither doesn't. */
	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	/** His move stops and he falls: {@code death} holds its last pose till his body goes ({@link #DEATH_TICKS}). */
	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) {
			tactics.stop();
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
		goalSelector.addGoal(1, new HoldGoal(this));
		goalSelector.addGoal(2, new ChaseGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		getBrain().tick(level, this);
		// a piglin turns zombie here, out of the nether; he never does (isImmuneToZombification)
		super.customServerAiStep(level);
		if (isRemoved()) {
			return;
		}
		pickTarget(level);
		// a showcase General only poses (Showcase plays his animations)
		if (!Showcase.isShowcase(this)) {
			tactics.tick(level);
		}
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
			return target != null && target.isAlive() && general.tactics.move() == GeneralTactics.Move.CHASE;
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
			// a move started since the goals last chose: he stands, and the hold takes over
			if (target == null || general.tactics.move() != GeneralTactics.Move.CHASE) {
				return;
			}
			general.getLookControl().setLookAt(target, 30f, 30f);
			if (--repath <= 0) {
				repath = REPATH_EVERY;
				general.getNavigation().moveTo(target, 1.0);
			}
		}
	}

	/**
	 * In any move but the chase he stands where he is: no walking, strolling or looking about. Which way he faces is
	 * the move's ({@link GeneralTactics}).
	 */
	private static final class HoldGoal extends Goal {

		private final PiglinGeneral general;

		HoldGoal(PiglinGeneral general) {
			this.general = general;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return general.tactics.move() != GeneralTactics.Move.CHASE;
		}

		@Override
		public void start() {
			general.getNavigation().stop();
		}
	}
}
