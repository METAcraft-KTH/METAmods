package nu.metacraft.nether_rifts;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import nu.metacraft.nether_rifts.rift.Rift;

/** What players near a rift are told. */
public final class RiftMessages {

	private RiftMessages() {}

	public static void warn(ServerLevel level, Rift rift) {
		send(level, rift, warning(rift.crying(), RiftGuide.available()));
	}

	/**
	 * The warning when a rift opens. A crying-obsidian frame is lit on purpose after the realm has steadied, so it
	 * doesn't claim the realm is unstable. {@code guide}: link the guidebook's Unstable Portals page.
	 */
	public static Component warning(boolean crying, boolean guide) {
		var text = crying
				? "The crying obsidian wakes a rift. Light it twice more to call a raid."
				: "The portal flickers but will not hold. The realm is unstable.";
		var message = Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE);
		if (guide) {
			message.append(" ").append(Component.literal("[Read more]").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
					.withClickEvent(RiftGuide.open())
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open Unstable Portals in the guidebook")))));
		}
		return message;
	}

	public static void say(ServerLevel level, Rift rift, String text) {
		send(level, rift, Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private static void send(ServerLevel level, Rift rift, Component message) {
		double range = NetherRiftsConfig.getInstance().ranges().warning();
		var centre = rift.centre();
		for (var player : level.players()) {
			if (player.position().distanceToSqr(centre) <= range * range) {
				player.sendSystemMessage(message);
			}
		}
	}
}
