package nu.metacraft.rivals;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player in a round wears their side's ovve. Every way into a round and every respawn asks this to
 * check the legs: DATA wears the Data ovve, IT wears the IT one — or the silicon-blue one (PolymITer's
 * kiselblå) if they own one, which counts as IT and is the better-looking of the two.
 *
 * <p>Ovves are ovvar's, and ovvar is on the minigame server but not on every server this mod runs on,
 * so the work is behind {@link FabricLoader#isModLoaded}: the class that names ovvar's types
 * ({@link OvvarBridge}) is only ever loaded when ovvar is, and without it this is a no-op.
 */
public final class Ovves {
	public static final String OVVAR = "ovvar";

	private Ovves() {}

	public static boolean available() {
		return FabricLoader.getInstance().isModLoaded(OVVAR);
	}

	/**
	 * Put the side's ovve on this player's legs if it is not there already: one they own out of their
	 * inventory first, a fresh one owned by them otherwise; whatever the legs held goes back into the
	 * inventory. Returns whether anything changed.
	 */
	public static boolean dress(ServerPlayer player, PaintColor color) {
		return available() && OvvarBridge.dress(player, color);
	}
}
