package nu.metacraft.rivals.gun;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;
import nu.metacraft.rivals.gun.WeaponTuning.Param;
import nu.metacraft.rivals.paint.Painter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The roller's other half: holding right click puts the head on the ground and rolls, which is a strip
 * of paint {@code roll_width} cells wide laid down where the player walks, a trickle of ink, a little
 * more speed, and a very heavy hit on anyone the head reaches.
 *
 * <p>The head leaves a spray where it touches: crumbs at the contact point on every tick that paints,
 * with a dust pillar every fourth, which is the only thing on the screen that says the drum is on the
 * ground rather than held in front of you.
 *
 * <p>A roll only happens where the player <em>moves</em>: standing still with the button down paints
 * nothing and costs nothing, exactly as it does in Splatoon, so a roller cannot stand in a doorway
 * repainting one cell. The movement is measured from two consecutive positions rather than taken from
 * {@code getDeltaMovement()}, for the same reason {@link nu.metacraft.rivals.PlayerTick} measures it
 * that way: the server's copy of a real player's velocity is a guess reconstructed from move packets.
 *
 * <p>All of it is in memory and all of it is per player, so a disconnect or a server stop drops it;
 * the speed bonus is a transient attribute modifier, which goes the same way.
 */
public final class Roll {
	/** The roll's movement bonus, by id so it can be taken off again. */
	public static final Identifier SPEED_ID = Rivals.id("roller/speed");
	/** How far down the strip looks for a floor: a step down, a slab, a stair. */
	private static final double FLOOR_REACH = 2.0;
	/** How tall the head's hit box is, in blocks: knee to chest of whoever is standing in front. */
	private static final double HEAD_HEIGHT = 1.6;
	/** Below this much horizontal movement in a tick the player is standing, not rolling. */
	private static final double MOVING = 0.02;
	/** Crumbs thrown up at the head's contact point on a painting tick, and how far they spread and fly. */
	private static final int CRUMBS = 3;
	private static final double SPREAD = 0.25;
	private static final double SPEED = 0.03;
	/** Every this many ticks the spray gets a dust pillar too: the ink pushed ahead of the drum. */
	private static final int PILLAR_EVERY = 4;

	/** Where each rolling player was last tick, to measure with. */
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	/** How many ticks each player has rolled since their last ink, for the trickle. */
	private static final Map<UUID, Integer> ROLLED = new HashMap<>();
	/** The tick each victim was last run over, so a roll is a hit rather than a grinder. */
	private static final Map<UUID, Long> RUN_OVER = new HashMap<>();
	/** Whose tank has already been reported empty, so the refill is announced once and not every tick. */
	private static final Set<UUID> DRY = new HashSet<>();

	private Roll() {}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
	}

	/** Everything a player leaves behind. Called from the disconnect hook, beside the weapons' own. */
	public static void forget(Player player) {
		stop(player);
		RUN_OVER.remove(player.getUUID());
		DRY.remove(player.getUUID());
	}

	/** For tests: nothing rolling anywhere. */
	public static void clearAll() {
		LAST_POS.clear();
		ROLLED.clear();
		RUN_OVER.clear();
		DRY.clear();
	}

	/** Has this roller already been told its tank is empty? What stops the message repeating, and a test. */
	public static boolean isDry(Player player) {
		return DRY.contains(player.getUUID());
	}

	/** Is this player's roll bonus on? What the tests read, and what {@link #stop} undoes. */
	public static boolean isRolling(Player player) {
		AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
		return instance != null && instance.hasModifier(SPEED_ID);
	}

	/**
	 * One tick of holding the roller down. Returns whether anything was painted, which is what the tests
	 * read.
	 *
	 * <p>A roll that runs the tank dry is not kicked out of the roll: the button is still held, the head
	 * still rolls and still runs people over, and the tank tops itself up — it just paints nothing until
	 * it has something to paint with. The one thing that happens on the way down is
	 * {@link PaintWeapon#outOfInk}, once, because a roller that has silently stopped painting reads as a
	 * roller that is broken.
	 */
	public static boolean tick(ServerLevel level, Player player, ItemStack stack, PaintColor color) {
		WeaponTuning tuning = WeaponTuning.get(Weapon.ROLLER);
		speed(player, tuning.value(Param.ROLL_SPEED));
		Vec3 at = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), at);
		if (last == null) return false;
		Vec3 moved = at.subtract(last);
		// Nothing happens where the roller stands still. Splatoon's roller paints and runs people over
		// with what it is pushed over, so holding the button in a doorway must not be a wall of damage
		// that anyone who walks past is splatted by: the roll is a charge, not a hazard.
		if (moved.horizontalDistanceSqr() < MOVING * MOVING) return false;
		runOver(level, player, tuning, color);
		if (Ink.get(stack) <= 0) {
			// An empty roller still rolls and still runs people over — Splatoon's does, and a drum is a
			// drum whether there is paint in it or not — it simply paints nothing. Worth saying once,
			// because a roller that has quietly stopped painting looks like a roller that is broken; and
			// once is the point, since this is every tick of a roll.
			if (DRY.add(player.getUUID())) PaintWeapon.outOfInk(level, player, stack);
			return false;
		}
		DRY.remove(player.getUUID());
		int painted = strip(level, player, tuning, color);
		// Where the head is actually touching. Every painting tick, because a roll is continuous and a
		// spray that came and went would read as the head bouncing.
		if (painted > 0) spray(level, player, color, level.getServer().getTickCount() % PILLAR_EVERY == 0);
		// The trickle is per tick of rolling, not per cell: a roller that turns on the spot and one that
		// runs down a corridor pay the same for the same time with the head down.
		int rolled = ROLLED.merge(player.getUUID(), 1, Integer::sum);
		int every = tuning.intValue(Param.ROLL_INK_EVERY);
		if (rolled >= every) {
			ROLLED.put(player.getUUID(), 0);
			Ink.add(stack, -1);
		}
		return painted > 0;
	}

	/**
	 * Paint thrown up where the head meets the ground: {@code CRUMBS} crumbs at the contact point, and on
	 * every {@link #PILLAR_EVERY}th tick a dust pillar with them, which is the ink being pushed ahead of
	 * the drum rather than merely landing under it.
	 *
	 * <p>The contact point is the head's own: one {@code roll_reach} ahead of the feet along the flat
	 * look, on whatever floor the same downward ray {@link #strip} uses finds under it — so on a stair or
	 * a slab the spray is where the drum is, not floating at the height the player happens to be.
	 *
	 * <p>Sent through {@link Painter#burst}, which keeps crumbs off a viewer's own camera; the roller's
	 * own eyes are a couple of blocks from its head, so a roller does see its own spray. Returns how many
	 * viewers were sent it, which is what the test reads.
	 */
	public static int spray(ServerLevel level, Player player, PaintColor color, boolean pillar) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0e-6) return 0;
		Vec3 ahead = player.position().add(flat.normalize().scale(Weapon.ROLL_REACH)).add(0, 0.1, 0);
		BlockHitResult down = level.clip(new ClipContext(ahead, ahead.subtract(0, FLOOR_REACH, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		// No floor under the head — a ledge, a gap — is nothing to throw paint off.
		if (down.getType() != HitResult.Type.BLOCK) return 0;
		// A hair above the surface: crumbs spawned inside the block are swallowed by it.
		Vec3 at = down.getLocation().add(0, 0.05, 0);
		int sent = Painter.burst(level, Painter.crumbs(color), at, CRUMBS, SPREAD, SPREAD * 0.4, SPREAD, SPEED);
		if (pillar) Painter.burst(level, Painter.pillar(color), at, 1, SPREAD * 0.5, 0.0, SPREAD * 0.5, SPEED);
		return sent;
	}

	/** Put the roller away: the bonus off, the measurement forgotten. Safe to call when not rolling. */
	public static void stop(Player player) {
		LAST_POS.remove(player.getUUID());
		ROLLED.remove(player.getUUID());
		DRY.remove(player.getUUID());
		AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance != null) instance.removeModifier(SPEED_ID);
	}

	private static void speed(Player player, double bonus) {
		modifier(player, Attributes.MOVEMENT_SPEED, bonus);
	}

	/**
	 * Put the modifier on, or move it to {@code amount} if it is already on and says something else.
	 * Bailing out on {@code hasModifier} alone — which is what this did — meant a {@code /rivals tune
	 * roller roll_speed} landed on the next roll but never on the one in progress, and the number a
	 * player is holding down is exactly the one they are trying to feel while tuning it.
	 */
	private static void modifier(Player player, Holder<Attribute> attribute, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) return;
		AttributeModifier existing = instance.getModifier(SPEED_ID);
		if (existing != null && existing.amount() == amount) return;
		instance.addOrUpdateTransientModifier(
				new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	/**
	 * The strip: {@code roll_width} cells across the direction the player is facing, one step ahead of
	 * their feet, each painted on whatever floor is under it. The floor is found by a short ray straight
	 * down rather than assumed to be the block below, so a roll goes down a stair or over a slab without
	 * leaving the paint hanging in the air — the same thing the charger's trail does along its line.
	 * Returns how many cells changed.
	 */
	private static int strip(ServerLevel level, Player player, WeaponTuning tuning, PaintColor color) {
		Direction facing = player.getDirection();
		Direction across = facing.getClockWise();
		int width = tuning.intValue(Param.ROLL_WIDTH);
		int half = width / 2;
		int painted = 0;
		for (int i = -half; i <= width - half - 1; i++) {
			BlockPos cell = player.blockPosition().relative(across, i).relative(facing);
			Vec3 from = Vec3.atBottomCenterOf(cell).add(0, 0.1, 0);
			BlockHitResult down = level.clip(new ClipContext(from, from.subtract(0, FLOOR_REACH, 0),
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (down.getType() != HitResult.Type.BLOCK) continue;
			if (Painter.paintFace(level, down.getBlockPos(), down.getDirection(), color, player.getUUID())) painted++;
		}
		if (painted > 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_STEP,
					SoundSource.PLAYERS, 0.4f, 0.8f);
		}
		return painted;
	}

	/**
	 * Anyone from another team standing where the head is sweeping takes {@code roll_damage}, once per
	 * {@code roll_hit_cooldown} ticks. The window is kept per victim rather than per roller: being run
	 * over is worth most of a player's health, and two rollers meeting the same target should not be
	 * worth two of them in the same tick.
	 *
	 * <p>Only called on a tick the roller actually moved — being run over means being run over.
	 */
	private static void runOver(ServerLevel level, Player player, WeaponTuning tuning, PaintColor color) {
		float damage = tuning.floatValue(Param.ROLL_DAMAGE);
		if (damage <= 0) return;
		Vec3 look = player.getLookAngle();
		Vec3 ahead = new Vec3(look.x, 0, look.z);
		ahead = ahead.lengthSqr() < 1.0e-6 ? Vec3.ZERO : ahead.normalize().scale(Weapon.ROLL_REACH);
		Vec3 centre = player.position().add(ahead).add(0, HEAD_HEIGHT / 2, 0);
		double halfWidth = tuning.intValue(Param.ROLL_WIDTH) / 2.0;
		AABB head = AABB.ofSize(centre, halfWidth * 2, HEAD_HEIGHT, halfWidth * 2);
		long now = level.getServer().getTickCount();
		int wait = tuning.intValue(Param.ROLL_HIT_COOLDOWN);
		for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, head)) {
			if (caught == player || !PaintBall.hostile(color, caught)) continue;
			Long last = RUN_OVER.get(caught.getUUID());
			if (last != null && now - last < wait) continue;
			RUN_OVER.put(caught.getUUID(), now);
			if (PaintDamage.hurt(level, caught, level.damageSources().indirectMagic(player, player), damage)) {
				InkOnScreen.hit(caught, color, damage);
				level.playSound(null, caught.getX(), caught.getY(), caught.getZ(), SoundEvents.SLIME_BLOCK_BREAK,
						SoundSource.PLAYERS, 1.0f, 0.6f);
			}
		}
	}
}
