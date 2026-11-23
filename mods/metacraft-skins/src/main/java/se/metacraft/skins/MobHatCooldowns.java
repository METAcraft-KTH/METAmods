package se.metacraft.skins;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where Mob Hats are on cooldown: the chunks around each place a hat-wearing mob spawned
 * ({@link MobHats#spawned}), each until a moment in real time. Each chunk on cooldown has an owner,
 * the mob whose spawn put it there. If that mob despawns, its chunks come off cooldown
 * ({@link MobHats#despawned}). If it dies, they stay. Cooldowns never stack: a chunk already on cooldown
 * keeps its end and its owner. Kept in memory only, so a restart clears them all. Thread-safe: most
 * spawns are on the server thread, but a mob placed with a structure as its chunk generates spawns on a
 * world-generation thread, so every method takes this object's lock.
 */
public final class MobHatCooldowns {
	/** A chunk of one dimension. */
	private record Chunk(ResourceKey<Level> dimension, int x, int z) {}

	/** A chunk's cooldown: until when, and the mob that started it. */
	private record Cooldown(Instant until, UUID owner) {}

	/** Each chunk on cooldown. */
	private final Map<Chunk, Cooldown> chunks = new HashMap<>();

	/** The chunk holding block column {@code (blockX, blockZ)}. */
	private static Chunk chunkAt(ResourceKey<Level> dimension, int blockX, int blockZ) {
		return new Chunk(dimension, blockX >> 4, blockZ >> 4);
	}

	/**
	 * Puts those of the 3x3 chunks centred on the one holding {@code (blockX, blockZ)} that aren't on
	 * cooldown already on cooldown for {@code length} from {@code now}, owned by {@code owner}. The rest
	 * keep their end and owner.
	 */
	public synchronized void start(ResourceKey<Level> dimension, int blockX, int blockZ, UUID owner, Duration length, Instant now) {
		chunks.values().removeIf(cooldown -> !cooldown.until().isAfter(now));
		Chunk centre = chunkAt(dimension, blockX, blockZ);
		Cooldown cooldown = new Cooldown(now.plus(length), owner);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				chunks.putIfAbsent(new Chunk(dimension, centre.x() + dx, centre.z() + dz), cooldown);
			}
		}
	}

	/** Takes every chunk {@code owner} put on cooldown off it. Nothing happens if it owns none. */
	public synchronized void end(UUID owner) {
		chunks.values().removeIf(cooldown -> cooldown.owner().equals(owner));
	}

	/** Is the chunk holding {@code (blockX, blockZ)} on cooldown at {@code now}? */
	public synchronized boolean onCooldown(ResourceKey<Level> dimension, int blockX, int blockZ, Instant now) {
		Cooldown cooldown = chunks.get(chunkAt(dimension, blockX, blockZ));
		return cooldown != null && cooldown.until().isAfter(now);
	}

	/** Ends every cooldown. */
	public synchronized void clear() {
		chunks.clear();
	}
}
