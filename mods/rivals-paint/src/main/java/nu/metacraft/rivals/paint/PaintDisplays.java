package nu.metacraft.rivals.paint;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import nu.metacraft.rivals.Arena;
import nu.metacraft.rivals.PaintColor;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paint for faces a multiface block cannot sit on (stairs, slabs, fences, panes …): one flat quad per
 * outline-box face on the struck side, as Polymer <em>block</em> displays carrying the very paint state
 * a painted cell would carry — {@link PaintStates#connected} for the colour, the attach direction and
 * the four connection bits — so a quad is the same material as the block paint beside it, borders
 * against it, and is drawn by the same shader code (block displays go through
 * {@code Sheets.cutoutBlockItemSheet()} → {@code RenderPipelines.ITEM_CUTOUT} → the pack's
 * {@code item.vsh}/{@code item.fsh}, which carry the terrain gloss block). One holder per cell, one
 * colour and one face per cell; recolouring rebuilds the holder. In memory only, like the tally: a
 * restart drops them.
 *
 * <p>Quads are not blocks, so nothing tells them to fall: {@link #count} sweeps the cells and drops the
 * ones whose holder or surface is gone, the same way {@link PaintTally} sweeps its paint blocks. Nothing
 * tells them about a new neighbour either, which is what {@link #refreshAround} is for: {@link Painter}
 * calls it after every cell it paints and the sweep calls it for every cell it drops, so a quad — and the
 * paint block on the other side of the seam — re-borders itself within the tick.
 */
public final class PaintDisplays {
	private static final Map<ResourceKey<Level>, PaintDisplays> ALL = new HashMap<>();
	private static final Direction[] DIRECTIONS = Direction.values();
	/**
	 * How many quads one cell may hold. A complex shape (a wall post plus four arms, a pane cross) can
	 * report a dozen outline boxes, and a quad per box is a dozen block displays in one cell for every
	 * client in range; three of them, the largest on the struck side, already read as a splat.
	 */
	static final int MAX_QUADS_PER_CELL = 3;

	/**
	 * A painted cell. {@code state} is the surface's block state at paint time: the quads are cut to
	 * that shape, so a surface that changes shape under them (a stair turned, a slab filled to a double
	 * slab) leaves them wrong and they are dropped rather than moved. {@code bits} is the cell's current
	 * connection nibble, shared by every quad in it — the neighbour test is per cell, not per box.
	 * {@code owner} is who painted it last, for {@code splat.stats.blocks}; null for paint nobody can be
	 * credited for (a test, a droplet whose shooter has gone).
	 */
	private static final class Painted {
		private final PaintColor color;
		private final ElementHolder holder;
		private final List<BlockDisplayElement> quads;
		private final BlockPos surface;
		private final Direction face;
		private final BlockState state;
		private final @Nullable UUID owner;
		private int bits;

		private Painted(PaintColor color, ElementHolder holder, List<BlockDisplayElement> quads,
				BlockPos surface, Direction face, BlockState state, int bits, @Nullable UUID owner) {
			this.color = color;
			this.holder = holder;
			this.quads = quads;
			this.surface = surface;
			this.face = face;
			this.state = state;
			this.bits = bits;
			this.owner = owner;
		}
	}

	private final Map<BlockPos, Painted> cells = new HashMap<>();

	public static PaintDisplays of(ServerLevel level) {
		return ALL.computeIfAbsent(level.dimension(), key -> new PaintDisplays());
	}

	public static void clearAll() {
		ALL.values().forEach(PaintDisplays::clear);
		ALL.clear();
	}

	public int holders() {
		return cells.size();
	}

	public @Nullable PaintColor colorAt(BlockPos cell) {
		Painted painted = cells.get(cell);
		return painted == null ? null : painted.color;
	}

	/** The face the quads in {@code cell} are painted on, or null if there are none. */
	public @Nullable Direction faceAt(BlockPos cell) {
		Painted painted = cells.get(cell);
		return painted == null ? null : painted.face;
	}

	/** The connection nibble the quads in {@code cell} carry, or -1 if there are none. For tests. */
	public int bitsAt(BlockPos cell) {
		Painted painted = cells.get(cell);
		return painted == null ? -1 : painted.bits;
	}

	/** The client states the quads in {@code cell} show, empty if there are none. For tests. */
	public List<BlockState> statesAt(BlockPos cell) {
		Painted painted = cells.get(cell);
		if (painted == null) return List.of();
		List<BlockState> states = new ArrayList<>();
		for (BlockDisplayElement quad : painted.quads) states.add(quad.getBlockState());
		return states;
	}

	/**
	 * Whether any quad in {@code cell} is still holding a change nobody has been told about. A display
	 * element's setters only write into its synched data; the packet goes out when the element ticks, so
	 * a dirty element is a border that has opened on the server and not on any screen. False for a cell
	 * with no quads at all. For tests.
	 */
	public boolean dirtyAt(BlockPos cell) {
		Painted painted = cells.get(cell);
		if (painted == null) return false;
		for (BlockDisplayElement quad : painted.quads) {
			if (quad.isDirty()) return true;
		}
		return false;
	}

	/**
	 * Quads per colour, counted as faces, dropping the cells whose paint is gone. Every colour has an entry.
	 */
	public Map<PaintColor, Integer> count(ServerLevel level) {
		Map<PaintColor, Integer> counts = new EnumMap<>(PaintColor.class);
		for (PaintColor color : PaintColor.values()) counts.put(color, 0);
		List<BlockPos> dead = new ArrayList<>();
		Iterator<Map.Entry<BlockPos, Painted>> it = cells.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Painted> entry = it.next();
			Painted painted = entry.getValue();
			if (!alive(level, entry.getKey(), painted)) {
				painted.holder.destroy();
				dead.add(entry.getKey());
				it.remove();
				continue;
			}
			counts.merge(painted.color, painted.quads.size(), Integer::sum);
		}
		// Paint that has just gone leaves its neighbours bordered against nothing: close them up. After the
		// sweep, not during it, so no entry is re-bordered against a cell that is about to be dropped too.
		for (BlockPos cell : dead) refreshAround(level, cell);
		return counts;
	}

	/**
	 * Quads per <em>player</em>, counted as faces the way {@link #count} counts them — the quad half of
	 * {@link PaintTally#countByPlayer}. No sweep and no pruning: {@code count} runs first at the whistle
	 * and has already dropped whatever is dead. A cell nobody can be credited for is left out.
	 */
	public Map<UUID, Integer> countByPlayer(ServerLevel level) {
		Map<UUID, Integer> counts = new HashMap<>();
		for (Map.Entry<BlockPos, Painted> entry : cells.entrySet()) {
			Painted painted = entry.getValue();
			if (painted.owner == null || !alive(level, entry.getKey(), painted)) continue;
			counts.merge(painted.owner, painted.quads.size(), Integer::sum);
		}
		return counts;
	}

	/**
	 * Whether this cell still holds real paint. Polymer destroys every attachment in a chunk when the chunk
	 * unloads, which nulls the holder's attachment and leaves the entry scoring for quads nobody can see; a
	 * broken or replaced surface leaves the quads hanging in the air; and a block built into the cell buries
	 * them. A full face is gone too: that side takes a paint block now, not quads. The surface must also be
	 * the very same state it was painted on — block states are interned, so reference equality is the test —
	 * because a shape change (a stair turned, a slab doubled) moves the faces out from under the quads.
	 */
	private boolean alive(ServerLevel level, BlockPos cell, Painted painted) {
		if (painted.holder.getAttachment() == null) return false;
		BlockState surface = level.getBlockState(painted.surface);
		if (surface != painted.state) return false;
		if (!Painter.paintable(surface)) return false;
		if (Block.isFaceFull(surface.getCollisionShape(level, painted.surface), painted.face)) return false;
		return free(level.getBlockState(cell));
	}

	/** A cell quads may live in: empty, or a paint block put there by a full face beside it. */
	private static boolean free(BlockState cell) {
		return cell.isAir() || Painter.isPaint(cell);
	}

	/** Destroy every holder in this level. Returns how many cells were cleared. */
	public int clear() {
		int n = cells.size();
		cells.values().forEach(painted -> painted.holder.destroy());
		cells.clear();
		return n;
	}

	/**
	 * The same for the cells inside {@code box} alone, which is what {@code /rivals reset} wants when the
	 * level has arena bounds: paint a player put on their own house outside the arena is not the reset's
	 * business. Returns how many cells were cleared.
	 */
	public int clear(BoundingBox box) {
		int cleared = 0;
		Iterator<Map.Entry<BlockPos, Painted>> it = cells.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Painted> entry = it.next();
			if (!box.isInside(entry.getKey())) continue;
			entry.getValue().holder.destroy();
			it.remove();
			cleared++;
		}
		return cleared;
	}

	/**
	 * Cover the {@code face} side of every outline box of the block at {@code surface} with quads in the
	 * cell in front. Returns false when the cell already holds this colour, the cell is built up, or the
	 * shape has no boxes. A cell whose paint has died (unloaded chunk, surface gone) counts as empty and is
	 * rebuilt, so a team can always repaint its own colour.
	 */
	public boolean paint(ServerLevel level, BlockPos surface, Direction face, PaintColor color) {
		return paint(level, surface, face, color, null);
	}

	/** The same, crediting {@code owner} with the quads for {@code splat.stats.blocks}. */
	public boolean paint(ServerLevel level, BlockPos surface, Direction face, PaintColor color, @Nullable UUID owner) {
		// Same bounds rule as Painter.paintFace, and for the same reason: this is the other door into the
		// same room, and a quad outside the arena is paint outside the arena.
		if (!Arena.paintAllowed(level, surface)) return false;
		BlockPos cell = surface.relative(face).immutable();
		Painted existing = cells.get(cell);
		if (existing != null && !alive(level, cell, existing)) {
			existing.holder.destroy();
			cells.remove(cell);
			existing = null;
		}
		if (existing != null && existing.color == color) return false;
		if (!free(level.getBlockState(cell))) return false;
		BlockState state = level.getBlockState(surface);
		// A grate, a pane, a set of bars: the shapes ink falls through rather than covers. Painter.paintable
		// already refuses them on the way in, and this is the other door into the same room — a caller with a
		// surface in hand, and the branch a face that is not full takes, which is exactly what a grate is.
		if (Unpaintable.test(state)) return false;
		// The outline shape, not the collision shape: a fence's collision box is 1.5 blocks tall, and paint
		// on top of it would float half a block over the post.
		VoxelShape shape = state.getShape(level, surface);
		if (shape.isEmpty()) shape = state.getCollisionShape(level, surface);
		List<AABB> boxes = largestFaces(shape.toAabbs(), face);
		if (boxes.isEmpty()) return false;
		if (existing != null) existing.holder.destroy();
		ElementHolder holder = new ElementHolder();
		Vec3 origin = Vec3.atLowerCornerOf(cell);
		Direction attach = face.getOpposite();
		// The cell's own bits have to be known before the quads are built: the state they show carries them.
		// The cell is not in the map yet, so this sees the neighbours only, which is what it wants.
		int bits = bits(level, cell, attach, color, face);
		List<BlockDisplayElement> quads = new ArrayList<>();
		for (AABB box : boxes) {
			BlockDisplayElement quad = quad(box, surface, face, color, bits, origin);
			holder.addElement(quad);
			quads.add(quad);
		}
		ChunkAttachment.of(holder, level, origin);
		cells.put(cell, new Painted(color, holder, quads, surface.immutable(), face, state, bits, owner));
		// The neighbours gain a bit pointing back at this cell.
		refreshAround(level, cell);
		return true;
	}

	/** At most {@link #MAX_QUADS_PER_CELL} boxes, the ones showing the most of themselves on {@code face}. */
	private static List<AABB> largestFaces(List<AABB> boxes, Direction face) {
		if (boxes.size() <= MAX_QUADS_PER_CELL) return boxes;
		List<AABB> sorted = new ArrayList<>(boxes);
		sorted.sort(Comparator.comparingDouble((AABB box) -> faceArea(box, face)).reversed());
		return sorted.subList(0, MAX_QUADS_PER_CELL);
	}

	/** The area of {@code box}'s {@code face} side, with the same in-plane axes {@link #quad} uses. */
	private static double faceArea(AABB box, Direction face) {
		double w = face.getAxis() == Direction.Axis.X ? box.getZsize() : box.getXsize();
		double h = face.getAxis() == Direction.Axis.Y ? box.getZsize() : box.getYsize();
		return w * h;
	}

	/**
	 * One paint quad on the {@code face} side of {@code box} (box coordinates are local to the surface
	 * block). A block display draws the block model with the model's own origin at the element's
	 * position, so there is no rotation to work out: the paint state's attach direction already puts the
	 * model's one quad against the right side of its unit cube (at local 0 when the attach direction
	 * points negative, at local 1 when it points positive, a tenth of a texel clear of the face, exactly
	 * as vanilla's multiface models are). Placing it is therefore two numbers per axis: where the box's
	 * face plane is on the face axis, and the box's own extent on the other two.
	 */
	private static BlockDisplayElement quad(AABB box, BlockPos surface, Direction face, PaintColor color,
			int bits, Vec3 origin) {
		Direction attach = face.getOpposite();
		Direction.Axis axis = face.getAxis();
		double along = switch (face) {
			case UP -> box.maxY; case DOWN -> box.minY; case EAST -> box.maxX; case WEST -> box.minX; case SOUTH -> box.maxZ; case NORTH -> box.minZ;
		};
		double plane = along - (attach.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0);
		Vec3 corner = new Vec3(
				axis == Direction.Axis.X ? plane : box.minX,
				axis == Direction.Axis.Y ? plane : box.minY,
				axis == Direction.Axis.Z ? plane : box.minZ);
		BlockDisplayElement element = new BlockDisplayElement(PaintStates.connected(color, attach, bits));
		element.setOffset(Vec3.atLowerCornerOf(surface).add(corner).subtract(origin));
		element.setScale(new Vector3f(
				axis == Direction.Axis.X ? 1f : (float) box.getXsize(),
				axis == Direction.Axis.Y ? 1f : (float) box.getYsize(),
				axis == Direction.Axis.Z ? 1f : (float) box.getZsize()));
		return element;
	}

	/**
	 * The connection nibble for a quad cell: the same four in-plane neighbours
	 * {@link ConnectedPaintBlock#neighbourBits} reads for a paint block, plus the neighbouring cells that
	 * hold quads of this colour on this face. A quad counts as a neighbour of a block and the other way
	 * round, so a slab beside a painted floor reads as one sheet of ink.
	 */
	private int bits(BlockGetter level, BlockPos cell, Direction attach, PaintColor color, Direction face) {
		int bits = ConnectedPaintBlock.neighbourBits(level, cell, attach, color);
		Direction[] around = ConnectedPaintBlock.inPlane(attach);
		for (int i = 0; i < 4; i++) {
			Painted other = cells.get(cell.relative(around[i]));
			if (other != null && other.color == color && other.face == face) bits |= 1 << i;
		}
		return bits;
	}

	/**
	 * Recompute the bits of the quads in {@code cell} and, if they changed, show the state that carries
	 * them — and send it.
	 *
	 * <p>The send is the whole of the second half. {@code BlockDisplayElement.setBlockState} only writes
	 * into the element's synched data; the {@code ClientboundSetEntityDataPacket} goes out of
	 * {@code GenericEntityElement.tick}, which the holder calls, which the <em>attachment</em> calls only
	 * when it was built to tick — and {@link ChunkAttachment#of} passes {@code autoTick = false}
	 * ({@code ofTicking} is the other one). These holders never ticked, so every border that opened after
	 * its quad was spawned stayed on the server: a field of quads — a dirt-path arena, where no cell is
	 * ever a paint block because a path top is fifteen sixteenths high — showed every cell wearing the
	 * closed border it was born with, whatever its neighbours did afterwards. Ticking the holder here,
	 * once per cell that actually changed, is the flush; making the attachment tick instead would tick
	 * every painted cell in the level twenty times a second for a change that happens when paint lands.
	 */
	private void refresh(ServerLevel level, BlockPos cell) {
		Painted painted = cells.get(cell);
		if (painted == null) return;
		Direction attach = painted.face.getOpposite();
		int bits = bits(level, cell, attach, painted.color, painted.face);
		if (bits == painted.bits) return;
		painted.bits = bits;
		BlockState state = PaintStates.connected(painted.color, attach, bits);
		for (BlockDisplayElement quad : painted.quads) quad.setBlockState(state);
		painted.holder.tick();
	}

	/**
	 * Re-border every quad cell touching {@code cell}. Called by {@link Painter} for every cell it paints,
	 * whether that cell became a paint block or quads of its own: either way the quads next to it have a
	 * new neighbour and their border has to open towards it.
	 */
	public void refreshAround(ServerLevel level, BlockPos cell) {
		for (Direction d : DIRECTIONS) {
			BlockPos at = cell.relative(d);
			refresh(level, at);
			refreshBlock(level, at);
		}
	}

	/**
	 * The other half of the seam: a paint <em>block</em> beside a quad cell. Its bits come from
	 * {@link ConnectedPaintBlock#neighbourBits}, which counts quads — but only {@code updateShape} ever
	 * asks it to, and a display quad appearing next door is no block change, so nothing would. The state
	 * goes out to clients only ({@link Block#UPDATE_CLIENTS}): a neighbour update here would ripple
	 * through the sheet for a change that is cosmetic and already accounted for.
	 */
	private static void refreshBlock(ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		if (!(state.getBlock() instanceof ConnectedPaintBlock paint)) return;
		Direction attach = state.getValue(ConnectedPaintBlock.FACE);
		BlockState next = ConnectedPaintBlock.withBits(state,
				ConnectedPaintBlock.neighbourBits(level, at, attach, paint.color()));
		if (next != state) level.setBlock(at, next, Block.UPDATE_CLIENTS);
	}
}
