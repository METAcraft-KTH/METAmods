package se.metacraft.ovvar.clienttest;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What the client's server-pack source has done, counted by ServerPackAppliedMixin: every pack the
 * server pushes gets a serial, and a reload that succeeds records the newest serial it started
 * with. So "the reload that began after push n finished" is {@code applied() >= n}, which is what a
 * test waiting for a freshly pushed pack needs to know (a fixed wait cannot tell a slow push from
 * none at all). Test source set only.
 */
public final class ServerPackActivity {
	private ServerPackActivity() {}

	private static final AtomicLong PUSHED = new AtomicLong();
	private static volatile long reloading, applied;
	private static volatile String lastHash = "";

	public static void pushed(String hash) {
		lastHash = hash == null ? "" : hash;
		PUSHED.incrementAndGet();
	}

	public static void reloadStarted() {
		reloading = PUSHED.get();
	}

	public static void reloadSucceeded() {
		applied = Math.max(applied, reloading);
	}

	/** How many packs the server has pushed so far. */
	public static long pushes() {
		return PUSHED.get();
	}

	/** The newest push a finished reload included. */
	public static long applied() {
		return applied;
	}

	public static String lastHash() {
		return lastHash;
	}
}
