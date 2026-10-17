package metacraft.ovvar.guide;

import eu.pb4.booklet.impl.BookletImplUtil;
import eu.pb4.booklet.impl.BookletOpenState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * "How to ovvar" is a chapter of the server's guidebook, written and illustrated in
 * metacraft-booklet ({@code resourcepacks/ovvar}, page {@code metacraft:ovvar}) and tested there.
 * {@code /ovvar guide} only opens it.
 */
public final class Guide {
	private Guide() {}

	/** The chapter's main page. */
	public static final Identifier MAIN = Identifier.fromNamespaceAndPath("metacraft", "ovvar");

	/** Opens the chapter for a player; false if there is no such page (no metacraft-booklet on this server). */
	public static boolean open(ServerPlayer player) {
		return BookletImplUtil.openPage(player, MAIN, BookletOpenState.DEFAULT);
	}
}
