package nu.metacraft.rivals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import nu.metacraft.rivals.paint.PaintTally;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One bossbar per colour that has been painted this session, showing that colour's share of all
 * paint across every level, following the online player list (players who leave are dropped on the
 * next refresh), refreshed once a second. Cleared on server stop, together with the tallies.
 *
 * <p><b>Only while a match is running.</b> A score bar is the round's score, so the whistle takes it off
 * every screen ({@link Match#dropBars}, {@link #clear}) and the lobby never puts one back: "DATA 100 %"
 * hanging over a lobby is the last round's result presented as news. The counting itself still happens
 * every refresh whatever the clock says, because {@code count()} is also what prunes cells whose paint
 * is gone.
 */
public final class ScoreBars {
	private static final int REFRESH_TICKS = 20;
	private static final Map<PaintColor, ServerBossEvent> BARS = new EnumMap<>(PaintColor.class);

	private ScoreBars() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_TICKS == 0) refresh(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			clear();
			BARS.clear();
			PaintTally.clearAll();
		});
	}

	/** Every score bar off every screen. The whistle's, and the server stop's. */
	public static void clear() {
		BARS.values().forEach(ServerBossEvent::removeAllPlayers);
	}

	/** Every score bar off one screen: what a disconnect mid-match gets. */
	public static void drop(ServerPlayer player) {
		BARS.values().forEach(bar -> bar.removePlayer(player));
	}

	/** Is any score bar on this player's screen? What the tests read. */
	public static boolean shows(ServerPlayer player) {
		for (ServerBossEvent bar : BARS.values()) {
			if (bar.getPlayers().contains(player)) return true;
		}
		return false;
	}

	/** The tick hook's form: whoever is online is who the bars are for. */
	static void refresh(MinecraftServer server) {
		refresh(server, server.getPlayerList().getPlayers());
	}

	/**
	 * Every level is counted, both so the bars match the whole game and because {@code count()} is the
	 * only thing that prunes cells whose paint is gone: one count per level per refresh prunes them all.
	 *
	 * <p>The viewers are handed in, the same seam {@link Match#start} has: the test server has no
	 * connected players, so a test that could only ask about the online list could not see a bar go on or
	 * come off anybody at all.
	 */
	public static void refresh(MinecraftServer server, List<ServerPlayer> viewers) {
		Map<PaintColor, Integer> counts = new EnumMap<>(PaintColor.class);
		for (PaintColor color : PaintColor.values()) counts.put(color, 0);
		for (ServerLevel level : server.getAllLevels()) {
			PaintTally.of(level).count(level).forEach((color, faces) -> counts.merge(color, faces, Integer::sum));
		}
		// Counted above whatever the state is (that is the prune); shown only to players in a match.
		if (!Match.running()) {
			clear();
			return;
		}
		Set<ServerPlayer> online = new HashSet<>(viewers);
		for (PaintColor color : PaintColor.values()) {
			int faces = counts.get(color);
			if (faces == 0 && !BARS.containsKey(color)) continue;
			ServerBossEvent bar = BARS.computeIfAbsent(color, c -> new ServerBossEvent(UUID.randomUUID(),
					Component.literal(c.displayName), c.barColor, BossEvent.BossBarOverlay.PROGRESS));
			float share = PaintTally.share(counts, color);
			bar.setName(Component.literal(color.displayName + " " + Math.round(share * 100) + " %"));
			bar.setProgress(share);
			for (ServerPlayer stale : List.copyOf(bar.getPlayers())) {
				if (!online.contains(stale)) bar.removePlayer(stale);
			}
			for (ServerPlayer player : online) {
				bar.addPlayer(player);
			}
		}
	}
}
