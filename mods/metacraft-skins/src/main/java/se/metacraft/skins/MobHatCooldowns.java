package se.metacraft.skins;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Where Mob Hats are on cooldown: the chunks around each place a hat-wearing mob died, each until
 * a moment in real time ({@link MobHats#died}). Kept in memory only, so a restart clears them all.
 * Used on the server thread only.
 */
public final class MobHatCooldowns {
	/** A chunk of one dimension. */
	private record Chunk(ResourceKey<Level> dimension, int x, int z) {}

	/** Each chunk on cooldown, and until when. */
	private final Map<Chunk, Instant> until = new HashMap<>();

	/** The chunk holding block column {@code (blockX, blockZ)}. */
	private static Chunk chunkAt(ResourceKey<Level> dimension, int blockX, int blockZ) {
		return new Chunk(dimension, blockX >> 4, blockZ >> 4);
	}

	/**
	 * Puts the 3x3 chunks centred on the one holding {@code (blockX, blockZ)} on cooldown for {@code length}
	 * from {@code now}. A chunk already on cooldown for longer keeps its later end.
	 */
	public void start(ResourceKey<Level> dimension, int blockX, int blockZ, Duration length, Instant now) {
		until.values().removeIf(end -> !end.isAfter(now));
		Chunk centre = chunkAt(dimension, blockX, blockZ);
		Instant end = now.plus(length);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				until.merge(new Chunk(dimension, centre.x() + dx, centre.z() + dz), end, (a, b) -> a.isAfter(b) ? a : b);
			}
		}
	}

	/** Is the chunk holding {@code (blockX, blockZ)} on cooldown at {@code now}? */
	public boolean onCooldown(ResourceKey<Level> dimension, int blockX, int blockZ, Instant now) {
		Instant end = until.get(chunkAt(dimension, blockX, blockZ));
		return end != null && end.isAfter(now);
	}

	/** Ends every cooldown. */
	public void clear() {
		until.clear();
	}
}
