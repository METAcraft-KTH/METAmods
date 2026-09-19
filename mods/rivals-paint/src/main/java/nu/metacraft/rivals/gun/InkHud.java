package nu.metacraft.rivals.gun;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.rivals.PaintColor;
import org.jspecify.annotations.Nullable;

/**
 * The ammo bar: action-bar text for every player holding a gun, refreshed every ten ticks and after
 * each shot — and every tick for a scoped charger, which is also where its charge is shown. The charge
 * is otherwise invisible: the spyglass zoom says the player is aiming and nothing says how long they
 * have been at it, so a charger is fired on a guess.
 */
public final class InkHud {
	private static final int REFRESH_TICKS = 10;
	private static final int CELLS = 10;
	/** Cells in the charge bar. Fewer than the ink bar: it is a second number on the same line. */
	private static final int CHARGE_CELLS = 6;

	private InkHud() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			boolean refresh = server.getTickCount() % REFRESH_TICKS == 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				// A charge that only updated every ten ticks would read as a bar that jumps in fifths.
				if (refresh || PaintWeapon.chargeOf(player) >= 0) show(player);
			}
		});
	}

	/**
	 * Send the bar to a player holding a gun in either hand; silent otherwise. A player on no team gets
	 * the bar too, in grey: the tank is real (it drains and refills whatever the team), and a gun that
	 * shows nothing at all reads as broken rather than as "join a team first".
	 */
	public static void show(ServerPlayer player) {
		if (player.connection == null) return;
		ItemStack gun = heldGun(player);
		if (gun == null) return;
		PaintColor color = PaintColor.byTeam(player.getTeam()).orElse(null);
		long now = player.level().getServer().getTickCount();
		player.sendSystemMessage(bar(color, Ink.get(gun), Ink.isRefilling(gun, now), PaintWeapon.isSquid(player),
				PaintWeapon.chargeOf(player)), true);
	}

	static ItemStack heldGun(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof PaintWeapon) return stack;
		}
		return null;
	}

	/** The bar with no charge on it, which is every weapon but a scoped charger. */
	public static Component bar(@Nullable PaintColor color, int ink, boolean refilling, boolean squid) {
		return bar(color, ink, refilling, squid, -1.0f);
	}

	/**
	 * The bar's text and colour; {@code color} null means no team, and the bar is grey. A {@code charge}
	 * of 0..1 adds the charge readout after the ink, in the same colour until it is full and in bold
	 * yellow once it is — the one moment the number matters, and the one the player is waiting for.
	 * Anything below 0 leaves the charge off the bar entirely.
	 */
	public static Component bar(@Nullable PaintColor color, int ink, boolean refilling, boolean squid, float charge) {
		StringBuilder text = new StringBuilder("INK ");
		if (refilling) {
			text.append("REFILLING…");
		} else {
			int filled = (int) Math.round(ink * (double) CELLS / Ink.MAX);
			text.append("█".repeat(filled)).append("░".repeat(CELLS - filled)).append(' ').append(ink).append('/').append(Ink.MAX);
		}
		if (squid) text.append("  SQUID");
		MutableComponent component = Component.literal(text.toString());
		if (charge >= 0) {
			int cells = Math.round(Math.min(1.0f, charge) * CHARGE_CELLS);
			MutableComponent bar = Component.literal("  CHARGE " + "▮".repeat(cells) + "▯".repeat(CHARGE_CELLS - cells)
					+ " " + Math.round(Math.min(1.0f, charge) * 100) + "%");
			component.append(charge >= 1.0f ? bar.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD) : bar);
		}
		if (color == null) return component.withStyle(ChatFormatting.GRAY);
		return component.withStyle(style -> style.withColor(TextColor.fromRgb(color.rgb)));
	}
}
