package metacraft.ovvar.store;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.OvvarConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The wardrobes this server knows: a cache per owner in front of the {@link WardrobeBackend},
 * filled on join (and on demand for an ovve whose owner is elsewhere), written through with a
 * compare-and-set on every change. All state here belongs to the server thread; the backend is
 * only ever touched from one store thread, and every result comes back through
 * {@code server.execute}. Nothing here decides what a failure means for the player — the callers
 * do, from the config ({@link DesignStoreConfig}).
 */
public final class Wardrobes {
	private Wardrobes() {}

	public enum Outcome {
		/** Written, and the cache holds the new wardrobe. */
		OK,
		/** The store held a newer version (another server wrote first); the cache is being refreshed. Retry after that. */
		CONFLICT,
		/** The store could not be reached; nothing was written. */
		UNREACHABLE,
		/** The owner's wardrobe has not been loaded (yet); a load has been requested. */
		NOT_LOADED,
		/** The change did not apply to the current wardrobe (spot taken, patch not in the stash); nothing written. */
		REJECTED
	}

	private record Queued(UnaryOperator<Wardrobe> change, String what) {}

	private static MinecraftServer server;
	private static WardrobeBackend backend;
	private static ExecutorService executor;
	private static final Map<UUID, Wardrobe> CACHE = new HashMap<>();
	private static final Set<UUID> LOADING = new HashSet<>();
	private static final Map<UUID, Long> FAILED_AT = new HashMap<>();
	/** Writes accepted while the store was unreachable, per owner in order; flushed by the tick. */
	private static final Map<UUID, Deque<Queued>> QUEUED = new HashMap<>();
	private static final Set<UUID> FLUSHING = new HashSet<>();
	private static long lastFailureLog;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(s -> {
			server = s;
			open(s, OvvarConfig.get().designs());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> close());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> fetch(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(Wardrobes::tick);
	}

	// ---- lifecycle

	/** Opens the backend the config names. The store thread is created here. */
	public static void open(MinecraftServer s, DesignStoreConfig config) {
		use(s, switch (config.backend()) {
			case FILE -> new FileBackend(config.fileDirectory().isEmpty()
					? s.getWorldPath(LevelResource.ROOT).resolve("ovvar").resolve("wardrobes")
					: Path.of(config.fileDirectory()));
			case JDBC -> new JdbcBackend(config.jdbc());
		});
		Ovvar.LOGGER.info("[ovvar] wardrobes: {}", backend.describe());
	}

	/** Swaps the backend (tests, {@code /ovvar store reconnect}); the cache is dropped and online players re-fetched. */
	public static void use(MinecraftServer s, WardrobeBackend newBackend) {
		close();
		server = s;
		executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "ovvar-wardrobes");
			t.setDaemon(true);
			return t;
		});
		backend = newBackend;
		if (s.getPlayerList() != null) {   // null while the server is still starting
			for (ServerPlayer player : s.getPlayerList().getPlayers()) fetch(player.getUUID());
		}
	}

	private static void close() {
		if (executor != null) executor.shutdown();
		executor = null;
		if (backend != null) backend.close();
		backend = null;
		CACHE.clear();
		LOADING.clear();
		FAILED_AT.clear();
		QUEUED.clear();
		FLUSHING.clear();
	}

	public static boolean available() {
		return backend != null && executor != null;
	}

	private static DesignStoreConfig config() {
		return OvvarConfig.get().designs();
	}

	// ---- reading

	public static boolean loaded(UUID owner) {
		return CACHE.containsKey(owner);
	}

	/** The cached wardrobe, or empty when the owner is not loaded. (A loaded owner with no row is {@link Wardrobe#NONE}.) */
	public static Optional<Wardrobe> cached(UUID owner) {
		return Optional.ofNullable(CACHE.get(owner));
	}

	/** The cached wardrobe, or {@link Wardrobe#NONE}; only meaningful once {@link #loaded}. */
	public static Wardrobe current(UUID owner) {
		return CACHE.getOrDefault(owner, Wardrobe.NONE);
	}

	/** Loads the owner's wardrobe into the cache if not there; no-op while a load is in flight or shortly after one failed. */
	public static void fetch(UUID owner) {
		if (!available() || LOADING.contains(owner)) return;
		Long failed = FAILED_AT.get(owner);
		if (failed != null && System.currentTimeMillis() - failed < config().retrySeconds() * 1000L) return;
		LOADING.add(owner);
		WardrobeBackend b = backend;
		boolean log = config().logQueries();
		executor.submit(() -> {
			try {
				Wardrobe loaded = b.load(owner).orElse(Wardrobe.NONE);
				if (log) Ovvar.LOGGER.info("[ovvar] wardrobes: loaded {} v{}", owner, loaded.version());
				server.execute(() -> {
					LOADING.remove(owner);
					FAILED_AT.remove(owner);
					// Local changes queued while unreachable stay on top of what the store has.
					Wardrobe w = loaded;
					for (Queued q : QUEUED.getOrDefault(owner, new ArrayDeque<>())) w = q.change.apply(w).withVersion(w.version());
					CACHE.put(owner, w);
				});
			} catch (IOException | RuntimeException e) {
				unreachable("loading " + owner, e);
				server.execute(() -> {
					LOADING.remove(owner);
					FAILED_AT.put(owner, System.currentTimeMillis());
				});
			}
		});
	}

	/** Forgets and reloads an owner (the reload command, and after a conflict). */
	public static void refresh(UUID owner) {
		CACHE.remove(owner);
		FAILED_AT.remove(owner);
		fetch(owner);
	}

	// ---- writing

	/**
	 * Applies {@code change} to the owner's current wardrobe and writes it, expecting the store to
	 * hold the version the cache has. {@code done} runs on the server thread with the outcome; on
	 * {@link Outcome#OK} the cache already holds the result. A change that changes nothing is OK
	 * without a write; a change that returns null is {@link Outcome#REJECTED}.
	 */
	public static void update(UUID owner, UnaryOperator<Wardrobe> change, Consumer<Outcome> done) {
		if (!available()) {
			done.accept(Outcome.UNREACHABLE);
			return;
		}
		if (!loaded(owner)) {
			fetch(owner);
			done.accept(Outcome.NOT_LOADED);
			return;
		}
		if (pending(owner)) {
			// Earlier writes are still waiting for the store; this one queues behind them (or not, the caller's call).
			done.accept(Outcome.UNREACHABLE);
			return;
		}
		Wardrobe current = current(owner);
		Wardrobe next = change.apply(current);
		if (next == null) {
			done.accept(Outcome.REJECTED);
			return;
		}
		if (next.sameContents(current)) {
			done.accept(Outcome.OK);
			return;
		}
		Wardrobe written = next.withVersion(current.version() + 1);
		WardrobeBackend b = backend;
		boolean log = config().logQueries();
		executor.submit(() -> {
			try {
				boolean ok = b.store(owner, written, current.version());
				if (log) Ovvar.LOGGER.info("[ovvar] wardrobes: store {} v{} -> {}", owner, written.version(), ok ? "ok" : "conflict");
				server.execute(() -> {
					if (ok) {
						CACHE.put(owner, written);
						done.accept(Outcome.OK);
					} else {
						refresh(owner);
						done.accept(Outcome.CONFLICT);
					}
				});
			} catch (IOException | RuntimeException e) {
				unreachable("storing " + owner, e);
				server.execute(() -> done.accept(Outcome.UNREACHABLE));
			}
		});
	}

	/**
	 * Accepts a change without the store (the "when unreachable" config options): it is applied
	 * to the cache now, so the ovve shows it, and written when the store is back. If by then it
	 * no longer applies (the spot was taken on another server), it is dropped with a log line.
	 */
	public static void queue(UUID owner, UnaryOperator<Wardrobe> change, String what) {
		Wardrobe current = current(owner);
		Wardrobe next = change.apply(current);
		if (next == null) return;
		CACHE.put(owner, next.withVersion(current.version()));
		QUEUED.computeIfAbsent(owner, k -> new ArrayDeque<>()).add(new Queued(change, what));
		Ovvar.LOGGER.warn("[ovvar] wardrobes: {} for {} queued until the store is back", what, owner);
	}

	public static boolean pending(UUID owner) {
		Deque<Queued> queue = QUEUED.get(owner);
		return queue != null && !queue.isEmpty();
	}

	public static int pendingCount() {
		return QUEUED.values().stream().mapToInt(Deque::size).sum();
	}

	// ---- retrying

	private static void tick(MinecraftServer s) {
		if (!available() || s.getTickCount() % (config().retrySeconds() * 20L) != 0) return;
		for (UUID owner : new ArrayList<>(QUEUED.keySet())) flush(owner);
	}

	/** Replays an owner's queued changes onto whatever the store now holds, one compare-and-set each. */
	private static void flush(UUID owner) {
		if (FLUSHING.contains(owner)) return;
		Deque<Queued> queue = QUEUED.get(owner);
		if (queue == null || queue.isEmpty()) return;
		List<Queued> changes = new ArrayList<>(queue);
		FLUSHING.add(owner);
		WardrobeBackend b = backend;
		executor.submit(() -> {
			int written = 0;
			try {
				Wardrobe fresh = b.load(owner).orElse(Wardrobe.NONE);
				for (Queued q : changes) {
					Wardrobe next = q.change.apply(fresh);
					if (next == null || next.sameContents(fresh)) {
						Ovvar.LOGGER.warn("[ovvar] wardrobes: queued {} for {} no longer applies, dropped", q.what, owner);
						written++;
						continue;
					}
					next = next.withVersion(fresh.version() + 1);
					if (!b.store(owner, next, fresh.version())) break;   // someone else wrote in between: next time round
					fresh = next;
					written++;
				}
				Wardrobe result = fresh;
				int n = written;
				server.execute(() -> {
					Deque<Queued> q = QUEUED.get(owner);
					for (int i = 0; i < n && q != null && !q.isEmpty(); i++) q.removeFirst();
					if (q != null && q.isEmpty()) QUEUED.remove(owner);
					Wardrobe w = result;
					if (pending(owner)) {
						for (Queued rest : QUEUED.get(owner)) {
							Wardrobe applied = rest.change.apply(w);
							if (applied != null) w = applied.withVersion(w.version());
						}
					} else {
						Ovvar.LOGGER.info("[ovvar] wardrobes: {} caught up with the store", owner);
					}
					CACHE.put(owner, w);
					FLUSHING.remove(owner);
				});
			} catch (IOException | RuntimeException e) {
				unreachable("flushing " + owner, e);
				server.execute(() -> FLUSHING.remove(owner));
			}
		});
	}

	private static void unreachable(String doing, Exception e) {
		long now = System.currentTimeMillis();
		if (now - lastFailureLog > 10_000) {
			lastFailureLog = now;
			Ovvar.LOGGER.warn("[ovvar] wardrobes: store unreachable while {}: {}", doing, e.toString());
		}
	}

	// ---- status

	public static String status() {
		if (!available()) return "wardrobes: no store open";
		return "wardrobes: " + backend.describe() + "; " + CACHE.size() + " owner(s) cached, " + LOADING.size() + " loading, "
				+ FAILED_AT.size() + " failed, " + pendingCount() + " queued write(s)";
	}
}
