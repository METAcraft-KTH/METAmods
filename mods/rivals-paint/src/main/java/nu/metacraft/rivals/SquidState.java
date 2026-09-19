package nu.metacraft.rivals;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Attribute modifiers and bookkeeping for squid form and enemy ink, applied and removed exactly once
 * per player.
 *
 * <p>Squid form is transient modifiers rather than potion effects: half size (so a squid fits where a
 * player does not), much faster, a taller step so it glides over kerbs and slabs instead of stalling
 * on them, a sneaking-speed modifier that cancels out vanilla's own sneak penalty (squid form is
 * entered by holding shift, so without this the two would multiply against each other), a big hop
 * with a floatier fall and no fall damage from it. Enemy ink takes the jump away entirely, which is what
 * makes it a trap rather than an inconvenience. The modifiers are transient, so they are never
 * written to the player's save data; the set of squids only exists to keep {@link #enter} and
 * {@link #exit} idempotent and to answer {@link #isSquid} without reading attributes back.
 *
 * <p>Squid form also hides the held items from everyone else. Vanilla invisibility hides the body but
 * not what the body is holding, so an invisible squid swimming through the ink reads to an enemy as a
 * gun floating across the floor — the one thing it must not give away. There is no server-side way to
 * hide equipment from some viewers and not others, so this lies to the trackers directly: an empty
 * {@link ClientboundSetEquipmentPacket} every squid tick to the players tracking this one (never to
 * the squid itself, which still wants to see its own gun), and the real equipment once on the way
 * out. Re-sent every tick because anything that makes vanilla's own {@code ServerEntity} resend the
 * slot — a hotbar change, a re-track — would otherwise put the gun back for good.
 */
public final class SquidState {
	public static final Identifier SCALE_ID = Rivals.id("squid/scale");
	public static final Identifier SPEED_ID = Rivals.id("squid/speed");
	public static final Identifier JUMP_ID = Rivals.id("squid/jump");
	public static final Identifier STEP_ID = Rivals.id("squid/step");
	public static final Identifier SNEAK_ID = Rivals.id("squid/sneak");
	public static final Identifier SAFE_FALL_ID = Rivals.id("squid/safe_fall");
	public static final Identifier GRAVITY_ID = Rivals.id("squid/gravity");
	public static final Identifier CLING_ID = Rivals.id("squid/cling");
	public static final Identifier NO_JUMP_ID = Rivals.id("ink/no_jump");

	private static final Set<UUID> SQUIDS = new HashSet<>();
	/** The slots squid form lies about: the hands, plus the armour a painted-up player might wear. */
	private static final EquipmentSlot[] HIDDEN = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private SquidState() {}

	public static boolean isSquid(Player player) {
		return SQUIDS.contains(player.getUUID());
	}

	/**
	 * Become a squid. The modifier helpers are themselves idempotent ({@code hasModifier}-guarded), so
	 * this always adds the UUID and always calls them: a rejoin or respawn drops the transient
	 * modifiers without touching the set, and an early return here on an already-tracked UUID would
	 * leave {@link #isSquid} true while the attributes stayed missing.
	 */
	public static void enter(Player player) {
		SQUIDS.add(player.getUUID());
		broadcast(player, hiddenEquipment(player));
		modifier(player, Attributes.SCALE, SCALE_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.8, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, 0.33, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.STEP_HEIGHT, STEP_ID, 0.5, AttributeModifier.Operation.ADD_VALUE);
		// Vanilla's sneaking penalty (default 0.3) would otherwise multiply on top of the speed boost
		// above, since squid form is entered by holding shift: +0.7 brings it back to 1.0 so sneaking
		// costs nothing while swimming.
		modifier(player, Attributes.SNEAKING_SPEED, SNEAK_ID, 0.7, AttributeModifier.Operation.ADD_VALUE);
		// The bigger hop above would otherwise hurt on the way down; a squid never takes fall damage
		// from its own jump.
		modifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, 4.0, AttributeModifier.Operation.ADD_VALUE);
		// A slightly floatier arc so the hop reads as a swim rather than a normal vanilla jump.
		modifier(player, Attributes.GRAVITY, GRAVITY_ID, -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/**
	 * Back to a player. {@link #remove} is a no-op when the modifier is absent, so this is safe to call
	 * unconditionally — which {@link nu.metacraft.rivals.PlayerTick} does, every tick, for everyone who
	 * is not a squid. The equipment packet is the one thing here that is not free, so it only goes out
	 * on the tick the player actually stops being a squid.
	 */
	public static void exit(Player player) {
		if (SQUIDS.remove(player.getUUID())) broadcast(player, realEquipment(player));
		remove(player, Attributes.SCALE, SCALE_ID);
		remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
		remove(player, Attributes.JUMP_STRENGTH, JUMP_ID);
		remove(player, Attributes.STEP_HEIGHT, STEP_ID);
		remove(player, Attributes.SNEAKING_SPEED, SNEAK_ID);
		remove(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID);
		remove(player, Attributes.GRAVITY, GRAVITY_ID);
		remove(player, Attributes.GRAVITY, CLING_ID);
	}

	/** Every slot a squid shows other players: none of them, whatever it is really carrying. */
	public static List<Pair<EquipmentSlot, ItemStack>> hiddenEquipment(Player player) {
		List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>();
		for (EquipmentSlot slot : HIDDEN) slots.add(Pair.of(slot, ItemStack.EMPTY));
		return slots;
	}

	/** What the player is really carrying, for the tick they stop being a squid. */
	public static List<Pair<EquipmentSlot, ItemStack>> realEquipment(Player player) {
		List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>();
		for (EquipmentSlot slot : HIDDEN) slots.add(Pair.of(slot, player.getItemBySlot(slot).copy()));
		return slots;
	}

	/**
	 * Tell everyone tracking {@code player} — and only them, never the player — what is in these slots.
	 * 26.3 calls this {@code sendToTrackingPlayers}; the self-including variant beside it would take the
	 * squid's own gun off its own screen.
	 */
	private static void broadcast(Player player, List<Pair<EquipmentSlot, ItemStack>> slots) {
		if (!(player.level() instanceof ServerLevel level)) return;
		level.getChunkSource().sendToTrackingPlayers(player, new ClientboundSetEquipmentPacket(player.getId(), slots));
	}

	/**
	 * Hang on the wall: gravity off, by attribute rather than by a velocity packet.
	 *
	 * <p>A clinging squid used to be held up by {@code setDeltaMovement} + a velocity packet every tick.
	 * For a real player that is a lie the client has to swallow: the client owns its own motion, the
	 * server's {@code getDeltaMovement()} is whatever the last move packets implied, and a packet built
	 * from it every tick overwrote whatever the player was actually doing — including the jump impulse,
	 * which is the bug that made a jump out of a wall cling lose all its momentum. Gravity is an
	 * attribute the client honours for its own physics, so a −1.0 multiplier is a cling that needs no
	 * packet at all: the squid stops falling and keeps every bit of speed it had.
	 *
	 * <p>Stacked on top of {@link #GRAVITY_ID}'s −0.15 float, and multiplicative, so the pair is exactly
	 * zero gravity however they are ordered.
	 */
	public static void applyCling(Player player) {
		modifier(player, Attributes.GRAVITY, CLING_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Is the cling already on? The tick it goes on is the one tick a fall has to be arrested. */
	public static boolean isClinging(Player player) {
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		return gravity != null && gravity.hasModifier(CLING_ID);
	}

	public static void clearCling(Player player) {
		remove(player, Attributes.GRAVITY, CLING_ID);
	}

	/** Standing in someone else's ink: no jumping out of it. */
	public static void applyEnemyInk(Player player) {
		modifier(player, Attributes.JUMP_STRENGTH, NO_JUMP_ID, -1.0, AttributeModifier.Operation.ADD_VALUE);
	}

	public static void clearEnemyInk(Player player) {
		remove(player, Attributes.JUMP_STRENGTH, NO_JUMP_ID);
	}

	/**
	 * Server stop: forget everyone. The set is keyed by UUID and would otherwise outlive the server it
	 * was filled from — a single-process restart (a dev run, an integrated server) would start with
	 * everyone still marked a squid. The modifiers themselves are transient and die with the entities.
	 */
	public static void clearAll() {
		SQUIDS.clear();
	}

	private static void modifier(Player player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && !instance.hasModifier(id)) instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
	}

	private static void remove(Player player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) instance.removeModifier(id);
	}
}
