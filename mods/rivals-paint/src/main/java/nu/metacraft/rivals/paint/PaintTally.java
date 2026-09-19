package nu.metacraft.rivals.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import nu.metacraft.rivals.PaintColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which cells the painter has painted, per level, and how many faces of each colour they hold now.
 *
 * <p>No deltas: {@link #count} reads every tracked cell's current state and drops cells that no longer
 * hold paint (removed by a player, by {@code updateShape} when the support went, or by reset). Vanilla
 * only calls {@code onPlace} when the block changes, so a face added to a same-colour cell would be
 * invisible to hooks; walking a few thousand positions once a second is cheap and cannot drift.
 * In memory only: a restart forgets the cells until paint is shot again, which is why the two calls that
 * take a {@link BoundingBox} — the arena bounds — sweep the world inside it instead of reading the set. The
 * blocks outlive the server; the set does not.
 */
public final class PaintTally {
	private static final Map<ResourceKey<Level>, PaintTally> TALLIES = new HashMap<>();

	private final Set<BlockPos> cells = new HashSet<>();
	/**
	 * Who was the last to paint each face of each tracked cell, keyed by the face's <em>attach</em>
	 * direction — the same index the cell's own face mask uses, so a face and its painter are the same
	 * bit. Only the last painter is kept: a face is painted over, not shared, and {@code splat.stats.blocks}
	 * is who holds the picture at the whistle rather than who has ever touched it.
	 */
	private final Map<BlockPos, Map<Direction, UUID>> owners = new HashMap<>();

	public static PaintTally of(ServerLevel level) {
		return TALLIES.computeIfAbsent(level.dimension(), key -> new PaintTally());
	}

	/** Forget every level's cells and drop every display quad (server stop). */
	public static void clearAll() {
		TALLIES.clear();
		PaintDisplays.clearAll();
	}

	public void track(BlockPos cell) {
		cells.add(cell.immutable());
	}

	public int cells() {
		return cells.size();
	}

	/**
	 * Remember who painted the {@code attach} face of this cell. A null painter — a test, a bounce droplet
	 * whose shooter has gone — forgets whoever held it instead, because a face nobody can be credited for
	 * must not stay credited to the player it was painted over from.
	 */
	public void credit(BlockPos cell, Direction attach, @Nullable UUID painter) {
		if (painter == null) {
			Map<Direction, UUID> faces = owners.get(cell);
			if (faces != null && faces.remove(attach) != null && faces.isEmpty()) owners.remove(cell);
			return;
		}
		owners.computeIfAbsent(cell.immutable(), pos -> new EnumMap<>(Direction.class)).put(attach, painter);
	}

	/** Every face of this cell forgotten: what an overpaint that wipes the cell to one colour does. */
	public void forget(BlockPos cell) {
		owners.remove(cell);
	}

	/** Who holds the {@code attach} face of this cell, if anybody. For the tests. */
	public @Nullable UUID ownerOf(BlockPos cell, Direction attach) {
		Map<Direction, UUID> faces = owners.get(cell);
		return faces == null ? null : faces.get(attach);
	}

	/**
	 * Faces per <em>player</em> over the tracked cells and the level's quads: what each player was the
	 * last to paint and still holds, which is {@code splat.stats.blocks} at the whistle. Colour does not
	 * come into it — a face is held by whoever painted it, and they painted it their own colour.
	 *
	 * <p>Prunes as {@link #count} does, and on the same terms: a cell that holds no paint is dropped, and
	 * so is a face the cell's mask no longer carries — that face was painted over, and its old painter
	 * does not hold it any more.
	 */
	public Map<UUID, Integer> countByPlayer(ServerLevel level) {
		Map<UUID, Integer> counts = new HashMap<>();
		Iterator<BlockPos> it = cells.iterator();
		while (it.hasNext()) {
			BlockPos pos = it.next();
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof Paint paint)) {
				it.remove();
				owners.remove(pos);
				continue;
			}
			Map<Direction, UUID> faces = owners.get(pos);
			if (faces == null) continue;
			int mask = paint.faceMask(state);
			faces.entrySet().removeIf(face -> (mask >> face.getKey().ordinal() & 1) == 0);
			if (faces.isEmpty()) {
				owners.remove(pos);
				continue;
			}
			for (UUID painter : faces.values()) counts.merge(painter, 1, Integer::sum);
		}
		PaintDisplays.of(level).countByPlayer(level).forEach((painter, quads) -> counts.merge(painter, quads, Integer::sum));
		return counts;
	}

	/**
	 * Faces per colour over the tracked cells plus the level's {@link PaintDisplays} quads, pruning cells
	 * that hold no paint any more. Every colour has an entry. A connected cell is one face, a multiface
	 * splat cell as many as it carries.
	 */
	public Map<PaintColor, Integer> count(ServerLevel level) {
		Map<PaintColor, Integer> counts = new EnumMap<>(PaintColor.class);
		for (PaintColor color : PaintColor.values()) counts.put(color, 0);
		Iterator<BlockPos> it = cells.iterator();
		while (it.hasNext()) {
			BlockPos pos = it.next();
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof Paint paint)) {
				it.remove();
				continue;
			}
			counts.merge(paint.color(), Integer.bitCount(paint.faceMask(state)), Integer::sum);
		}
		PaintDisplays.of(level).count(level).forEach((color, quads) -> counts.merge(color, quads, Integer::sum));
		return counts;
	}

	/**
	 * The same, but with {@code box} — the level's arena bounds — swept for paint blocks instead of trusted
	 * to the tracked cells, which is what {@code /rivals score} wants: after a restart the cells are
	 * forgotten while the blocks are still standing, and a score that reads only the tracking would call a
	 * painted arena empty. Inside the box the world is the authority; outside it the tracked cells still
	 * are, so paint a player put on their own house is not lost from the total. Tracked cells are pruned
	 * either way. Quads come from {@link PaintDisplays} as always.
	 *
	 * <p>{@link #sweep} loads chunks, so this is for commands, not for the per-second bars: {@code count(level)}
	 * is the one {@code ScoreBars} calls.
	 */
	public Map<PaintColor, Integer> count(ServerLevel level, BoundingBox box) {
		Map<PaintColor, Integer> counts = new EnumMap<>(PaintColor.class);
		for (PaintColor color : PaintColor.values()) counts.put(color, 0);
		Iterator<BlockPos> it = cells.iterator();
		while (it.hasNext()) {
			BlockPos pos = it.next();
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof Paint paint)) {
				it.remove();
				continue;
			}
			// Inside the box the sweep below finds this very cell; counting it here too would double it.
			if (box.isInside(pos)) continue;
			counts.merge(paint.color(), Integer.bitCount(paint.faceMask(state)), Integer::sum);
		}
		for (BlockPos pos : sweep(level, box)) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof Paint paint) {
				counts.merge(paint.color(), Integer.bitCount(paint.faceMask(state)), Integer::sum);
			}
		}
		PaintDisplays.of(level).count(level).forEach((color, quads) -> counts.merge(color, quads, Integer::sum));
		return counts;
	}

	/**
	 * Every paint block inside {@code box}, read out of the world rather than out of the tracking — the one
	 * thing that survives a restart, since the blocks do and the tracking does not.
	 *
	 * <p>A section at a time, not a position at a time: an arena is easily a million positions, and
	 * {@link LevelChunkSection#maybeHas} answers "no paint in this section" off the palette alone, so all
	 * but the few sections that hold paint cost one predicate pass over a handful of entries. Only the
	 * survivors are walked, and only over the part of the section the box covers.
	 *
	 * <p>This <em>loads</em> chunks: {@link ServerLevel#getChunk(int, int)} on an unloaded chunk reads it
	 * from disk (generating it if it has never been written), because paint in a chunk nobody stands in is
	 * exactly the paint a reset after a restart is there to find. Acceptable for an admin command, which is
	 * the only caller; do not put this on a tick path.
	 */
	private static List<BlockPos> sweep(ServerLevel level, BoundingBox box) {
		List<BlockPos> found = new ArrayList<>();
		int lowIndex = level.getSectionIndex(Math.max(box.minY(), level.getMinY()));
		int highIndex = level.getSectionIndex(Math.min(box.maxY(), level.getMaxY()));
		for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
				LevelChunk chunk = level.getChunk(cx, cz);
				LevelChunkSection[] sections = chunk.getSections();
				for (int index = Math.max(lowIndex, 0); index <= Math.min(highIndex, sections.length - 1); index++) {
					LevelChunkSection section = sections[index];
					if (section.hasOnlyAir() || !section.maybeHas(Painter::isPaint)) continue;
					int baseX = SectionPos.sectionToBlockCoord(cx);
					int baseY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(index));
					int baseZ = SectionPos.sectionToBlockCoord(cz);
					for (int y = Math.max(box.minY() - baseY, 0); y <= Math.min(box.maxY() - baseY, 15); y++) {
						for (int x = Math.max(box.minX() - baseX, 0); x <= Math.min(box.maxX() - baseX, 15); x++) {
							for (int z = Math.max(box.minZ() - baseZ, 0); z <= Math.min(box.maxZ() - baseZ, 15); z++) {
								if (!Painter.isPaint(section.getBlockState(x, y, z))) continue;
								found.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
							}
						}
					}
				}
			}
		}
		return found;
	}

	/** This colour's fraction of all painted faces; 0 when nothing is painted. */
	public static float share(Map<PaintColor, Integer> counts, PaintColor color) {
		int total = 0;
		for (int n : counts.values()) total += n;
		return total == 0 ? 0f : counts.getOrDefault(color, 0) / (float) total;
	}

	/**
	 * Remove every tracked paint block and every display quad from the level and forget the cells.
	 * Returns how many were removed.
	 *
	 * <p>Tracked only, and with no bounds there is nothing to sweep instead — the alternative is the whole
	 * level — so after a restart this sees only what has been painted since. {@link #reset(ServerLevel,
	 * BoundingBox)} is the honest one, and the command says as much when there are no bounds.
	 */
	public int reset(ServerLevel level) {
		int removed = 0;
		for (BlockPos pos : cells) {
			if (Painter.isPaint(level.getBlockState(pos))) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				removed++;
			}
		}
		removed += PaintDisplays.of(level).clear();
		cells.clear();
		owners.clear();
		return removed;
	}

	/**
	 * The same, inside {@code box} alone: what {@code /rivals reset} does on a level with arena bounds, so
	 * that clearing the arena between rounds leaves whatever is painted outside it where it is. Cells
	 * outside the box stay tracked — they are still paint, and the bars still count them.
	 *
	 * <p>The box itself is {@link #sweep}ed rather than the tracking read, so paint the tracking has
	 * forgotten goes too. That is the restart case, and it was the bug: the blocks outlive the server, the
	 * set of cells does not, and a reset that trusted the set reported "nothing painted" over a few
	 * thousand standing paint blocks that then had to be cleared by hand with {@code /fill}. The sweep
	 * loads any chunk inside the box that is not loaded — see {@link #sweep}; an admin command may.
	 */
	public int reset(ServerLevel level, BoundingBox box) {
		// The tracking is not the authority here, the sweep is; these cells are about to be air either way.
		cells.removeIf(box::isInside);
		owners.keySet().removeIf(box::isInside);
		int removed = 0;
		for (BlockPos pos : sweep(level, box)) {
			// Removing a cell can take its neighbours with it (a connected face loses its support), so a
			// position found by the sweep may already be air by the time its turn comes.
			if (!Painter.isPaint(level.getBlockState(pos))) continue;
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			removed++;
		}
		removed += PaintDisplays.of(level).clear(box);
		return removed;
	}
}
