package metacraft.ovvar;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * The MOTD every server announces itself with: which server this is and whether ovvar sewing works
 * here. Set once the server is up and again whenever the config is re-read ({@code /ovvar store
 * reconnect}), so a player reads in the server list what they get before joining — a survival
 * server sews, a minigame server only shows the stash.
 */
public final class Motd {
	private Motd() {}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(Motd::apply);
	}

	/**
	 * This server's MOTD, from the config as it reads right now. The cached status a ping is answered
	 * with is thrown away with it ({@link MinecraftServer#invalidateStatus}): it is only rebuilt when
	 * it expires, so without that a ping in the first seconds would still serve
	 * {@code server.properties}' MOTD.
	 */
	public static void apply(MinecraftServer server) {
		String motd = text(OvvarConfig.get());
		server.setMotd(motd);
		server.invalidateStatus();
		Ovvar.LOGGER.info("[{}] motd: {}", Ovvar.MOD_ID, motd);
	}

	/** The MOTD this config describes. Plain text: the vanilla MOTD here carries no formatting codes. */
	public static String text(OvvarConfig config) {
		return text(config.server().name(), config.stash().minigameServer());
	}

	/** The MOTD of a server of this name in this mode. */
	public static String text(String name, boolean minigameServer) {
		String server = name.isBlank() ? ServerConfig.DEFAULT.name() : name;
		return minigameServer
				? server + " Minigame · ovve stash only, no sewing"
				: server + " Survival · ovve sewing on stands, patches are items";
	}
}
