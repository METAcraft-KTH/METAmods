package nu.metacraft.rivals;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The squid everyone else sees. Squid form is invisibility plus a half-size hitbox, which from the
 * outside is nothing at all: an enemy swimming past reads as empty floor, and the player themselves
 * has no way to tell a teammate's swim from a teammate's absence. So while the form is on, a
 * team-coloured Pirkko — Julle's model of the IT chapter's mascot, the same figure and the same dye
 * path an item in a frame would use — rides the player's feet on a Polymer item display, lying flat on
 * the paint, head first along the way they are going, nose down into a swim and nose up out of a leap.
 *
 * <p>It used to be the thrown ball's {@code blob}: a lump of paint, stretched by speed and squashed on
 * landing. The ball still wears that. A swimming player is a character, though, and the stretch was
 * standing in for a pose it could not strike — so the stretch is cut back to a hint of one
 * ({@link #STRETCH_MAX}) and the speed goes into the pitch instead, which is what makes a dive read as
 * a dive rather than as a blob that got longer.
 *
 * <p>The figure is not sent to the squid itself: their own screen keeps the clean first-person view the
 * form is played from, and a Pirkko drawn at their own feet would sit in the camera. There is no
 * per-viewer filter on an {@link EntityAttachment}, so the holder itself refuses to start watching its
 * own player — a viewer that never starts watching is never sent an entity to begin with.
 *
 * <p>A squid that is holding still <em>in</em> its own ink shows nothing at all: lying motionless in the
 * paint is how a squid hides, and a figure sitting on the surface would give the ambush away. It comes
 * back the moment it moves, leaps or climbs — anything that would leave a wake anyway. That is also why
 * there is no idle wobble: the one time a squid is still is the one time it must not be seen.
 *
 * <p>The orientation and the pose come from the movement {@link PlayerTick} measures between ticks
 * rather than from {@code getDeltaMovement()}, for the same reason everything else in the squid loop
 * does: for a real player the server's delta is a guess, and a figure oriented by it points the wrong
 * way. A squid that has stopped keeps whichever way it was last facing, because a figure that snaps
 * back to north the moment you let go of the keys reads as a bug.
 */
public final class SquidDisplay {
	/**
	 * Julle's figure in its own model units: 24 along the z axis its head points down, 22 across
	 * including the hands, 4 tall with the underside exactly at y = 0. The item asset test holds the
	 * delivery to these, because the two numbers below are worked out from them.
	 */
	public static final float MODEL_LONG = 24f;
	public static final float MODEL_TALL = 4f;
	/**
	 * How long Pirkko is on the floor, nose to tail. At scale 1 she is {@code MODEL_LONG / 16} = 1.5
	 * blocks, which next to a half-height squid is a raft; a swimming player reads at about 0.9.
	 */
	public static final float LENGTH = 0.9f;
	/** {@link #LENGTH} over the 1.5 blocks she is at scale 1, uniform so the figure keeps its shape. */
	public static final float SCALE = 0.6f;
	/**
	 * How far above the feet the display's origin sits, so the underside lands on the floor.
	 *
	 * <p>An item's own coordinates are centred: the client renders a model's vertices at
	 * {@code element / 16} and then translates by −0.5, so Julle's {@code y = 0..4} band comes out at
	 * −0.5..−0.25 and the <em>underside</em> is a full half block below the origin however tall the
	 * figure is. The delivery's README says translation {@code [0, 0.5, 0]} for a display placed at the
	 * floor surface, and that is this half block — at scale 1. Scaled, it is {@code 0.5 * SCALE}, and a
	 * hair of {@link #CLEARANCE} on top keeps the underside off the paint it is lying on rather than
	 * z-fighting the display quad for the same pixels.
	 */
	public static final double CLEARANCE = 0.02;
	public static final double LIFT = 0.5 * SCALE + CLEARANCE;
	/** How much of a stretch along the swim a block per tick of speed is worth, and the cap. */
	private static final float STRETCH_PER_SPEED = 1.4f;
	private static final float STRETCH_MAX = 0.3f;
	/**
	 * The swimming pitch: nose down with speed, degrees per block per tick, capped. Ten degrees is a
	 * lean into the swim; more than that and a squid crossing the floor looks like it is drilling into it.
	 */
	private static final float SWIM_PITCH_PER_SPEED = 50f;
	private static final float SWIM_PITCH_MAX = 10f;
	/** Above this much measured rise per tick the squid is leaping, and below its negative, diving. */
	private static final double LEAP_SPEED = 0.2;
	/** Nose up out of a leap and down into a dive, and the little lift that sells the jump. */
	private static final float LEAP_PITCH = 35f;
	private static final double LEAP_LIFT = 0.1;
	/** Below this much measured drop per tick onto the ground, a landing is just a landing. */
	private static final double LANDING_SPEED = -0.25;
	/** The landing squash, and how many ticks it is held before the shape eases back. */
	private static final float LANDING_WIDE = 1.2f;
	private static final float LANDING_FLAT = 0.7f;
	private static final int LANDING_TICKS = 2;
	/** A tick of interpolation, so the figure glides between ticks instead of stepping. */
	private static final int INTERPOLATION = 1;
	/** Below this the measured movement has no direction worth turning to. */
	private static final double ORIENT_EPSILON = 1.0e-4;
	/** Below this much measured movement per tick, horizontal and vertical, the squid is holding still. */
	private static final double STILL_SPEED = 0.02;

	/** One Pirkko per squid, by UUID: the same bookkeeping shape {@link SquidState} keeps. */
	private static final Map<UUID, Swimmer> SWIMMERS = new HashMap<>();

	private static final class Swimmer {
		private final ElementHolder holder;
		private final ItemDisplayElement element;
		private int color;
		/** Whether the figure is currently showing; a still squid in its own ink shows nothing. */
		private boolean shown = true;
		/** The last direction worth facing, kept for the ticks the squid holds still. */
		private Vec3 facing = new Vec3(0, 0, 1);
		/** Ticks left of the landing squash. */
		private int landing = 0;

		private Swimmer(ElementHolder holder, ItemDisplayElement element, int color) {
			this.holder = holder;
			this.element = element;
			this.color = color;
		}
	}

	/** What the figure is doing this tick: how big, how pitched, and how far off the floor. */
	private record Pose(Vector3f scale, float pitch, double lift) {}

	/** A holder that will not show itself to one player: the squid wearing it. */
	private static final class OwnBlind extends ElementHolder {
		private final UUID owner;

		private OwnBlind(UUID owner) {
			this.owner = owner;
		}

		/** The rule, on its own, so a test can ask it without a connection to ask it through. */
		private boolean refuses(@Nullable UUID viewer) {
			return owner.equals(viewer);
		}

		@Override
		public boolean startWatching(ServerGamePacketListenerImpl connection) {
			if (connection.player != null && refuses(connection.player.getUUID())) return false;
			return super.startWatching(connection);
		}
	}

	private SquidDisplay() {}

	/** The holder riding {@code player}, or null when they are not showing a squid. Tests read this. */
	public static @Nullable ElementHolder holderOf(Player player) {
		Swimmer swimmer = SWIMMERS.get(player.getUUID());
		return swimmer == null ? null : swimmer.holder;
	}

	/**
	 * One tick of the figure: make it if it is missing, then point it along {@code moved} and pose it by
	 * how fast the player is going. {@code moved} is the movement measured this tick, so a zero one is a
	 * squid holding still rather than one with no information.
	 */
	public static void show(Player player, PaintColor color, Vec3 moved, boolean inOwnInk) {
		if (!(player.level() instanceof ServerLevel)) return;
		Swimmer swimmer = SWIMMERS.computeIfAbsent(player.getUUID(), uuid -> make(player, uuid, color));
		boolean hide = inOwnInk && new Vec3(moved.x, 0, moved.z).length() < STILL_SPEED
				&& Math.abs(moved.y) < STILL_SPEED;
		if (swimmer.color != color.rgb || swimmer.shown == hide) {
			swimmer.color = color.rgb;
			swimmer.shown = !hide;
			// An item display carrying nothing draws nothing, which is the whole of hiding: the element,
			// its entity and every watcher stay exactly as they are, so showing it again is one item
			// packet rather than a respawn.
			swimmer.element.setItem(hide ? ItemStack.EMPTY : pirkkoStack(color));
		}
		if (hide) return;
		Vec3 along = new Vec3(moved.x, 0, moved.z);
		if (along.lengthSqr() > ORIENT_EPSILON * ORIENT_EPSILON) swimmer.facing = along.normalize();
		Pose pose = pose(player, swimmer, moved);
		// Yaw first, then pitch in the figure's own frame: yaw(heading) · pitch(pose), so the nose dips
		// along the swim whichever way the swim is pointing.
		swimmer.element.setLeftRotation(new Quaternionf().rotationY(heading(swimmer))
				.rotateX((float) Math.toRadians(pose.pitch())));
		swimmer.element.setScale(pose.scale());
		// The leap's lift rides the display's own translation rather than the element's offset: a
		// translation is part of the transformation, so it interpolates with the rest of the pose instead
		// of teleporting the entity a tenth of a block.
		swimmer.element.setTranslation(new Vector3f(0f, (float) pose.lift(), 0f));
		swimmer.element.setInterpolationDuration(INTERPOLATION);
		swimmer.element.startInterpolationIfDirty();
	}

	/**
	 * The yaw that puts the head out in front. Julle's model has its head towards −Z, and a plain
	 * {@code atan2(x, z)} turns the model's <em>+Z</em> onto the heading — which would send Pirkko down
	 * the floor tail first. Half a turn on top of it is the fix.
	 */
	private static float heading(Swimmer swimmer) {
		return (float) (Math.atan2(swimmer.facing.x, swimmer.facing.z) + Math.PI);
	}

	/**
	 * What the figure is doing this tick. A leap pitches the nose up and lifts her a little, a fall
	 * pitches it down, a swim leans into it by speed with a hint of the old stretch along its length, and
	 * the tick she lands she pancakes flat for {@link #LANDING_TICKS} — milder than the blob's squash was,
	 * but kept, because it is what makes a hop read as having weight.
	 */
	private static Pose pose(Player player, Swimmer swimmer, Vec3 moved) {
		if (moved.y <= LANDING_SPEED && player.onGround()) swimmer.landing = LANDING_TICKS;
		if (swimmer.landing > 0) {
			swimmer.landing--;
			// Flat on the floor, so no pitch: a pancake with its nose in the air is not a landing.
			return new Pose(new Vector3f(SCALE * LANDING_WIDE, SCALE * LANDING_FLAT, SCALE * LANDING_WIDE), 0f, 0.0);
		}
		if (moved.y > LEAP_SPEED) return new Pose(new Vector3f(SCALE), LEAP_PITCH, LEAP_LIFT);
		if (moved.y < -LEAP_SPEED) return new Pose(new Vector3f(SCALE), -LEAP_PITCH, 0.0);
		float speed = (float) new Vec3(moved.x, 0, moved.z).length();
		float stretch = Math.min(STRETCH_MAX, STRETCH_PER_SPEED * speed);
		// Long along the swim (the figure's own length, which the yaw above points that way), narrow
		// across it: what it gains in length it loses in width, so the figure keeps its volume.
		return new Pose(new Vector3f(SCALE / (1 + stretch), SCALE, SCALE * (1 + stretch)),
				-Math.min(SWIM_PITCH_MAX, SWIM_PITCH_PER_SPEED * speed), 0.0);
	}

	private static Swimmer make(Player player, UUID uuid, PaintColor color) {
		ElementHolder holder = new OwnBlind(uuid);
		ItemDisplayElement element = new ItemDisplayElement(pirkkoStack(color));
		// NONE, not FIXED: the delivery's `fixed` transform pitches Pirkko −90° to stand her up in an item
		// frame, which on the floor would leave her balanced on her nose. NONE is no transform at all, so
		// the model lies flat the way it was built and the pose below is the only thing turning her.
		element.setItemDisplayContext(ItemDisplayContext.NONE);
		element.setOffset(new Vec3(0, LIFT, 0));
		element.setInterpolationDuration(INTERPOLATION);
		element.setTeleportDuration(1);
		element.setScale(new Vector3f(SCALE));
		holder.addElement(element);
		EntityAttachment.ofTicking(holder, player);
		return new Swimmer(holder, element, color.rgb);
	}

	/** Pirkko in the team colour: Julle's model on the same dye component an item in a frame would use. */
	private static ItemStack pirkkoStack(PaintColor color) {
		ItemStack stack = new ItemStack(Items.STICK);
		stack.set(DataComponents.ITEM_MODEL, Rivals.id("pirkko"));
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.rgb));
		return stack;
	}

	/**
	 * Take the figure down. Safe to call for a player who has none, which {@link PlayerTick} does every
	 * tick for everyone who is not a squid — the same shape as {@link SquidState#exit}.
	 */
	public static void hide(Player player) {
		Swimmer swimmer = SWIMMERS.remove(player.getUUID());
		if (swimmer != null) swimmer.holder.destroy();
	}

	/** Server stop: every figure goes, holders and all. The UUIDs would otherwise outlive the server. */
	public static void clearAll() {
		for (Swimmer swimmer : SWIMMERS.values()) swimmer.holder.destroy();
		SWIMMERS.clear();
	}

	/** Is {@code player}'s figure showing? False for a still squid in its own ink, and for none at all. */
	public static boolean isShown(Player player) {
		Swimmer swimmer = SWIMMERS.get(player.getUUID());
		return swimmer != null && swimmer.shown;
	}

	/**
	 * Would the figure riding {@code player} be kept from a viewer with this id? The rule
	 * {@code startWatching} applies, asked directly, because a game test has no second connection to
	 * watch through.
	 */
	public static boolean hiddenFrom(Player player, UUID viewer) {
		Swimmer swimmer = SWIMMERS.get(player.getUUID());
		return swimmer != null && swimmer.holder instanceof OwnBlind blind && blind.refuses(viewer);
	}

	/** How many figures are riding players. Tests read this. */
	public static int showing() {
		return SWIMMERS.size();
	}
}
