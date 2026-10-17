package nu.metacraft.qol.void_anchor.rift;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.qol.Qol;

/**
 * Fades a player's screen to white and back, as METAcraft's events do: a title of one glyph from
 * the pack's {@code metacraft:flash} font, a white rectangle scaled up to cover any screen.
 */
public final class WhiteOut {

	private static final Component GLYPH = Component.literal("").withStyle(
			Style.EMPTY.withFont(new FontDescription.Resource(Qol.getID("flash"))).withColor(0xFFFFFF).withShadowColor(0)
	);

	private WhiteOut() {}

	/** In over fadeIn ticks, white for stay, out over fadeOut. */
	public static void send(ServerPlayer player, int fadeIn, int stay, int fadeOut) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		player.connection.send(new ClientboundSetTitleTextPacket(GLYPH));
	}

}
