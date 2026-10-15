package nu.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import org.jetbrains.annotations.Nullable;

/**
 * A goggled piglin who drops magma bombs from a war ghast's deck (as a figure in the ghast's model; see
 * {@link WarGhast#dropBomb}). This is the mob it becomes when its ghast falls: it glides down and fights on foot.
 * Clients see a vanilla piglin (so it walks and swings as one), right-handed, with the artist's goggles on its head
 * ({@link RiftItems#BOMBARDIER_HELMET}) and his gloved right arm over its own, holding a golden sword (the
 * {@link RiftItems#GRENADIER_SWORD}, a golden sword on the server).
 * <p>
 * Hand Bombs: with a player 4 to 12 blocks off in sight, every 80 ticks it stops, turns to them, swings its arm and
 * lobs a magma bomb onto the ground where they stand. Through that stop clients see the gloved arm without the sword,
 * the bomb in its fist till it lets go ({@link GrenadierSword.Throw}). The ground warning goes up as it lets go, and the
 * bomb reaches neither the ground nor the head of someone standing on it sooner than the configured warning time. It
 * never changes blocks and belongs to its rift, as the deck's bombs do.
 * Up close it fights with the sword. The numbers are the defaults of the config's {@code mobs.bombardier} section.
 * <p>
 * Wherever it is, it never trades or picks things up, never turns zombie, ignores gold armour, and its kit never
 * drops (see {@link RiftMobs#keepsRiftRules}). Only a rift's Bombardier is one of its wave, with the wave's loot.
 */
public class GhastBombardier extends Piglin implements PolymerEntity {

	/** Defaults for {@link NetherRiftsConfig.BombardierTuning}. */
	public static final int THROW_EVERY = 80;
	public static final double THROW_MIN_RANGE = 4;
	public static final double THROW_RANGE = 12;

	/** A throw's stop, in ticks: it turns to the target, swings, lets go mid-swing, and stands a moment more. */
	private static final int THROW_STOP = 14;
	private static final int SWING_AT = 3;
	private static final int RELEASE_AT = 6;
	/** The shortest a thrown bomb is in the air, however short the configured warning. */
	public static final int MIN_FLIGHT = 10;
	/** Where the bomb leaves its hand: about its shoulder, half a block toward the target. */
	private static final double HAND_HEIGHT = 1.6;
	private static final double HAND_REACH = 0.5;

	/** Ticks since the last throw, up to the throw interval. */
	private int throwClock = 0;
	/** Ticks into the current throw's stop, or -1. */
	private int throwing = -1;
	@Nullable
	private LivingEntity throwAt;

	/** A throw worked out: from the hand onto the ground under the target, at this speed. */
	private record Throw(Vec3 from, Vec3 ground, Vec3 speed) {}

	public GhastBombardier(EntityType<? extends GhastBombardier> type, Level level) {
		super(type, level);
		keepKit();
	}

	/** A piglin's, with the old Bombardier's health. */
	public static AttributeSupplier.Builder createAttributes() {
		return Piglin.createAttributes()
				.add(Attributes.MAX_HEALTH, 20.0);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.PIGLIN;
	}

	/**
	 * Its kit and its rules, set again whenever something might have changed them: spawning (a summon would give it a
	 * crossbow and gold armour at random, and one in twenty mobs is left-handed) and loading (a save from before the
	 * Bombardier was a piglin has none of it, one from before the glove a plain golden sword). Right-handed: the glove
	 * is a right arm.
	 */
	private void keepKit() {
		if (!getItemBySlot(EquipmentSlot.HEAD).is(RiftItems.BOMBARDIER_HELMET)) {
			setItemSlot(EquipmentSlot.HEAD, new ItemStack(RiftItems.BOMBARDIER_HELMET));
		}
		if (!getMainHandItem().is(RiftItems.GRENADIER_SWORD)) {
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(RiftItems.GRENADIER_SWORD));
		}
		for (var slot : EquipmentSlot.values()) {
			setDropChance(slot, 0f);
		}
		setLeftHanded(false);
		setCanPickUpLoot(false);
		// synced as well as saved: a vanilla client shakes a piglin that isn't immune when it's out of the nether
		setImmuneToZombification(true);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		var data = super.finalizeSpawn(level, difficulty, reason, groupData);
		keepKit();
		return data;
	}

	/** No random gold armour over its own kit. */
	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		keepKit();
	}

	/** Always grown up. */
	@Override
	public void setBaby(boolean baby) {
		super.setBaby(false);
	}

	/**
	 * Its brain as a piglin's, and Hand Bombs. Through a throw's stop the brain rests (it would walk on at its target),
	 * so the Bombardier stands still, facing the target.
	 */
	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (throwing >= 0) {
			throwStep(level);
			return;
		}
		super.customServerAiStep(level);
		if (throwClock < tuning().throwEvery()) {
			throwClock++;
			return;
		}
		var target = throwTarget();
		if (target != null && aim(level, target) != null) {
			throwClock = 0;
			throwing = 0;
			throwAt = target;
			getNavigation().stop();
			GrenadierSword.setThrow(getMainHandItem(), GrenadierSword.Throw.BOMB);
		}
	}

	private void throwStep(ServerLevel level) {
		var target = throwAt;
		if (target == null || !target.isAlive()) {
			endThrow();
			return;
		}
		getNavigation().stop();
		face(target);
		if (throwing == SWING_AT) {
			swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		}
		if (throwing == RELEASE_AT) {
			// aimed again as it lets go: at where the target stands now
			var aim = aim(level, target);
			if (aim != null) {
				throwBomb(level, aim);
			}
			GrenadierSword.setThrow(getMainHandItem(), GrenadierSword.Throw.THROWN);
		}
		if (++throwing >= THROW_STOP) {
			endThrow();
		}
	}

	private void endThrow() {
		throwing = -1;
		throwAt = null;
		GrenadierSword.setThrow(getMainHandItem(), null);
	}

	/** The player it would throw at now: its target, in range and in sight, while it stands on the ground. */
	@Nullable
	private Player throwTarget() {
		if (!onGround() || !(getTarget() instanceof Player player) || player.isCreative() || player.isSpectator()) {
			return null;
		}
		var tuning = tuning();
		double distance = distanceTo(player);
		return distance >= tuning.throwMinRange() && distance <= tuning.throwRange() && hasLineOfSight(player) ? player : null;
	}

	private void face(LivingEntity target) {
		float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90f;
		setYRot(yaw);
		setYBodyRot(yaw);
		setYHeadRot(yaw);
		getLookControl().setLookAt(target, 30f, 30f);
	}

	/**
	 * A throw at the ground under {@code target}, flying at least {@link #flightTicks} ticks before it could hit the
	 * ground or the target standing there; null when there's no ground under it, or a wall, roof or ledge is in the way
	 * of the arc (the bomb would burst short of its warning).
	 */
	@Nullable
	private Throw aim(ServerLevel level, LivingEntity target) {
		var standing = target.position().add(0, 0.1, 0);
		var ground = BurningPatches.groundBelow(level, standing);
		if (ground.equals(standing)) {
			return null;
		}
		var toward = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ()).normalize().scale(HAND_REACH);
		var from = position().add(toward).add(0, HAND_HEIGHT, 0);
		// the warning time to the ground, and to the target's head (it may well still stand there)
		int ticks = MagmaBomb.lobTicks(from, ground, target.getBbHeight(), flightTicks(tuning().warnTicks()));
		if (ticks < 0) {
			return null;
		}
		var speed = MagmaBomb.throwSpeed(from, ground, ticks);
		var path = MagmaBomb.path(from, speed, ticks);
		// every tick's move but the last, which ends on the ground
		var at = from;
		for (int tick = 0; tick < ticks - 1; tick++) {
			var next = path.get(tick);
			var hit = level.clip(new ClipContext(at, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			if (hit.getType() != HitResult.Type.MISS) {
				return null;
			}
			at = next;
		}
		return new Throw(from, ground, speed);
	}

	/** How long a thrown bomb flies at least: the warning time, so the warning shows that long before it lands. */
	public static int flightTicks(int warnTicks) {
		return Math.max(warnTicks, MIN_FLIGHT);
	}

	/** Lets the bomb go and puts the warning up where it will land, as {@link WarGhast#dropBomb} does. */
	private void throwBomb(ServerLevel level, Throw aim) {
		var bomb = new MagmaBomb(level, this);
		bomb.setPos(aim.from().x, aim.from().y, aim.from().z);
		bomb.setDeltaMovement(aim.speed());
		var rift = RiftMobs.riftOf(this).orElse(null);
		if (rift != null) {
			bomb.setAttached(RiftMobs.RIFT, rift);
		}
		level.addFreshEntity(bomb);
		BurningPatches.warn(level, aim.ground(), rift);
		level.playSound(null, aim.from().x, aim.from().y, aim.from().z, SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0f, 0.7f);
	}

	private static NetherRiftsConfig.BombardierTuning tuning() {
		return NetherRiftsConfig.getInstance().mobs().bombardier();
	}
}
