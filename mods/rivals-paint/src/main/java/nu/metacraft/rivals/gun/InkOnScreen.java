package nu.metacraft.rivals.gun;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import nu.metacraft.rivals.PaintColor;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ink on your screen: how much enemy paint is in the player's face, and the one number the post effect
 * that draws it needs.
 *
 * <p><b>The amount is the health you have lost</b>, not a meter of its own:
 * {@code 255 * (maxHealth - health) / maxHealth}, floored, and 0 at full health. So the four overlays the
 * shader picks between are quarters of your health gone — {@code ink_1} is up to a quarter lost,
 * {@code ink_2} about half, {@code ink_3} about three quarters, {@code ink_4} nearly dead — and the ink
 * is a health bar the player cannot help reading, which is what it is for. It is floored rather than
 * rounded so that losing exactly a quarter is the top of state 1 rather than the bottom of state 2.
 *
 * <p>Nothing decays and nothing is topped up: regenerating clears the ink by itself, a heal wipes it on
 * the next tick, and a respawn starts clean. {@link #hit} and {@link #standing} are still here, but only
 * to say <em>whose</em> ink it is: the colour on the glass is the team of the last enemy paint that
 * touched the player, and a player no enemy has touched has no colour and so no ink, however much health
 * they have lost to a fall.
 *
 * <p>Getting the number to the shader is the interesting half. A server-side mod cannot send a uniform
 * to a vanilla client's post effect, so it writes the number into the frame the shader reads — and it
 * has to be in the frame <em>before</em> the effect runs: 26.3's {@code GameRenderer.render} calls
 * {@code renderLevel()}, then {@code applyPostEffects()}, and only then {@code GuiRenderer.render()},
 * so nothing on the HUD (a title, the action bar) is on the target the effect samples. What is on it is
 * the held item, drawn inside {@code renderLevel} by {@code renderItemInHand}. So the number rides the
 * weapon: every paint weapon model carries a one-pixel <em>data LED</em> whose faces are the only thing
 * in the item pipelines' atlases at alpha {@link nu.metacraft.rivals.pack.InkArt#LED_ALPHA}, tinted by
 * {@code custom_model_data} colour 0 — which this class writes:
 *
 * <ul>
 * <li>red 255 and green below 16 is the signature, which nothing else in the frame is for several flat
 *     pixels in a row (the probe checks two samples a step apart);</li>
 * <li>green's low nibble is the enemy team's index, which picks the ink colour;</li>
 * <li>blue is the amount, 0..255.</li>
 * </ul>
 *
 * <p>With no ink the value is {@link #IDLE}, a dark grey that matches no part of the signature, so the
 * shader sees nothing and the pip reads as an indicator that is simply off. The published value only
 * changes when the health does, and at most every {@link #SEND_EVERY} ticks, because every change is an
 * item-slot sync to the client; {@link PaintWeapon#inventoryTick} is the one place it reaches the stacks.
 *
 * <p>The value only ever goes to the holder's own client: {@link PaintWeapon#ledForViewer} hands every
 * other viewer {@link #IDLE}, because a lit LED on someone else's gun is both a tell and a false reading —
 * the probe would find it on their third-person weapon and splatter the finder's screen.
 *
 * <p>Where the LED lands is not the model's business any more. It used to be: each weapon's element was
 * solved so that its own {@code firstperson_righthand} transform put it under the hotbar. The hand is not
 * fixed on screen, though — {@code GameRenderer.bobView} moves the hand pose by up to a tenth of the
 * screen height per walk cycle, and the sprint FOV change moves it too — so the LED left the frame every
 * other step and the ink blinked in walking rhythm. {@code item.vsh} (RIVALS_LED_PIN) now throws the
 * model's position away for LED vertices and emits a fixed eight-by-eight-pixel quad at the bottom centre
 * of the screen, under the hotbar, at every resolution and every GUI scale.
 *
 * <p>Consequences worth knowing: the pinned quad does not depend on where the hand is, so third person
 * shows ink now too — the held weapon is still drawn inside {@code renderLevel} through the same item
 * pipelines, on the player model instead of in the hand, and its LED vertices land on the same quad. With
 * an empty hand there is no LED on the frame at all and therefore no ink, however much health is missing,
 * and the ink comes back the moment a weapon is in hand again. Everybody else's weapon, and every dropped
 * one, carries {@link #IDLE}, which the fragment shader discards outright, so their pinned quad draws
 * nothing; and under an orthographic projection — the hotbar's own icons, the inventory — the vertex
 * shader clips the quad away rather than pinning it over the GUI.
 */
public final class InkOnScreen {
	/** The most ink a screen can hold; the shader's amount byte is 0..{@code MAX}. */
	public static final int MAX = 255;
	/**
	 * The fewest ticks between two changes of the published LED value for the same player. Counted down
	 * rather than compared against a clock: the value is published from a tick and the two clocks a
	 * server has — the level's game time and the server's tick count — are not the same number, so a
	 * timestamp written by one and tested against the other blocked the LED forever on any world that had
	 * been played before.
	 */
	public static final int SEND_EVERY = 2;
	/** The signature the shader looks for: red at full. */
	public static final int SIGNATURE_RED = 0xFF;
	/** The LED with nothing to say: a dark grey, which fails the red test and the green test both. */
	public static final int IDLE = 0x303030;
	/** The stack-data key holding whose meter the LED is showing, beside the tank's own keys. */
	static final String OWNER = "rivals_led_owner";

	/**
	 * One player's ink. There is no amount here: that is read off the player's health. What a meter
	 * holds is whose ink is on the glass and what the weapons were last told to carry ({@link #IDLE} for
	 * nothing), so a player with a meter is exactly a player some enemy paint has reached.
	 */
	private static final class Meter {
		private PaintColor color;
		private int written = IDLE;
		/** Ticks still to wait before the published value may change again. */
		private int hold;

		private Meter(PaintColor color) {
			this.color = color;
		}
	}

	private static final Map<UUID, Meter> METERS = new HashMap<>();

	private InkOnScreen() {}

	public static void init() {
		// A player who logs out with ink on screen is never ticked again: drop the meter rather than
		// leave a stale amount for whoever rejoins on that UUID.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> METERS.clear());
	}

	/**
	 * How much ink is on {@code player}'s screen: the share of their health they have lost, as the
	 * shader's 0..{@link #MAX} byte. Full health is 0, and the shader reads 0 as nothing to draw.
	 */
	public static int amount(Player player) {
		float max = player.getMaxHealth();
		float lost = max - player.getHealth();
		if (max <= 0.0f || lost <= 0.0f) return 0;
		// Floored: a quarter of your health gone is the top of state 1, not the bottom of state 2.
		return Math.min(MAX, (int) (MAX * lost / max));
	}

	/** Whose ink it is, or null when the screen is clean. */
	public static @Nullable PaintColor color(Player player) {
		Meter meter = METERS.get(player.getUUID());
		return meter == null ? null : meter.color;
	}

	/**
	 * A hit landed. {@code by} is the shooter's colour, which is the ink that ends up on the victim's
	 * screen; anything that is not a player takes no ink, and neither does a hit that did no damage. How
	 * much ink there is comes from the health the hit took, not from this call.
	 */
	public static void hit(Entity victim, PaintColor by, float damage) {
		if (!(victim instanceof Player player) || damage <= 0) return;
		paint(player, by);
	}

	/** A tick spent standing in {@code enemy}'s paint: the visor takes that team's colour too. */
	public static void standing(Player player, PaintColor enemy) {
		paint(player, enemy);
	}

	private static void paint(Player player, PaintColor color) {
		Meter meter = METERS.computeIfAbsent(player.getUUID(), key -> new Meter(color));
		// The newest ink is the ink you see: a DATA hit on a screen full of IT turns it red.
		meter.color = color;
	}

	/**
	 * One tick of a player's ink: a new published value if the one the weapons are carrying is out of
	 * date, and nothing at all once the health is back. Called from {@link nu.metacraft.rivals.PlayerTick}
	 * last, once the tick has had its chance to hurt or heal the player.
	 */
	public static void tick(Player player) {
		Meter meter = METERS.get(player.getUUID());
		if (meter == null) return;
		if (meter.hold > 0) meter.hold--;
		// Not in a match, not alive, not playing: no ink, and the LED goes dark.
		if (player.isSpectator() || !player.isAlive() || PaintColor.byTeam(player.getTeam()).isEmpty()) {
			clear(player);
			return;
		}
		// Healed up, regenerated, or never hurt: no lost health, no ink, and the LED goes dark.
		int amount = amount(player);
		if (amount == 0) {
			clear(player);
			return;
		}
		int value = led(meter.color, amount);
		// Every change is an item-slot sync to the client, so the value is held still for a tick or two.
		if (value == meter.written || meter.hold > 0) return;
		meter.written = value;
		meter.hold = SEND_EVERY;
	}

	/**
	 * The value the player's weapons should be carrying: the published value, or {@link #IDLE} when there
	 * is nothing to say. {@link PaintWeapon#inventoryTick} puts it on the stacks, in the same place and
	 * the same way it keeps the tank's dye up to date — one writer, so a weapon that was stowed while the
	 * screen was full cannot come back out still carrying a live number.
	 */
	public static int ledFor(Player player) {
		Meter meter = METERS.get(player.getUUID());
		return meter == null ? IDLE : meter.written;
	}

	/**
	 * The LED's colour for an amount: red at full as the signature, green the enemy team's index, blue the
	 * amount. The item shader hands this straight to the frame, unlit and unmodulated, so what the probe
	 * reads back is this value byte for byte.
	 */
	public static int led(PaintColor color, int amount) {
		return SIGNATURE_RED << 16 | (color.ordinal() & 0xF) << 8 | Math.max(0, Math.min(MAX, amount));
	}

	/** Put {@code value} on {@code stack} if it is not already there. Returns whether anything changed. */
	public static boolean put(ItemStack stack, int value) {
		if (ledOf(stack) == value) return false;
		stack.set(DataComponents.CUSTOM_MODEL_DATA,
				new CustomModelData(List.of(), List.of(), List.of(), List.of(value)));
		return true;
	}

	/**
	 * Note whose meter a stack's LED is showing. The value itself is on the stack for the server's own
	 * bookkeeping, but only this player's client may be told it: see
	 * {@link PaintWeapon#ledForViewer}.
	 */
	public static void owner(ItemStack stack, UUID owner) {
		if (owner.equals(ownerOf(stack))) return;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(OWNER, owner.toString()));
	}

	/** Whose LED a stack is carrying, or null if nobody's. */
	public static @Nullable UUID ownerOf(ItemStack stack) {
		String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(OWNER, "");
		if (id.isEmpty()) return null;
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The value a weapon is carrying, or {@link #IDLE} when it carries none. */
	public static int ledOf(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (data == null) return IDLE;
		Integer color = data.getColor(0);
		return color == null ? IDLE : color;
	}

	/** Wipe the ink; the weapons' LEDs go dark on their next inventory tick. */
	public static void clear(Player player) {
		METERS.remove(player.getUUID());
	}

	/** Drop a player's ink without touching their weapon: a disconnect, a server stop. */
	public static void forget(Player player) {
		METERS.remove(player.getUUID());
	}

	/** For tests: no ink anywhere. */
	public static void clearAll() {
		METERS.clear();
	}
}
