package se.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.api.AnimatedEntity;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import se.metacraft.nether_rifts.mixin.PiglinAiInvoker;
import se.metacraft.nether_rifts.raid.RiftMobs;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Leads every rift wave from wave 2, and now and then walks with the nether's own piglins (a piglin spawning naturally
 * in the nether brings one along at the config's {@code natural_chance}). It keeps away from players and drums as it
 * goes: a beat every 10 ticks;
 * every 4th is the big beat, for which it stands its ground, winds up (arms raised) and slams
 * {@link #BIG_BEAT_SLAM_TICK} ticks later. War Beat gives its piglins within 12 blocks Speed I + Strength I for 6 s
 * (a rift's drummer buffs rift piglins, a nether one the nether's own grown piglins),
 * and Shock Beat hurts players within 4 blocks (2 hearts) and throws them back. The beat is on a fixed tempo, so players can learn to hit
 * between beats. The numbers are the defaults of the config's {@code mobs.drummer} section.
 *
 * <p>It drums only in combat: with a player who can fight within {@link #COMBAT_RANGE} blocks, once it has seen them,
 * or with one of its piglins that close fighting something (a player, or a hoglin on a hunt)
 * (a fight starts with a war cry, if it has been out of one for 10 s). Otherwise it idles,
 * and walks when it moves. BIL gives a .bbmodel's animations every bone, so the highest-priority one playing poses
 * the whole model: the {@code beat} (a 1 s loop of two strikes, each heard as its stick lands: {@link #loop})
 * carries the walk's legs (merged in on import), so it marches as it drums;
 * {@code big_beat} (2.8 s, the slam at 1.8 s) moves every bone, and {@code idle} is under them all.
 *
 * <p>It is a piglin to other piglins ({@link AbstractPiglin}): they count it among the grown piglins near them and
 * share their anger with it, and when it is hit they turn on the attacker (it doesn't add to their numbers against
 * hoglins: vanilla counts only piglins and brutes there). Its own AI is still its goals: its brain only remembers
 * (whom it is angry at, its piglins near), with no behaviours, so it never attacks, hunts, admires, barters, dances or
 * picks anything up. A nether drummer keeps the gold truce as piglins do (a player in gold it isn't angry at is no
 * fight, and its Shock Beat spares them) and turns zombie in the overworld as they do; a rift's drummer keeps the
 * rift's rules ({@link RiftMobs}): every player is a foe, and it never turns.
 *
 * <p>It can ride a hoglin (the Piglin General's guard brings one so): the hoglin steers and charges, and the drummer only
 * drums and beats from its back, as on foot, with none of its walking goals and no walk ({@link #updateControlFlags}),
 * sitting where an adult piglin would ({@link #HOGLIN_SEAT}).
 *
 * <p>It shows the armour it wears ({@link ArmourPieces}). A nether drummer may spawn in gold armour, as a piglin does:
 * each piece at {@link #GOLD_ARMOUR_CHANCE}, enchanted as a mob's armour is. A rift's drummer comes as the rift sends it.
 */
public class WarDrummer extends AbstractPiglin implements AnimatedEntity {

	/** Defaults for {@link NetherRiftsConfig.DrummerTuning}. */
	public static final int BEAT_TICKS = 10;
	public static final int BIG_BEAT_EVERY = 4;
	public static final double BUFF_RANGE = 12;
	public static final int BUFF_TICKS = 120;
	public static final double SHOCK_RANGE = 4;
	public static final double SHOCK_STRENGTH = 1.2;
	public static final float SHOCK_DAMAGE = 4;
	public static final double NATURAL_CHANCE = 0.03;
	/** A natural spawn wears each piece of gold armour at this chance: vanilla's for a piglin. */
	public static final float GOLD_ARMOUR_CHANCE = 0.1f;
	private static final List<Item> GOLD_ARMOUR = List.of(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);

	/** It drums only with a player or a fighting piglin of its own this close: out of combat it idles and wanders. */
	public static final double COMBAT_RANGE = 16;
	/** How often it looks round for its piglins fighting, in ticks. */
	private static final int KIN_CHECK_EVERY = 5;
	/** A nether drummer spawns within this many blocks of the piglin it came with (across; and a block up or down). */
	private static final int NATURAL_SPREAD = 2;
	private static final int NATURAL_TRIES = 10;
	private static final int[] STEPS = {0, 1, -1};

	/**
	 * Memory only: what other piglins tell it (anger) and what it needs to tell them in turn (its piglins near). No
	 * behaviours, so the brain never moves it; the goals do. Nothing of its own sets the attack target (a piglin's
	 * target comes from its brain); only the Piglin General, setting his guard on a player ({@link PiglinGeneral#provoke}),
	 * which marks it as fighting to other drummers.
	 */
	private static final Brain.Provider<WarDrummer> BRAIN = Brain.provider(
			List.of(MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ANGRY_AT, MemoryModuleType.UNIVERSAL_ANGER),
			List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.PIGLIN_BRUTE_SPECIFIC_SENSOR),
			drummer -> List.of()
	);

	/** Ticks out of combat before it war-cries again as a fight starts. */
	public static final int WAR_CRY_REST = 200;
	/** Moving more than this a tick (blocks, across), it walks. */
	private static final double WALKING = 0.01;

	/** The big_beat animation's length, and the tick in it where both hands come down on the drum. */
	public static final int BIG_BEAT_TICKS = 56;
	public static final int BIG_BEAT_SLAM_TICK = 36;

	/**
	 * The beat animation is a 1 s bar ({@link #BAR} frames) of two strikes, and the Chained Ghast's idle drums the same
	 * bar four times: the right stick comes down on the drum at 0.40 s (frame 8, where its fast drop from 0.28 s ends
	 * and the stick wobbles) and the left at 0.92 s (frame 18; its drop from 0.76 s). A beat is heard on those frames.
	 */
	public static final int BAR = 20;
	public static final int RIGHT_STRIKE = 8;
	public static final int LEFT_STRIKE = 18;
	/** How loud a beat is: on the ground, up on a Chained Ghast's deck (heard farther: it's well over the fight), and there out of a fight. */
	public static final float GROUND_BEAT_VOLUME = 1.5f;
	public static final float DECK_BEAT_VOLUME = 2.5f;
	public static final float DECK_REST_VOLUME = 0.5f;

	/**
	 * Riding a hoglin it sits as low as an adult piglin (a piglin's riding offset): its own type has none, which would
	 * stand it on the hoglin's back.
	 */
	public static final Vec3 HOGLIN_SEAT = new Vec3(0, 0.7, 0);

	/** Hear every beat struck (tests); none in play. */
	public static final List<StrikeListener> STRIKE_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** A beat struck: where, and how loud. */
	@FunctionalInterface
	public interface StrikeListener {
		void struck(ServerLevel level, double x, double y, double z, float volume);
	}

	private final HeldItems.Holder<WarDrummer> holder;
	private boolean walking = false;
	private boolean drumming = false;
	private boolean inBigBeat = false;
	private double lastX, lastZ;
	private boolean fighting = false;
	/** Game time it was last in combat; a war cry needs {@link #WAR_CRY_REST} ticks out of it. */
	private long lastFought = Long.MIN_VALUE / 2;
	/** One of its piglins near is fighting; looked at every {@link #KIN_CHECK_EVERY} ticks. */
	private boolean kinFighting = false;

	public WarDrummer(EntityType<? extends WarDrummer> type, Level level) {
		super(type, level);
		this.holder = new HeldItems.Holder<>(this, RiftModels.WAR_DRUMMER);
		EntityAttachment.ofTicking(this.holder, this);
		this.holder.getAnimator().playAnimation("idle", 0, true);
		setCanPickUpLoot(false);
		// a piglin paths through doors and opens them from its brain; the drummer has no door goal, so it keeps off them
		getNavigation().setCanOpenDoors(false);
	}

	/** A natural spawn (a nether drummer) may get gold armour, as a piglin does; other spawns, a rift's among them, don't. */
	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		if (reason == EntitySpawnReason.NATURAL) {
			var random = level.getRandom();
			populateDefaultEquipmentSlots(random, difficulty);
			populateDefaultEquipmentEnchantments(level, random, difficulty);
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	/** A piglin's gold armour: each piece at {@link #GOLD_ARMOUR_CHANCE}. */
	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		for (var item : GOLD_ARMOUR) {
			if (random.nextFloat() < GOLD_ARMOUR_CHANCE) {
				var stack = new ItemStack(item);
				setItemSlot(getEquipmentSlotForItem(stack), stack);
			}
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	public HeldItems.Holder<WarDrummer> getHolder() {
		return holder;
	}

	@Override
	protected Brain<WarDrummer> makeBrain(Brain.Packed packed) {
		return BRAIN.makeBrain(this, packed);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Brain<WarDrummer> getBrain() {
		return (Brain<WarDrummer>) super.getBrain();
	}

	/** It never hunts hoglins: piglins on a hunt don't call it in. */
	@Override
	protected boolean canHunt() {
		return false;
	}

	/** Never set: its arms are its model's. */
	@Override
	public PiglinArmPose getArmPose() {
		return PiglinArmPose.DEFAULT;
	}

	@Override
	protected void playConvertedSound() {
		makeSound(SoundEvents.PIGLIN_CONVERTED_TO_ZOMBIFIED);
	}

	/** A rift's drummer never turns zombie, whatever its saved flag says (a save from before it was a piglin has none). */
	@Override
	public boolean isConverting() {
		return !RiftMobs.isRiftMob(this) && super.isConverting();
	}

	/** It never picks anything up, gold least of all: no drummer barters. */
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

	/** In a fight, so drumming (tests). */
	public boolean inFight() {
		return fighting;
	}

	/** Hit by anything but a piglin, it gets angry at the attacker and calls its piglins in, as a piglin does. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof AbstractPiglin)) {
			PiglinAiInvoker.netherRifts$maybeRetaliate(level, this, attacker);
		}
		return hurt;
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
		return SoundEvents.PIGLIN_ANGRY;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PIGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PIGLIN_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.PIGLIN_STEP, 0.15f, 1.0f);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 6.0f, 1.0, 1.2,
				target -> target instanceof Player player && !player.isCreative() && !player.isSpectator()));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	/**
	 * Riding, its walking goals (keeping away from players, strolling) and its jumps wait; it still looks about. Vanilla
	 * sets these flags every few ticks, so getting on or off takes effect within them.
	 */
	@Override
	protected void updateControlFlags() {
		super.updateControlFlags();
		if (isPassenger()) {
			goalSelector.setControlFlag(Goal.Flag.MOVE, false);
			goalSelector.setControlFlag(Goal.Flag.JUMP, false);
		}
	}

	/** It never steers what it rides: a hoglin carrying it goes its own way, after its own target. */
	@Override
	public boolean canControlVehicle() {
		return false;
	}

	@Override
	public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
		return vehicle instanceof Hoglin ? HOGLIN_SEAT : super.getVehicleAttachmentPoint(vehicle);
	}

	/**
	 * The tempo comes from the world clock, not the drummer's own count, so every drummer in a world beats on the
	 * same ticks and they all slam together. A cycle is {@code bigBeatEvery - 1} beats, then the big beat, rounded
	 * up to whole beats.
	 */
	@Override
	protected void customServerAiStep(ServerLevel level) {
		getBrain().tick(level, this);
		// turns zombie here, out of the nether (not a rift's drummer)
		super.customServerAiStep(level);
		if (isRemoved()) {
			return;
		}
		var animator = holder.getAnimator();
		// moved since the last AI step (this tick's own move comes after it); carried, it doesn't walk
		boolean moving = !isPassenger() && Mth.lengthSquared(getX() - lastX, getZ() - lastZ) > WALKING * WALKING;
		lastX = getX();
		lastZ = getZ();
		if (moving != walking) {
			walking = moving;
			if (moving) {
				animator.playAnimation("walk", 5, true);
			} else {
				animator.stopAnimation("walk");
			}
		}
		int beat = tuning().beatTicks();
		int bigBeatStart = bigBeatStart();
		int at = cycleAt(level.getGameTime());
		if (inBigBeat) {
			getNavigation().stop();
			if (at == bigBeatStart + BIG_BEAT_SLAM_TICK) {
				bigBeat(level);
			}
			if (at >= bigBeatStart && at < bigBeatStart + BIG_BEAT_TICKS) {
				return;
			}
			animator.stopAnimation("big_beat");
			inBigBeat = false;
		}
		if (!inCombat(level)) {
			fighting = false;
			stopDrumming();
			return;
		}
		if (!fighting) {
			fighting = true;
			if (level.getGameTime() - lastFought > WAR_CRY_REST) {
				warCry(level);
			}
		}
		lastFought = level.getGameTime();
		if (at == bigBeatStart) {
			// it stands its ground for the big beat: the windup is the tell
			stopDrumming();
			loop("big_beat");
			bigBeatWindupSound(level, getX(), getY(), getZ());
			inBigBeat = true;
			return;
		}
		if (at > bigBeatStart || at % beat != 0) {
			return;
		}
		if (!drumming) {
			// the loops start on a beat, so their strikes stay on the tempo; each strike is heard as the sticks land
			loop("beat");
			drumming = true;
		}
	}

	/**
	 * Plays one of the model's animations, with the drum heard where the sticks land: the beat's strikes
	 * ({@link #isStrike}), and (for a showcase drummer, whose big beat is only a look) the big beat's slam. A drummer in a
	 * fight slams on the drum clock instead ({@link #bigBeat}), its buffs with it.
	 */
	public void loop(String animation) {
		var animator = holder.getAnimator();
		switch (animation) {
			case "beat" -> animator.playAnimation("beat", 10, true, frame -> {
				if (isStrike(frame) && level() instanceof ServerLevel level) {
					beatSound(level, getX(), getY() + 1, getZ(), GROUND_BEAT_VOLUME);
				}
			}, player -> {});
			case "big_beat" -> animator.playAnimation("big_beat", 10, true, frame -> {
				if (frame == BIG_BEAT_SLAM_TICK && Showcase.isShowcase(this) && level() instanceof ServerLevel level) {
					slamSound(level, getX(), getY(), getZ());
				}
			}, player -> {});
			default -> animator.playAnimation(animation, 10, true);
		}
	}

	/** Whether a frame of the beat (or of the Chained Ghast's idle) is one where a stick lands on the drum. */
	public static boolean isStrike(int frame) {
		int inBar = Math.floorMod(frame, BAR);
		return inBar == RIGHT_STRIKE || inBar == LEFT_STRIKE;
	}

	/** Where every drummer in the world is in its drum cycle at a game time: ticks into it. */
	public static int cycleAt(long gameTime) {
		int beat = tuning().beatTicks();
		int cycle = bigBeatStart() + Mth.positiveCeilDiv(BIG_BEAT_TICKS, beat) * beat;
		return (int) Math.floorMod(gameTime, (long) cycle);
	}

	/** The tick of the cycle where the big beat starts: after {@code bigBeatEvery - 1} beats. */
	public static int bigBeatStart() {
		var tuning = tuning();
		return (tuning.bigBeatEvery() - 1) * tuning.beatTicks();
	}

	/**
	 * A beat's drum, as a stick lands: a bass drum pitched up to where it's heard (an octave down, as it
	 * was, it was all but lost), a low snare for the stick's crack, and a ravager's step for the drum's body. A client plays no
	 * sound louder than 1, so a volume over 1 only carries it farther (16 blocks a unit).
	 */
	public static void beatSound(ServerLevel level, double x, double y, double z, float volume) {
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.HOSTILE, volume, 0.8f);
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_SNARE, SoundSource.HOSTILE, volume * 0.7f, 0.6f);
		level.playSound(null, x, y, z, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, volume * 0.6f, 0.7f);
		for (var listener : STRIKE_LISTENERS) {
			listener.struck(level, x, y, z, volume);
		}
	}

	/** The big beat's windup (an angry roar) and its slam. */
	public static void bigBeatWindupSound(ServerLevel level, double x, double y, double z) {
		level.playSound(null, x, y, z, SoundEvents.PIGLIN_ANGRY, SoundSource.HOSTILE, 1.5f, 0.7f);
	}

	public static void slamSound(ServerLevel level, double x, double y, double z) {
		level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0f, 0.8f);
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.HOSTILE, 3.0f, 0.5f);
	}

	/** War Beat's buff: Speed I and Strength I for the configured time. */
	public static void warBeat(LivingEntity mob) {
		int ticks = tuning().buffTicks();
		mob.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0));
		mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0));
	}

	/** War Beat's reach, in blocks. */
	public static double buffRange() {
		return tuning().buffRange();
	}

	/**
	 * A player who can fight (not in creative or spectator, and not one it keeps the gold truce with) within
	 * {@link #COMBAT_RANGE} blocks; a fight starts only once it sees them, and goes on while they stay in range. Or one
	 * of its piglins that close with something to fight (a piglin's attack target: a player, or a hoglin it hunts).
	 */
	private boolean inCombat(ServerLevel level) {
		var player = level.getNearestPlayer(getX(), getY(), getZ(), COMBAT_RANGE,
				entity -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity) && entity instanceof Player p && isFoe(p));
		if (player != null && (fighting || hasLineOfSight(player))) {
			return true;
		}
		if (level.getGameTime() % KIN_CHECK_EVERY == 0) {
			kinFighting = !level.getEntitiesOfClass(AbstractPiglin.class, getBoundingBox().inflate(COMBAT_RANGE),
					piglin -> piglin != this && isKin(piglin) && piglin.distanceTo(this) <= COMBAT_RANGE && hasAttackTarget(piglin)).isEmpty();
		}
		return kinFighting;
	}

	/**
	 * Its own piglins, whom War Beat buffs and whose fights it drums for: a rift's drummer's are rift piglins, a nether
	 * drummer's the grown piglins of no rift (brutes, and other drummers, too).
	 */
	public boolean isKin(AbstractPiglin piglin) {
		return RiftMobs.isRiftMob(this) ? RiftMobs.isRiftMob(piglin) : !RiftMobs.isRiftMob(piglin) && piglin.isAdult();
	}

	/** A piglin's target is its brain's; one without that memory (not a vanilla brain) has none. */
	private static boolean hasAttackTarget(AbstractPiglin piglin) {
		var target = piglin.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
		return target != null && target.isPresent() && target.get().isAlive();
	}

	/**
	 * A player it fights: any, for a rift's drummer (rift mobs ignore gold); for a nether one, as for a piglin, anyone
	 * not in gold, or someone it is angry at.
	 */
	public boolean isFoe(Player player) {
		return RiftMobs.keepsRiftRules(this) || !PiglinAi.isWearingSafeArmor(player)
				|| getBrain().isMemoryValue(MemoryModuleType.ANGRY_AT, player.getUUID());
	}

	/** As a fight starts: a raid horn and an angry roar, and its war cry animation if the model has one. */
	public void warCry(ServerLevel level) {
		warCrySound(level, getX(), getY(), getZ());
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getEyeY() + 0.5, getZ(), 5, 0.4, 0.2, 0.4, 0);
		if (holder.getModel().animations().containsKey("warcry")) {
			holder.getAnimator().playAnimation("warcry", 20, true);
		}
	}

	/** A war cry's sound: a raid horn and a brute's roar. */
	public static void warCrySound(ServerLevel level, double x, double y, double z) {
		level.playSound(null, x, y, z, SoundEvents.RAID_HORN, SoundSource.HOSTILE, 2.0f, 1.0f);
		level.playSound(null, x, y, z, SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 2.0f, 0.8f);
	}

	private void stopDrumming() {
		holder.getAnimator().stopAnimation("beat");
		drumming = false;
	}

	/** The big beat's slam: War Beat (buff its piglins) and Shock Beat (hurt players and throw them back). */
	public void bigBeat(ServerLevel level) {
		var tuning = tuning();
		slamSound(level, getX(), getY(), getZ());
		for (int i = 0; i < 24; i++) {
			float angle = i * Mth.TWO_PI / 24;
			level.sendParticles(ParticleTypes.FLAME, getX() + Mth.cos(angle) * 3, getY() + 0.2, getZ() + Mth.sin(angle) * 3, 1, 0, 0, 0, 0);
		}
		double buffRange = tuning.buffRange();
		for (var piglin : level.getEntitiesOfClass(AbstractPiglin.class, getBoundingBox().inflate(buffRange),
				p -> isKin(p) && p.distanceTo(this) <= buffRange)) {
			warBeat(piglin);
		}
		double shockRange = tuning.shockRange();
		for (var player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(shockRange),
				p -> !p.isSpectator() && !p.isCreative() && isFoe(p) && p.distanceTo(this) <= shockRange)) {
			var source = level.damageSources().mobAttack(this);
			player.hurtServer(level, source, tuning.shockDamage());
			// knockback() pushes away from the (x, z) it is given; a player's own client only hears of it when
			// syncVelocity is set (the server keeps no say over a player's motion otherwise)
			player.knockback(tuning.shockStrength(), getX() - player.getX(), getZ() - player.getZ(), source, 0f);
			player.syncVelocity = true;
		}
	}

	/**
	 * After a piglin's {@code finalizeSpawn}: a grown piglin (not a rift's, nor the Bombardier) spawning naturally in the
	 * nether (vanilla's natural spawns there are all {@link EntitySpawnReason#NATURAL}; chunk generation spawns only
	 * animals) brings a drummer along at the config's {@code natural_chance}, beside it where a piglin could spawn.
	 * Returns the drummer, or null (no roll, a miss, or no room).
	 */
	@Nullable
	public static WarDrummer joinNaturalSpawn(ServerLevelAccessor level, Piglin piglin, EntitySpawnReason reason) {
		if (reason != EntitySpawnReason.NATURAL || !piglin.is(EntityTypes.PIGLIN) || !piglin.isAdult() || RiftMobs.isRiftMob(piglin)
				|| level.getLevel().dimension() != Level.NETHER) {
			return null;
		}
		double chance = tuning().naturalChance();
		if (chance <= 0 || level.getRandom().nextDouble() >= chance) {
			return null;
		}
		var drummer = RiftEntities.WAR_DRUMMER.create(level.getLevel(), EntitySpawnReason.NATURAL);
		if (drummer == null) {
			return null;
		}
		var random = level.getRandom();
		var origin = piglin.blockPosition();
		for (int i = 0; i < NATURAL_TRIES; i++) {
			int dx = random.nextIntBetweenInclusive(-NATURAL_SPREAD, NATURAL_SPREAD);
			int dz = random.nextIntBetweenInclusive(-NATURAL_SPREAD, NATURAL_SPREAD);
			if (dx == 0 && dz == 0) {
				continue;
			}
			// level with the piglin, or a step up or down
			for (int dy : STEPS) {
				var pos = origin.offset(dx, dy, dz);
				if (!SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, RiftEntities.WAR_DRUMMER)) {
					continue;
				}
				drummer.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
				if (level.noCollision(drummer) && drummer.checkSpawnObstruction(level)) {
					drummer.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
					level.addFreshEntityWithPassengers(drummer);
					return drummer;
				}
			}
		}
		return null;
	}

	private static NetherRiftsConfig.DrummerTuning tuning() {
		return NetherRiftsConfig.getInstance().mobs().drummer();
	}
}
