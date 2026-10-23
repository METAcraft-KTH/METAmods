package se.metacraft.moredyes.armor;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When each player's client reported our resource pack loaded, which is roughly when the client's
 * sprite animations start at their first frame. Armour is sent in the colour that point in the
 * loop has reached for that viewer (see {@link AnimatedArmor}), so it stays in step with the blocks
 * they see. Keyed by profile id because the pack arrives in the configuration phase, off the server
 * thread, before a player exists.
 */
public final class PackClock {
	private static final Map<UUID, Long> LOADED = new ConcurrentHashMap<>();

	private PackClock() {}

	/** The latest call wins: rejoining reloads the pack, which restarts the client's animations. */
	public static void loaded(UUID profile, long millis) {
		LOADED.put(profile, millis);
	}

	public static void forget(UUID profile) {
		LOADED.remove(profile);
	}

	/** Ticks since that client loaded the pack, by wall clock so server lag does not pull armour out of step; 0 if never seen. */
	public static double ticksSince(@Nullable UUID profile, long nowMillis) {
		Long loaded = profile == null ? null : LOADED.get(profile);
		return loaded == null ? 0 : Math.max(0, (nowMillis - loaded) / 50.0);
	}
}
