package metacraft.ovvar.store;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Where wardrobes are kept. Called from the store's own thread only ({@link Wardrobes}), never
 * the server thread. Every method may throw when the store cannot be reached; the caller treats
 * that as "unreachable, try later" — it never guesses.
 */
public interface WardrobeBackend {
	/** The player's row, with the store's version; empty for a player with none. */
	Optional<Wardrobe> load(UUID owner) throws IOException;

	/**
	 * Compare-and-set: writes {@code next} (its version must be {@code expectedVersion + 1}) if the
	 * store's current version for the owner is {@code expectedVersion}, where 0 means "no row yet".
	 *
	 * @return false if the store held a different version, in which case nothing was written
	 */
	boolean store(UUID owner, Wardrobe next, long expectedVersion) throws IOException;

	/** For the status command. */
	String describe();

	default void close() {}
}
