package nu.metacraft.rivals.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The client-state table (spec §2). A vanilla client can only be shown vanilla blockstates, and a
 * painted cell now needs to say which of its four in-plane neighbours are painted, so paint borrows
 * states from eight donor blocks. Every state it borrows has to be <em>inert</em> on a vanilla client:
 * no collision, no light, no water, and no {@code animateTick} that emits anything. A pack can
 * repaint a borrowed state; it cannot make the client stop simulating it.
 *
 * <p><b>Tripwire is deliberately not a donor, and that is why this table has the shape it has.</b>
 * Polymer's own block pool ({@code eu.pb4.polymer.blocks.api.BlockModelType.TRIPWIRE} and
 * {@code TRIPWIRE_FLAT}) hands out tripwire states, so any mod calling
 * {@code PolymerBlockResourceUtils.requestBlock} is writing {@code minecraft/blockstates/tripwire.json}
 * too. On the minigame server, where moredyes' carpets sit in that pool next to Rivals, the two
 * overrides collided and every floor cell drew nothing at all. No donor is in any
 * {@code BlockModelType} pool, which is pinned by a game test that derives Polymer's pooled blocks
 * and asserts no donor is among them. A new donor has to clear the same test.
 *
 * <p><b>Redstone wire is not a donor either, and for the other reason a donor can fail: the map.</b>
 * The pack override is the donor's whole blockstate file, so <em>every</em> block of that kind anybody
 * places in the world draws as paint — and Paint Splat Town is built with redstone in it. Floors and
 * ceilings used to take the 64 unpowered wire states, and the corner masks the stone button's 24; both
 * now come from five wooden buttons instead, chosen for being the ones nobody builds with — the stone
 * button went for the same reason the wire did. So the rule for a donor is three-sided: outside
 * Polymer's pools, inert in every state it lends, and a block the arena's builders will never place.
 *
 * <p><b>What each donor can actually lend</b>, once the states that are not inert are struck out:
 *
 * <table border="1">
 * <caption>The inert pools</caption>
 * <tr><th>donor</th><th>inert states</th><th>struck out</th></tr>
 * <tr><td>sculk vein</td><td>64</td><td>the 64 waterlogged states: a waterlogged state carries a water
 *     {@code FluidState}, so the client draws a full block of water in the cell and predicts swimming
 *     in it. Nothing a pack can reach.</td></tr>
 * <tr><td>resin clump</td><td>64</td><td>nothing; it has no {@code waterlogged}</td></tr>
 * <tr><td>pale moss carpet</td><td>81</td><td>the 81 {@code base=true} states:
 *     {@code MossyCarpetBlock.getCollisionShape} returns a real box for those, so a client would stand
 *     a notch above the paint and disagree with the server about where the player is</td></tr>
 * <tr><td>the crimson, warped, bamboo, pale oak and poplar buttons</td><td>24 each</td>
 *     <td>nothing; a button has no {@code animateTick} (a lever has one), no collision and no
 *     {@code waterlogged}, and {@code ButtonBlock.entityInside} returns on {@code isClientSide} before
 *     it reads anything, so even the wooden ones an arrow can press are inert on a client</td></tr>
 * </table>
 *
 * <p>329 inert states against 306 paint states, so the table fits with 23 button states to spare.
 * <b>Which donor state stands for which paint state is chosen by donor, not by shape.</b>
 * Rivals is played in adventure mode, so the one thing a borrowed state's outline was ever good for —
 * the targeted-block highlight, which no pack can change — never appears, and a donor's shape costs
 * nothing:
 *
 * <ol>
 * <li><b>Wall cells take the colour's own multiface donor.</b> Attach N/E/S/W is 4 × 16 = 64 states per
 *     colour, which is exactly how many inert states a multiface block has: sculk vein is DATA's,
 *     resin clump is IT's, and neither pool has a state to spare. The all-faces-false state is usable
 *     because the pack replaces the whole blockstate file, so what the client draws is our quad and not
 *     vanilla's union of face slabs (that state's <em>shape</em> is empty, which in adventure mode
 *     nobody can see).</li>
 * <li><b>Floor and ceiling cells take the five button donors</b> — crimson, warped, bamboo, pale oak,
 *     poplar, 24 states each — in that order. Attach {@link Direction#DOWN} and {@link Direction#UP},
 *     2 × 16 per colour = 64 of the 120 they lend.</li>
 * <li><b>Splat masks take the pale moss carpet, then the buttons' 56 leftovers.</b> A splat is the
 *     fallback for a cell painted on two or more faces — the join lines of an arena rather than its
 *     surfaces — and there are 114 of them: 81 carpet, 33 button, which leaves 23 unspent.</li>
 * </ol>
 *
 * <p>The allocation runs once, in a fixed order, and throws at class load rather than reuse a state,
 * run a pool dry, or hand out a state that is not inert — every state in the finished table is checked
 * against {@link #inert} one more time, so a vanilla change that makes a donor noisy is a start-up
 * failure and not a report from a server. {@link #entry()} is the reverse map.
 */
public final class PaintStates {
	/**
	 * The five buttons floors and ceilings are dealt from, in the order they are spent, and whose leftovers
	 * the corner masks take after the carpet. Deliberately the ones nobody builds with: see the class note
	 * on why neither redstone wire nor the stone button is here any more.
	 */
	private static final List<Block> FLAT_DONORS = List.of(Blocks.CRIMSON_BUTTON, Blocks.WARPED_BUTTON,
			Blocks.BAMBOO_BUTTON, Blocks.PALE_OAK_BUTTON, Blocks.POPLAR_BUTTON);

	/** Every donor block, all of them outside every Polymer block pool. */
	public static final List<Block> DONORS = Stream.concat(
			Stream.of(Blocks.SCULK_VEIN, Blocks.RESIN_CLUMP, Blocks.PALE_MOSS_CARPET),
			FLAT_DONORS.stream()).toList();
	public static final int CONNECTED_PER_COLOR = 6 * 16;
	/** Face masks with at least two faces set: every splat a cell can be, per colour. */
	public static final int SPLAT_PER_COLOR = (1 << 6) - 1 - 6;
	private static final int PER_COLOR = CONNECTED_PER_COLOR + SPLAT_PER_COLOR;
	private static final Direction[] DIRECTIONS = Direction.values();
	/** Connection-bit patterns per (colour, attach face). */
	private static final int BITS = 16;
	/** The multiface donor each colour's wall cells come from, in colour order. */
	private static final List<Block> WALL_DONORS = List.of(Blocks.SCULK_VEIN, Blocks.RESIN_CLUMP);
	/** Wall cells per colour: four attach directions of bit patterns. */
	private static final int WALL_PER_COLOR = 4 * BITS;
	/** Floor and ceiling cells per colour: what the flat pool has to cover before the splats get any. */
	private static final int FLAT_PER_COLOR = 2 * BITS;
	/** The attach faces that are not walls, in the order they are handed flat-donor states. */
	private static final List<Direction> FLAT_FACES = List.of(Direction.DOWN, Direction.UP);

	/** The table, indexed {@code colour * PER_COLOR + local} exactly as {@link #entry} decodes it. */
	private static final List<BlockState> TABLE = table();
	private static final Map<BlockState, Entry> ENTRIES = entries();

	private PaintStates() {}

	// ---------------------------------------------------------------- inertness

	/**
	 * Whether a client can be shown this state and do nothing with it: one of ours, no water, no light
	 * and no collision. {@code animateTick} is the one rule that cannot be read off a state, and since
	 * redstone wire went there is no donor that has one at all (26.3 — a lever would not qualify, and
	 * neither would a torch), so being a donor is the whole of that test.
	 */
	public static boolean inert(BlockState state) {
		if (!DONORS.contains(state.getBlock())) return false;
		if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) return false;
		if (!state.getFluidState().isEmpty()) return false;
		if (state.getLightEmission() != 0) return false;
		return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** Every inert state of a donor, in registry order. */
	private static List<BlockState> pool(Block donor) {
		List<BlockState> out = new ArrayList<>();
		for (BlockState state : donor.getStateDefinition().getPossibleStates()) {
			if (inert(state)) out.add(state);
		}
		return out;
	}

	private static Deque<BlockState> queue(Block donor) {
		return new ArrayDeque<>(pool(donor));
	}

	/** One queue over several donors' pools, spent in the order they are listed. */
	private static Deque<BlockState> queue(List<Block> donors) {
		Deque<BlockState> out = new ArrayDeque<>();
		for (Block donor : donors) out.addAll(pool(donor));
		return out;
	}

	// ---------------------------------------------------------------- the allocator

	private static List<BlockState> table() {
		PaintColor[] colors = PaintColor.values();
		require(colors.length <= WALL_DONORS.size(), colors.length + " colours but " + WALL_DONORS.size()
				+ " multiface donors to give each one its own wall cells");
		BlockState[] table = new BlockState[colors.length * PER_COLOR];
		// 1. Wall cells: the colour's own multiface donor, one state per (direction, bits).
		for (PaintColor color : colors) {
			Block donor = WALL_DONORS.get(color.ordinal());
			Deque<BlockState> walls = queue(donor);
			require(walls.size() >= WALL_PER_COLOR, donor + " lends " + walls.size() + " inert states but "
					+ color + "'s wall cells need " + WALL_PER_COLOR);
			for (Direction wall : Direction.Plane.HORIZONTAL) {
				for (int bits = 0; bits < BITS; bits++) table[index(color, wall, bits)] = walls.remove();
			}
		}
		// 2. Floors and ceilings, then 3. the splat masks. The colours are interleaved rather than
		// filled one after the other, so a pool that runs out part way through the splats runs out for
		// both teams at the same mask instead of for one of them only.
		Deque<BlockState> flats = queue(FLAT_DONORS);
		require(flats.size() >= colors.length * FLAT_PER_COLOR, "the flat donors lend " + flats.size()
				+ " states but the floors and ceilings need " + colors.length * FLAT_PER_COLOR);
		for (Direction face : FLAT_FACES) {
			for (int bits = 0; bits < BITS; bits++) {
				for (PaintColor color : colors) table[index(color, face, bits)] = flats.remove();
			}
		}
		Deque<BlockState> carpet = queue(Blocks.PALE_MOSS_CARPET);
		require(carpet.size() + flats.size() >= colors.length * SPLAT_PER_COLOR,
				"the splat masks need " + colors.length * SPLAT_PER_COLOR + " states but the carpet and what the "
						+ "buttons have left come to " + (carpet.size() + flats.size()));
		for (int mask = 0; mask < SPLAT_PER_COLOR; mask++) {
			for (PaintColor color : colors) {
				table[color.ordinal() * PER_COLOR + CONNECTED_PER_COLOR + mask] = take(color, mask, carpet, flats);
			}
		}
		List<BlockState> out = List.of(table);
		require(out.size() == Set.copyOf(out).size(), "a donor state was handed out twice");
		// Say it once more over the finished table: a vanilla change that gives a donor collision, light
		// or a particle of its own is a start-up failure, not a report from a server.
		for (int i = 0; i < out.size(); i++) {
			BlockState state = out.get(i);
			require(inert(state), "paint state " + i + " is not inert on a vanilla client: " + state);
		}
		return out;
	}

	/** The next state from the first pool with anything left. */
	@SafeVarargs
	private static BlockState take(PaintColor color, int mask, Deque<BlockState>... pools) {
		for (Deque<BlockState> pool : pools) {
			BlockState state = pool.poll();
			if (state != null) return state;
		}
		throw new IllegalStateException("[" + Rivals.MOD_ID + "] out of donor states for " + color + " splat mask " + mask);
	}

	/** Where {@code (colour, face, bits)} lives in the table. */
	private static int index(PaintColor color, Direction face, int bits) {
		return color.ordinal() * PER_COLOR + face.ordinal() * BITS + bits;
	}

	/** Fail at class load, with the mod's own prefix: a donor that ran out is a bug in the table, not a runtime condition. */
	private static void require(boolean condition, String what) {
		if (!condition) throw new IllegalStateException("[" + Rivals.MOD_ID + "] " + what);
	}

	private static Map<BlockState, Entry> entries() {
		Map<BlockState, Entry> out = new HashMap<>();
		for (int i = 0; i < TABLE.size(); i++) {
			PaintColor color = PaintColor.values()[i / PER_COLOR];
			int local = i % PER_COLOR;
			out.put(TABLE.get(i), local < CONNECTED_PER_COLOR
					? new Entry(color, DIRECTIONS[local / 16], local % 16, 1 << (local / 16))
					: new Entry(color, null, 0, splatMask(local - CONNECTED_PER_COLOR)));
		}
		return Map.copyOf(out);
	}

	/** The {@code index}-th face mask with at least two bits, counting up from 0. */
	private static int splatMask(int index) {
		for (int mask = 1; mask < 64; mask++) {
			if (Integer.bitCount(mask) < 2) continue;
			if (index-- == 0) return mask;
		}
		throw new IllegalArgumentException("no splat mask " + index);
	}

	// ---------------------------------------------------------------- the lookups

	public static BlockState connected(PaintColor color, Direction face, int bits) {
		return TABLE.get(index(color, face, bits & 15));
	}

	/** {@code faceMask} bit i = Direction i painted. One face is a connected state with no bits. */
	public static BlockState splat(PaintColor color, int faceMask) {
		int popcount = Integer.bitCount(faceMask & 63);
		if (popcount == 0) throw new IllegalArgumentException("empty face mask");
		if (popcount == 1) return connected(color, DIRECTIONS[Integer.numberOfTrailingZeros(faceMask)], 0);
		// Number the masks with ≥ 2 bits in increasing order: 0..56.
		int index = 0;
		for (int mask = 1; mask < 64; mask++) {
			if (Integer.bitCount(mask) < 2) continue;
			if (mask == (faceMask & 63)) break;
			index++;
		}
		return TABLE.get(color.ordinal() * PER_COLOR + CONNECTED_PER_COLOR + index);
	}

	/**
	 * The state every paint particle of this colour carries: a wall cell, which is always one of the
	 * multiface donors (sculk vein for DATA, resin clump for IT), with all four bits set so the sprite
	 * is that colour's all-connected tile.
	 *
	 * <p>It has to be a multiface state. The client takes a block crumb's sprite from the state's model
	 * {@code particle} texture, which the pack points at the paint tile, but it also runs the state
	 * through vanilla's {@code BlockColors} — and a redstone-wire-backed state would come out tinted
	 * dark red whatever the texture said.
	 */
	public static BlockState particles(PaintColor color) {
		return connected(color, Direction.NORTH, BITS - 1);
	}

	/** Every client state in use, for tests and the pack. */
	public static List<BlockState> all() {
		return TABLE;
	}

	/** Which server state a client state stands for, for the pack: (colour, face, bits) or (colour, mask). */
	public record Entry(PaintColor color, @Nullable Direction face, int bits, int faceMask) {}

	public static Entry entry(BlockState client) {
		Entry entry = ENTRIES.get(client);
		if (entry == null) throw new IllegalArgumentException("not a paint state: " + client);
		return entry;
	}
}
