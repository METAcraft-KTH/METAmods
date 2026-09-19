# Metacraft Rivals v4 — connected paint — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Painted regions render as continuous sheets with rounded borders: two ovve-coloured teams, per-cell connection bits carried in vanilla blockstates, edges drawn by the terrain shader.

**Architecture:** A new `ConnectedPaintBlock` (face + four in-plane connection bits) is the normal paint cell; v3's multiface `PaintBlock` stays as the fallback for cells painted on more than one face. `PaintStates` holds the fixed table from every server paint state to a client state of sculk vein, glow lichen, resin clump or tripwire. The pack ships 16 uniform textures per colour whose red low nibble carries the bits, and `terrain.fsh` recovers the bits from the texel, works out the in-plane axes from the face normal, and discards outside a rounded, wobbling box.

**Tech Stack:** Fabric + Polymer 0.17.5 (core, resource-pack), Minecraft 26.2 Mojang mappings, Java 25, Fabric game tests (`./gradlew --offline mods:metacraft-rivals:runGameTest`).

**Spec:** `docs/superpowers/specs/2026-09-12-metacraft-rivals-v4-connected-paint-design.md`

## Global Constraints

- Minecraft 26.2, Polymer 0.17.5+26.2, Java 25, Loom 1.17; module `mods/metacraft-rivals`; stays out of `dist` (`standaloneMods`).
- Gradle always `--offline`; never run `build`; tests with `./gradlew --offline mods:metacraft-rivals:runGameTest`. The reported total is our `@GameTest` count + 1 (vanilla's `always_pass`).
- Git: plain, separate commands only (no `&&`, pipes or variables on a git line); commit messages start with `rivals: `; no `Co-Authored-By` trailer; stage only files under `mods/metacraft-rivals/` and `docs/`.
- Tabs for indentation in Java and JSON; javadoc in the voice of the existing files.
- Colours: DATA `0xBD3754`, IT `0x8A57BD`; team names `data`, `it`.
- Donor order and budget (spec §2): sculk vein 63, glow lichen 63, resin clump 63, tripwire 128 — 317 client states, 306 used; no waterlogged state, no all-false multiface state.
- Bit order (spec §3/§5): `NEG_U, POS_U, NEG_V, POS_V` = bits 0..3; axes per normal: Y → (u=x, v=z); X → (u=z, v=y); Z → (u=x, v=y).
- Paint marker alpha stays 229; the shader guard `abs(tex.a - 0.898) < 0.004` stays.

---

## File structure

| file | responsibility |
|---|---|
| `PaintColor.java` | the two colours, team + bar colours; donors leave this enum |
| `paint/PaintStates.java` (new) | the server-state → client-state table; `connected(color, face, bits)`, `splat(color, faceMask)`, `all()` |
| `paint/Paint.java` (new) | interface both paint blocks implement: `color()`, `faceMask(BlockState)` |
| `paint/ConnectedPaintBlock.java` (new) | single-face paint with bits; survival; bits recomputed on neighbour change; Polymer mapping |
| `paint/PaintBlock.java` | multiface fallback; Polymer mapping via `PaintStates.splat` |
| `paint/PaintBlocks.java` | registers both blocks per colour |
| `paint/Painter.java` | `paintFace` state machine; `paintable` excludes both paint blocks |
| `paint/PaintTally.java` | counts through `Paint.faceMask` |
| `pack/PaintArt.java` (new) | 32 uniform textures, 6 quad models, 4 blockstate overrides |
| `pack/SplatArt.java` | keeps only the white splat-quad item files for `PaintDisplays` |
| `pack/RivalsPack.java` | adds both generators |
| `rivals_shaders/terrain.fsh` | bits → rounded box → discard; liquid look |
| `gametest/RivalsGameTests.java` | adapted + 7 new tests |
| `README.md`, `lang/en_us.json` | two teams, connected paint |

---

### Task 1: Two ovve colours

**Files:**
- Modify: `src/main/java/nu/metacraft/rivals/PaintColor.java`
- Modify: `src/main/resources/assets/metacraft-rivals/lang/en_us.json`
- Modify: `src/main/java/nu/metacraft/rivals/gun/PaintBall.java:97` (`PaintColor.MAGENTA` default → `PaintColor.DATA`)
- Modify: `src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java` (every `MAGENTA`→`DATA`, `LIME`→`IT`, `CYAN`→`IT` or `DATA` so each test still has two distinct colours where it needs them; team names `"magenta"`→`"data"` etc.)
- Modify: `README.md` (teams paragraph)

**Interfaces:**
- Produces: `enum PaintColor { DATA, IT }` with fields `id, displayName, rgb, donor, teamColor, barColor` — `donor` stays in this task (DATA → `Blocks.SCULK_VEIN`, IT → `Blocks.GLOW_LICHEN`) so v3's mapping keeps compiling; Task 3 removes it.

- [x] **Step 1: Change the enum**

```java
public enum PaintColor {
	/** The Data chapter's ovve, sampled from art/ovvar/data.png the way ovvar's mockups do. */
	DATA("data", "DATA", 0xBD3754, Blocks.SCULK_VEIN, TeamColor.RED, BossEvent.BossBarColor.RED),
	/** The IT chapter's ovve, from art/ovvar/it.png. */
	IT("it", "IT", 0x8A57BD, Blocks.GLOW_LICHEN, TeamColor.DARK_PURPLE, BossEvent.BossBarColor.PURPLE);
```

Update the class javadoc: two teams, ovve colours, "the id doubles as the vanilla team name". Keep `donorPath()`, `idList()`, `byId`, `byTeam`.

- [x] **Step 2: Lang**

```json
	"block.metacraft-rivals.paint_data": "DATA Paint",
	"block.metacraft-rivals.paint_it": "IT Paint",
```

(remove the magenta/lime/cyan lines.)

- [x] **Step 3: Tests and PaintBall default**

Replace every colour reference in `RivalsGameTests.java`. Rule: the first colour a test uses becomes `DATA`, the second `IT`; a test that used all three (e.g. the tally share test with `1/3`) is rewritten for two colours with the expected fraction updated (`1/2` or whatever the new counts give — recompute from the test's own numbers, do not guess). Team-name strings follow (`"data"`, `"it"`). Check `grep -n 'MAGENTA\|LIME\|CYAN\|"magenta"\|"lime"\|"cyan"' src/main/java` returns nothing.

- [x] **Step 4: Run the tests**

Run: `./gradlew --offline mods:metacraft-rivals:runGameTest`
Expected: `All 40 required tests passed` (39 ours + always_pass; the count before this task — verify with `grep -c @GameTest` and adjust if the squid pass changed it).

- [x] **Step 5: Commit**

```bash
git add mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/PaintColor.java mods/metacraft-rivals/src/main/resources/assets/metacraft-rivals/lang/en_us.json mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/gun/PaintBall.java mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java mods/metacraft-rivals/README.md
git commit -m "rivals: two teams — DATA and IT in their ovve colours"
```

---

### Task 2: The client-state table

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/paint/PaintStates.java`
- Test: `gametest/RivalsGameTests.java` (append `paintStatesAreUniqueAndSafe`)

**Interfaces:**
- Produces:
  - `PaintStates.connected(PaintColor color, Direction face, int bits) → BlockState` (bits 0..15)
  - `PaintStates.splat(PaintColor color, int faceMask) → BlockState` (mask 1..63; popcount 1 delegates to `connected(color, face, 0)`)
  - `PaintStates.all() → List<BlockState>` every client state in use (306)
  - `PaintStates.DONORS` = `List.of(Blocks.SCULK_VEIN, Blocks.GLOW_LICHEN, Blocks.RESIN_CLUMP, Blocks.TRIPWIRE)`
  - `PaintStates.CONNECTED_PER_COLOR = 96`, `SPLAT_PER_COLOR = 57`

- [x] **Step 1: Write the failing test** (append at the end of the test class)

```java
	/** Spec §2: every server paint state has its own client state, and none of them shows water or nothing. */
	@GameTest
	public void paintStatesAreUniqueAndSafe(GameTestHelper helper) {
		List<BlockState> all = PaintStates.all();
		helper.assertValueEqual(all.size(), PaintColor.values().length * (PaintStates.CONNECTED_PER_COLOR + PaintStates.SPLAT_PER_COLOR), "client states in use");
		helper.assertValueEqual(new HashSet<>(all).size(), all.size(), "client states are distinct");
		for (BlockState state : all) {
			helper.assertTrue(PaintStates.DONORS.contains(state.getBlock()), "a donor block: " + state);
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				helper.assertFalse(state.getValue(BlockStateProperties.WATERLOGGED), "never waterlogged: " + state);
			}
			if (state.getBlock() instanceof MultifaceBlock) {
				boolean anyFace = false;
				for (Direction d : Direction.values()) anyFace |= state.getValue(MultifaceBlock.getFaceProperty(d));
				helper.assertTrue(anyFace, "a multiface donor state with no face renders nothing: " + state);
			}
		}
		// The same request always gives the same state, and popcount-1 splat masks fold into connected.
		helper.assertValueEqual(PaintStates.connected(PaintColor.DATA, Direction.UP, 5), PaintStates.connected(PaintColor.DATA, Direction.UP, 5), "deterministic");
		helper.assertValueEqual(PaintStates.splat(PaintColor.IT, 1 << Direction.NORTH.ordinal()), PaintStates.connected(PaintColor.IT, Direction.NORTH, 0), "single-face mask is a connected state");
		helper.succeed();
	}
```

- [x] **Step 2: Run it to see it fail**

Run: `./gradlew --offline mods:metacraft-rivals:runGameTest`
Expected: compile error, `PaintStates` does not exist.

- [x] **Step 3: Implement**

```java
package nu.metacraft.rivals.paint;

/**
 * The client-state table (spec §2). A vanilla client can only be shown vanilla blockstates, and a
 * painted cell now needs to say which of its four in-plane neighbours are painted, so paint borrows
 * every state of four donor blocks that render whatever the pack says, have no collision and no
 * client-side behaviour: the three multiface blocks (63 usable states each: not waterlogged, at
 * least one face) and tripwire (128). Server states are numbered per colour — connected first
 * (face × bits, 96), then the multi-face splat masks (57) — and take the pool in donor order.
 */
public final class PaintStates {
	public static final List<Block> DONORS = List.of(Blocks.SCULK_VEIN, Blocks.GLOW_LICHEN, Blocks.RESIN_CLUMP, Blocks.TRIPWIRE);
	public static final int CONNECTED_PER_COLOR = 6 * 16;
	public static final int SPLAT_PER_COLOR = 63 - 6;
	private static final int PER_COLOR = CONNECTED_PER_COLOR + SPLAT_PER_COLOR;
	private static final List<BlockState> POOL = pool();
	private static final Direction[] DIRECTIONS = Direction.values();

	private PaintStates() {}

	/** The pool: every usable donor state, in donor order, each donor's states in registry order. */
	private static List<BlockState> pool() {
		List<BlockState> out = new ArrayList<>();
		for (Block donor : DONORS) {
			for (BlockState state : donor.getStateDefinition().getPossibleStates()) {
				if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) continue;
				if (donor instanceof MultifaceBlock) {
					boolean any = false;
					for (Direction d : DIRECTIONS) any |= state.getValue(MultifaceBlock.getFaceProperty(d));
					if (!any) continue;
				}
				out.add(state);
			}
		}
		if (out.size() < PaintColor.values().length * PER_COLOR) {
			throw new IllegalStateException("[" + Rivals.MOD_ID + "] " + out.size() + " donor states for "
					+ PaintColor.values().length * PER_COLOR + " paint states");
		}
		return List.copyOf(out);
	}

	public static BlockState connected(PaintColor color, Direction face, int bits) {
		return POOL.get(color.ordinal() * PER_COLOR + face.ordinal() * 16 + (bits & 15));
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
		return POOL.get(color.ordinal() * PER_COLOR + CONNECTED_PER_COLOR + index);
	}

	/** Every client state in use, for tests and the pack. */
	public static List<BlockState> all() {
		return List.copyOf(POOL.subList(0, PaintColor.values().length * PER_COLOR));
	}

	/** Which server state a client state stands for, for the pack: (colour, face, bits) or (colour, mask). */
	public record Entry(PaintColor color, @Nullable Direction face, int bits, int faceMask) {}

	public static Entry entry(BlockState client) {
		int i = POOL.indexOf(client);
		if (i < 0 || i >= PaintColor.values().length * PER_COLOR) throw new IllegalArgumentException("not a paint state: " + client);
		PaintColor color = PaintColor.values()[i / PER_COLOR];
		int local = i % PER_COLOR;
		if (local < CONNECTED_PER_COLOR) return new Entry(color, DIRECTIONS[local / 16], local % 16, 1 << (local / 16));
		int index = local - CONNECTED_PER_COLOR;
		for (int mask = 1; mask < 64; mask++) {
			if (Integer.bitCount(mask) < 2) continue;
			if (index-- == 0) return new Entry(color, null, 0, mask);
		}
		throw new IllegalStateException();
	}
}
```

- [x] **Step 4: Run the tests**

Expected: `All 41 required tests passed`.

- [x] **Step 5: Commit**

```bash
git add mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/paint/PaintStates.java mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java
git commit -m "rivals: the client-state table — 306 paint states over four donors"
```

---

### Task 3: The connected block and the cell state machine

**Files:**
- Create: `paint/Paint.java`, `paint/ConnectedPaintBlock.java`
- Modify: `paint/PaintBlock.java` (mapping via `PaintStates.splat`; drop `verifyDonor`), `paint/PaintBlocks.java` (register both), `paint/Painter.java:74-112` (`paintable`, `paintFace`), `paint/PaintTally.java:58-100` (`count`, `reset` through `Paint`), `PaintColor.java` (remove `donor`, `donorPath`)
- Test: append `cornerCellFallsBackToSplat`, `tallyCountsConnectedAndSplat`

**Interfaces:**
- Produces:
  - `interface Paint { PaintColor color(); int faceMask(BlockState state); }` — mask bit i = `Direction.values()[i]` painted (the direction of the *supporting* block from the cell, i.e. the attach direction, same convention as `MultifaceBlock` face properties).
  - `ConnectedPaintBlock`: properties `FACE = EnumProperty.create("face", Direction.class)` (the attach direction), `NEG_U, POS_U, NEG_V, POS_V` (`BooleanProperty`); static `int bits(BlockState)`, `BlockState withBits(BlockState, int)`, `static Direction[] inPlane(Direction face)` returning `{negU, posU, negV, posV}` per spec §3 table.
  - `PaintBlocks.connected(PaintColor)`, `PaintBlocks.splat(PaintColor)` (the old `of` renamed to `splat`).
  - `Painter.paintFace` unchanged signature; `Painter.isPaint(BlockState)`.

- [x] **Step 1: Write the failing tests**

```java
	/** Spec §4: floor then wall in the same air cell → the multiface fallback with both faces. */
	@GameTest
	public void cornerCellFallsBackToSplat(GameTestHelper helper) {
		BlockPos floor = new BlockPos(2, 1, 2);
		BlockPos wall = new BlockPos(2, 2, 1);
		helper.setBlock(floor, Blocks.STONE);
		helper.setBlock(wall, Blocks.STONE);
		BlockPos cell = new BlockPos(2, 2, 2);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Painter.paintFace(level, helper.absolutePos(floor), Direction.UP, PaintColor.DATA), "floor painted");
		BlockState single = level.getBlockState(helper.absolutePos(cell));
		helper.assertTrue(single.getBlock() instanceof ConnectedPaintBlock, "one face is a connected cell");
		helper.assertValueEqual(single.getValue(ConnectedPaintBlock.FACE), Direction.DOWN, "floor paint attaches down");
		helper.assertTrue(Painter.paintFace(level, helper.absolutePos(wall), Direction.SOUTH, PaintColor.DATA), "wall painted");
		BlockState corner = level.getBlockState(helper.absolutePos(cell));
		helper.assertTrue(corner.getBlock() instanceof PaintBlock, "two faces fall back to the multiface block");
		helper.assertTrue(corner.getValue(MultifaceBlock.getFaceProperty(Direction.DOWN)), "keeps the floor face");
		helper.assertTrue(corner.getValue(MultifaceBlock.getFaceProperty(Direction.NORTH)), "gains the wall face");
		helper.assertFalse(Painter.paintFace(level, helper.absolutePos(wall), Direction.SOUTH, PaintColor.DATA), "same face again is a no-op");
		helper.assertTrue(Painter.paintFace(level, helper.absolutePos(wall), Direction.SOUTH, PaintColor.IT), "the other colour repaints");
		BlockState over = level.getBlockState(helper.absolutePos(cell));
		helper.assertTrue(over.getBlock() instanceof ConnectedPaintBlock && ((Paint) over.getBlock()).color() == PaintColor.IT, "overpaint wipes the cell to one IT face");
		helper.succeed();
	}

	/** One face per connected cell, popcount per splat cell. */
	@GameTest
	public void tallyCountsConnectedAndSplat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PaintTally tally = new PaintTally();
		Map<PaintColor, Integer> before = tally.count(level);
		for (int x = 1; x <= 3; x++) helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
		for (int x = 1; x <= 3; x++) Painter.paintFace(level, helper.absolutePos(new BlockPos(x, 1, 2)), Direction.UP, PaintColor.DATA);
		Painter.paintFace(level, helper.absolutePos(new BlockPos(2, 2, 1)), Direction.SOUTH, PaintColor.DATA);
		for (int x = 1; x <= 3; x++) tally.track(helper.absolutePos(new BlockPos(x, 2, 2)));
		Map<PaintColor, Integer> after = tally.count(level);
		helper.assertValueEqual(after.get(PaintColor.DATA) - before.get(PaintColor.DATA), 4, "three floor faces plus one wall face");
		helper.succeed();
	}
```

(`PaintTally` needs a public no-arg constructor; the existing tally test already constructs one — follow it.)

- [x] **Step 2: Run to see them fail** — compile error on `ConnectedPaintBlock`.

- [x] **Step 3: `Paint` and `ConnectedPaintBlock`**

```java
package nu.metacraft.rivals.paint;

/** A paint block of either kind: its colour, and which faces of the cell carry paint. */
public interface Paint {
	PaintColor color();

	/** Bit i set = paint on the {@code Direction.values()[i]} side of the cell (the attach direction). */
	int faceMask(BlockState state);
}
```

```java
package nu.metacraft.rivals.paint;

/**
 * Paint on one face of a cell, with the four in-plane connection bits the client turns into a
 * continuous sheet (spec §3). {@link #FACE} is the attach direction: floor paint attaches DOWN. The
 * bits are recomputed from the neighbours whenever one changes, through {@link #updateShape}, so
 * a cell whose neighbour is painted or wiped re-borders itself without anyone telling it to.
 */
public final class ConnectedPaintBlock extends Block implements Paint, PolymerBlock {
	public static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
	public static final BooleanProperty NEG_U = BooleanProperty.create("neg_u");
	public static final BooleanProperty POS_U = BooleanProperty.create("pos_u");
	public static final BooleanProperty NEG_V = BooleanProperty.create("neg_v");
	public static final BooleanProperty POS_V = BooleanProperty.create("pos_v");
	private static final BooleanProperty[] BITS = {NEG_U, POS_U, NEG_V, POS_V};
	private final PaintColor color;

	public ConnectedPaintBlock(Properties properties, PaintColor color) {
		super(properties);
		this.color = color;
		registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN)
				.setValue(NEG_U, false).setValue(POS_U, false).setValue(NEG_V, false).setValue(POS_V, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACE, NEG_U, POS_U, NEG_V, POS_V);
	}

	@Override
	public PaintColor color() {
		return color;
	}

	@Override
	public int faceMask(BlockState state) {
		return 1 << state.getValue(FACE).ordinal();
	}

	public static int bits(BlockState state) {
		int bits = 0;
		for (int i = 0; i < 4; i++) if (state.getValue(BITS[i])) bits |= 1 << i;
		return bits;
	}

	public static BlockState withBits(BlockState state, int bits) {
		for (int i = 0; i < 4; i++) state = state.setValue(BITS[i], (bits >> i & 1) != 0);
		return state;
	}

	/**
	 * The in-plane neighbours of a face, in bit order NEG_U, POS_U, NEG_V, POS_V (spec §3): normal
	 * Y → u = x, v = z; normal X → u = z, v = y; normal Z → u = x, v = y.
	 */
	public static Direction[] inPlane(Direction face) {
		return switch (face.getAxis()) {
			case Y -> new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
			case X -> new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP};
			case Z -> new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP};
		};
	}

	/** Bits for {@code cell} holding {@code color} on {@code face}, from what its four in-plane neighbours hold now. */
	public static int neighbourBits(BlockGetter level, BlockPos cell, Direction face, PaintColor color) {
		Direction[] around = inPlane(face);
		int bits = 0;
		for (int i = 0; i < 4; i++) {
			BlockState other = level.getBlockState(cell.relative(around[i]));
			if (other.getBlock() instanceof Paint paint && paint.color() == color && (paint.faceMask(other) & 1 << face.ordinal()) != 0) bits |= 1 << i;
		}
		return bits;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		// The same paper-thin slab the multiface donors use, so rays and paintable() see a face, not a cube.
		return MultifaceBlock.getShapeForFace(state.getValue(FACE)); // if this helper is private in 26.2, copy sculk vein's six shapes: 1/16 thick slabs against each face
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction face = state.getValue(FACE);
		BlockPos support = pos.relative(face);
		return Block.isFaceFull(level.getBlockState(support).getCollisionShape(level, support), face.getOpposite());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
		return withBits(state, neighbourBits(level, pos, state.getValue(FACE), color));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return PaintStates.connected(color, state.getValue(FACE), bits(state));
	}
}
```

Check the exact `updateShape` signature and `getShapeForFace` visibility with `javap -p` on `net.minecraft.world.level.block.Block` / `MultifaceBlock` in `~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar`; adapt the override, not the design. If `getShapeForFace` is not accessible, define six `Block.box` slabs: DOWN `box(0,0,0,16,1,16)`, UP `box(0,15,0,16,16,16)`, NORTH `box(0,0,0,16,16,1)`, SOUTH `box(0,0,15,16,16,16)`, WEST `box(0,0,0,1,16,16)`, EAST `box(15,0,0,16,16,16)`.

- [x] **Step 4: `PaintBlock` mapping, `Paint`, registration, `PaintColor`**

`PaintBlock implements Paint`: `color()` returns the field; `faceMask` ORs the six face properties; `getPolymerBlockState` returns `PaintStates.splat(color, faceMask(state))`; delete `verifyDonor`. Remove `donor`, `donorPath()` and the `Block`/`Blocks`/`BuiltInRegistries` imports from `PaintColor`.

`PaintBlocks`: two maps; ids `paint_<id>` (splat, keeps the v3 id) and `paint_<id>_face` (connected); same properties builder for both; `splat(color)`, `connected(color)`.

- [x] **Step 5: `paintFace`**

```java
	/** Either paint block. */
	public static boolean isPaint(BlockState state) {
		return state.getBlock() instanceof Paint;
	}

	public static boolean paintable(BlockState surface) {
		return !surface.isAir() && !surface.canBeReplaced() && !(surface.getBlock() instanceof LiquidBlock) && !isPaint(surface);
	}

	/**
	 * Paint one face (spec §4): the {@code face} side of the block at {@code surface}. A face that is not
	 * full takes display quads. Otherwise the cell in front must be air or paint. Air, or another colour,
	 * becomes a connected cell for this face (overpaint wipes the cell); a same-colour connected cell on
	 * another face becomes the multiface fallback with both; a same-colour multiface cell gains the face.
	 * The connection bits come from {@link ConnectedPaintBlock#neighbourBits} here and follow neighbour
	 * changes on their own afterwards. Returns whether anything changed.
	 */
	public static boolean paintFace(ServerLevel level, BlockPos surface, Direction face, PaintColor color) {
		BlockState surfaceState = level.getBlockState(surface);
		if (!paintable(surfaceState)) return false;
		if (!Block.isFaceFull(surfaceState.getCollisionShape(level, surface), face)) {
			return PaintDisplays.of(level).paint(level, surface, face, color);
		}
		BlockPos cell = surface.relative(face);
		Direction attach = face.getOpposite();
		int bit = 1 << attach.ordinal();
		BlockState existing = level.getBlockState(cell);
		BlockState next;
		if (existing.isAir() || (existing.getBlock() instanceof Paint other && other.color() != color)) {
			next = connectedState(level, cell, attach, color);
		} else if (existing.getBlock() instanceof Paint same) {
			int mask = same.faceMask(existing);
			if ((mask & bit) != 0) return false;
			next = splatState(color, mask | bit);
		} else {
			return false;
		}
		if (!level.setBlock(cell, next, Block.UPDATE_ALL)) return false;
		PaintTally.of(level).track(cell);
		return true;
	}

	private static BlockState connectedState(ServerLevel level, BlockPos cell, Direction attach, PaintColor color) {
		BlockState state = PaintBlocks.connected(color).defaultBlockState().setValue(ConnectedPaintBlock.FACE, attach);
		return ConnectedPaintBlock.withBits(state, ConnectedPaintBlock.neighbourBits(level, cell, attach, color));
	}

	private static BlockState splatState(PaintColor color, int mask) {
		BlockState state = PaintBlocks.splat(color).defaultBlockState();
		for (Direction d : DIRECTIONS) state = state.setValue(MultifaceBlock.getFaceProperty(d), (mask >> d.ordinal() & 1) != 0);
		return state;
	}
```

`PaintTally.count`: `if (!(state.getBlock() instanceof Paint paint)) { it.remove(); continue; } counts.merge(paint.color(), Integer.bitCount(paint.faceMask(state)), Integer::sum);`. `reset`: `instanceof Paint`.

Any other `instanceof PaintBlock` in the module (`grep -rn 'instanceof PaintBlock' src/main/java`) — `PlayerTick.paintUnder`, `paintedWallBeside`, `PaintDisplays`, tests — becomes `instanceof Paint paint` with `paint.color()` and `paint.faceMask(state)` in place of `paint.color` and the face-property reads. Squid detection reads "own paint under the feet": the DOWN bit of the cell's mask.

- [x] **Step 6: Run the tests**

Expected: `All 43 required tests passed`. Existing tests that asserted `instanceof PaintBlock` after a single `paintFace` now see `ConnectedPaintBlock`: update those assertions to `instanceof Paint` (the behaviour they test is unchanged); do not weaken anything else.

- [x] **Step 7: Commit**

```bash
git add mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/paint mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/PaintColor.java mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/PlayerTick.java mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java
git commit -m "rivals: connected paint cells — one face with four bits, the multiface block as the corner fallback"
```

---

### Task 4: Connection bits follow the neighbours

**Files:**
- Modify: `paint/ConnectedPaintBlock.java` (only if Task 3's `updateShape` does not already cover a case), `paint/Painter.java`
- Test: append `floorPaintConnects`, `wallPaintUsesTheWorldFrame`, `overpaintReconnects`

- [x] **Step 1: Write the failing tests**

```java
	/** Spec §7: a 3×3 floor — centre all four bits, an edge three, a corner two. */
	@GameTest
	public void floorPaintConnects(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) Painter.paintFace(level, helper.absolutePos(new BlockPos(x, 1, z)), Direction.UP, PaintColor.DATA);
		helper.assertValueEqual(ConnectedPaintBlock.bits(level.getBlockState(helper.absolutePos(new BlockPos(2, 2, 2)))), 15, "centre: all four");
		helper.assertValueEqual(ConnectedPaintBlock.bits(level.getBlockState(helper.absolutePos(new BlockPos(1, 2, 2)))), 0b1110, "west edge: everything but NEG_U (west)");
		helper.assertValueEqual(ConnectedPaintBlock.bits(level.getBlockState(helper.absolutePos(new BlockPos(1, 2, 1)))), 0b1010, "north-west corner: POS_U (east) and POS_V (south)");
		BlockState centre = level.getBlockState(helper.absolutePos(new BlockPos(2, 2, 2)));
		helper.assertValueEqual(((PolymerBlock) centre.getBlock()).getPolymerBlockState(centre, PacketContext.get()), PaintStates.connected(PaintColor.DATA, Direction.DOWN, 15), "the client sees the all-connected state");
		helper.succeed();
	}

	/** A 3-wide, 2-high north wall (paint cells south of it): the bottom-middle cell connects up and sideways, not down. */
	@GameTest
	public void wallPaintUsesTheWorldFrame(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 1; x <= 3; x++) for (int y = 2; y <= 3; y++) helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
		for (int x = 1; x <= 3; x++) for (int y = 2; y <= 3; y++) Painter.paintFace(level, helper.absolutePos(new BlockPos(x, y, 1)), Direction.SOUTH, PaintColor.IT);
		BlockState cell = level.getBlockState(helper.absolutePos(new BlockPos(2, 2, 2)));
		helper.assertValueEqual(cell.getValue(ConnectedPaintBlock.FACE), Direction.NORTH, "attaches north");
		helper.assertValueEqual(ConnectedPaintBlock.bits(cell), 0b1011, "NEG_U (west), POS_U (east), POS_V (up); no NEG_V (down)");
		helper.succeed();
	}

	/** IT over the middle of a DATA row: the DATA neighbours drop that bit, the IT cell has none. */
	@GameTest
	public void overpaintReconnects(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 1; x <= 3; x++) helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
		for (int x = 1; x <= 3; x++) Painter.paintFace(level, helper.absolutePos(new BlockPos(x, 1, 2)), Direction.UP, PaintColor.DATA);
		helper.assertValueEqual(ConnectedPaintBlock.bits(level.getBlockState(helper.absolutePos(new BlockPos(1, 2, 2)))), 0b0010, "west cell connects east");
		Painter.paintFace(level, helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, PaintColor.IT);
		helper.assertValueEqual(ConnectedPaintBlock.bits(level.getBlockState(helper.absolutePos(new BlockPos(1, 2, 2)))), 0, "west cell lost its neighbour");
		helper.assertValueEqual(ConnectedPaintBlock.bits(level.getBlockState(helper.absolutePos(new BlockPos(3, 2, 2)))), 0, "east cell lost its neighbour");
		helper.assertValueEqual(ConnectedPaintBlock.bits(level.getBlockState(helper.absolutePos(new BlockPos(2, 2, 2)))), 0, "the IT cell has no IT neighbours");
		helper.succeed();
	}
```

- [x] **Step 2: Run them.** If `updateShape` fires for in-plane neighbours on `setBlock(..., UPDATE_ALL)` (it should: neighbours get shape updates for all six directions), the tests may already pass. If a bit is stale, add an explicit refresh at the end of `paintFace` after `setBlock`:

```java
	/** Re-derive the bits of the four in-plane neighbours of {@code cell} for {@code face}, whatever colour they hold. */
	static void refreshNeighbours(ServerLevel level, BlockPos cell, Direction face) {
		for (Direction d : ConnectedPaintBlock.inPlane(face)) {
			BlockPos at = cell.relative(d);
			BlockState other = level.getBlockState(at);
			if (other.getBlock() instanceof ConnectedPaintBlock block && other.getValue(ConnectedPaintBlock.FACE) == face) {
				BlockState fresh = ConnectedPaintBlock.withBits(other, ConnectedPaintBlock.neighbourBits(level, at, face, block.color()));
				if (fresh != other) level.setBlock(at, fresh, Block.UPDATE_CLIENTS);
			}
		}
	}
```

Call it with the attach direction for every cell that changed (including the overpaint case, where the old colour's neighbours must lose the bit — `neighbourBits` with the neighbour's own colour handles that).

- [x] **Step 3: Run the tests** — Expected: `All 46 required tests passed`.

- [x] **Step 4: Commit**

```bash
git add mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/paint mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java
git commit -m "rivals: connection bits follow the neighbours — floors, walls and overpaint re-border themselves"
```

---

### Task 5: The pack — uniform textures, six quads, four blockstate overrides

**Files:**
- Create: `pack/PaintArt.java`
- Modify: `pack/SplatArt.java` (keep `SHAPES`, `whiteMask`, the `splat_quad_*` item files and `PAINT_ALPHA`; delete the block textures, strips, `quadModel`, `blockstate`, `FACES`, frames, mcmeta), `pack/RivalsPack.java` (call both; new log line)
- Test: append `packCoversEveryPaintState`

**Interfaces:**
- Produces: `PaintArt.packFiles() → Map<String, byte[]>`; `PaintArt.textureName(PaintColor, int bits)` = `paint_<id>_<bits>`; `PaintArt.modelName(Direction attach)` = `paint_face_<down|up|north|south|west|east>`; `PaintArt.encodeRed(int rgb, int bits)`.

- [x] **Step 1: Write the failing test**

```java
	/** Every client state in use has a blockstate variant; every (colour, bits) has a texture; the marker alpha is on every texel. */
	@GameTest
	public void packCoversEveryPaintState(GameTestHelper helper) throws IOException {
		Map<String, byte[]> files = PaintArt.packFiles();
		for (PaintColor color : PaintColor.values()) {
			for (int bits = 0; bits < 16; bits++) {
				byte[] png = files.get("assets/metacraft-rivals/textures/block/" + PaintArt.textureName(color, bits) + ".png");
				helper.assertTrue(png != null, "texture for " + color + " bits " + bits);
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
				int argb = image.getRGB(0, 0);
				helper.assertValueEqual(argb >>> 24, SplatArt.PAINT_ALPHA, "marker alpha");
				helper.assertValueEqual((argb >> 16 & 0xFF) & 0x0F, bits, "bits in the red nibble");
				helper.assertValueEqual(image.getRGB(15, 15), argb, "uniform");
			}
		}
		for (Block donor : PaintStates.DONORS) {
			String path = "assets/minecraft/blockstates/" + BuiltInRegistries.BLOCK.getKey(donor).getPath() + ".json";
			helper.assertTrue(files.containsKey(path), "override " + path);
			String json = new String(files.get(path), StandardCharsets.UTF_8);
			for (BlockState state : PaintStates.all()) {
				if (state.getBlock() != donor) continue;
				helper.assertTrue(json.contains("\"" + PaintArt.variantKey(state) + "\""), "variant for " + state);
			}
		}
		helper.succeed();
	}
```

- [x] **Step 2: Run it** — compile error.

- [x] **Step 3: Implement `PaintArt`**

```java
/**
 * Paint art for connected paint (spec §5). No edge tiles: the shader draws the border. Per colour
 * sixteen 16×16 textures of one flat colour, the four connection bits in the low nibble of red and
 * the paint marker in alpha; uniform sprites survive mipmapping exactly, which is what makes reading
 * bits back out of a texel safe. Six one-quad models (one per attach direction), and a variants
 * blockstate for each donor block mapping every client state in use to its model and texture; donor
 * states we do not use point at the empty model so a stray vanilla sculk vein shows nothing.
 */
public final class PaintArt {
	public static final int SIZE = 16;
	private static final Direction[] DIRECTIONS = Direction.values();

	public static String textureName(PaintColor color, int bits) { return "paint_" + color.id + "_" + bits; }
	public static String modelName(Direction attach) { return "paint_face_" + attach.getSerializedName(); }

	/** The red channel with the bits in its low nibble. */
	public static int encodeRed(int rgb, int bits) { return (rgb >> 16 & 0xF0) | (bits & 15); }

	public static Map<String, byte[]> packFiles() {
		Map<String, byte[]> files = new LinkedHashMap<>();
		for (PaintColor color : PaintColor.values()) {
			for (int bits = 0; bits < 16; bits++) {
				files.put("assets/" + Rivals.MOD_ID + "/textures/block/" + textureName(color, bits) + ".png", uniform(color.rgb, bits));
			}
		}
		for (Direction attach : DIRECTIONS) {
			files.put("assets/" + Rivals.MOD_ID + "/models/block/" + modelName(attach) + ".json", quad(attach).getBytes(StandardCharsets.UTF_8));
		}
		files.put("assets/" + Rivals.MOD_ID + "/models/block/paint_none.json", "{\"textures\":{},\"elements\":[]}".getBytes(StandardCharsets.UTF_8));
		for (Block donor : PaintStates.DONORS) {
			files.put("assets/minecraft/blockstates/" + BuiltInRegistries.BLOCK.getKey(donor).getPath() + ".json", blockstate(donor).getBytes(StandardCharsets.UTF_8));
		}
		return files;
	}

	static byte[] uniform(int rgb, int bits) {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		int argb = SplatArt.PAINT_ALPHA << 24 | encodeRed(rgb, bits) << 16 | (rgb & 0xFFFF);
		for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) image.setRGB(x, y, argb);
		return SplatArt.png(image); // make png(BufferedImage) package-private static in SplatArt
	}

	/** One paper-thin element against the attach face, both sides textured with #paint, no tint. */
	static String quad(Direction attach) {
		// element bounds 0.1/16 off the face, like sculk vein's own model: e.g. DOWN → from [0,0.1,0] to [16,0.1,16] with faces up + down
		...
	}

	/** The variant key of a client state, e.g. "attached=false,disarmed=false,east=true,north=false,powered=false,south=false,west=true". */
	public static String variantKey(BlockState state) {
		return state.getProperties().stream().sorted(Comparator.comparing(Property::getName))
				.map(p -> p.getName() + "=" + valueName(state, p)).collect(Collectors.joining(","));
	}

	static String blockstate(Block donor) {
		JsonObject variants = new JsonObject();
		for (BlockState state : donor.getStateDefinition().getPossibleStates()) {
			JsonObject variant = new JsonObject();
			if (PaintStates.all().contains(state)) {
				PaintStates.Entry entry = PaintStates.entry(state);
				if (entry.face() != null) {
					variant.addProperty("model", Rivals.MOD_ID + ":block/" + modelName(entry.face()));
					// the texture goes through a per-(colour,bits,face) model wrapper: models/block/paint_<id>_<bits>_<face>.json = {"parent": "metacraft-rivals:block/paint_face_<face>", "textures": {"paint": "metacraft-rivals:block/paint_<id>_<bits>"}}
				} else {
					// splat mask: a multipart is not available in a variants file, so emit one wrapper model per mask that lists all its faces' elements with the all-connected texture (bits 15)
				}
			} else {
				variant.addProperty("model", Rivals.MOD_ID + ":block/paint_none");
			}
			variants.add(variantKey(state), variant);
		}
		JsonObject root = new JsonObject();
		root.add("variants", variants);
		return root.toString();
	}
}
```

Concrete rules for the two `...` parts, so the implementer does not invent them: (1) connected entries reference a wrapper model `paint_<id>_<bits>_<face>` (generate all 2 × 16 × 6 = 192 tiny JSON files, `parent` = the face quad, `textures.paint` = the colour/bits texture); (2) splat entries reference `paint_<id>_mask<mask>` (2 × 57 files), each a model whose `elements` are the face quads of every set bit, all textured with `paint_<id>_15`; the six quad element definitions live in one Java method used by both the face models and the mask models. Use `JsonObject` for all JSON, never string templates for the variants file. `valueName` for `Direction`/boolean uses `Property.getName(value)`.

- [x] **Step 4: Wire the pack.** `RivalsPack.init`: `PaintArt.packFiles().forEach(builder::addData); SplatArt.packFiles().forEach(builder::addData);` and a log line `"[{}] pack: {} paint files ({} colours × 16 textures, 6 face models, {} donors), {} splat-quad files, terrain shader"`. `SplatArt.packFiles` returns only the quad items.

- [x] **Step 5: Run the tests** — Expected: `All 47 required tests passed`.

- [x] **Step 6: Commit**

```bash
git add mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/pack mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java
git commit -m "rivals: the pack for connected paint — bit-carrying textures, six quads, four donor overrides"
```

---

### Task 6: The shader draws the border

**Files:**
- Modify: `src/main/resources/rivals_shaders/terrain.fsh` (the RIVALS_GLOSS block only)

No game test can see it; verification is reading it back plus the in-game round. `chunkPos` (chunk-relative vertex position) and `viewPos` are provided by `terrain.vsh`; `GameTime`, `TextureSize`, `Sampler0` are vanilla uniforms already used in the file.

- [x] **Step 1: Replace the block**

```glsl
	// RIVALS_GLOSS: paint texels carry alpha 229/255 = 0.898 as a marker; the window admits 228..230
	// (three steps, filtering tolerance) and stays five steps clear of the nearest vanilla value (224).
	// Derivatives are only defined in uniform control flow: take them before the paint branch.
	vec3 chunkDx = dFdx(chunkPos), chunkDy = dFdy(chunkPos);
	vec3 viewDx = dFdx(viewPos), viewDy = dFdy(viewPos);
	if (abs(tex.a - 0.898) < 0.004) {
		// Connected paint (spec §5). The texel is one flat colour whose red low nibble says which of the
		// cell's four in-plane neighbours are painted; the face normal (from chunkPos) picks the two
		// in-plane axes, and a rounded box — full on connected sides, inset and rounded on the others —
		// decides whether this fragment is paint at all. Inside it: a meniscus rim from the same distance,
		// a three-wave crawling normal, and glint / sheen / fresnel that mix toward light instead of adding.
		int bits = int(mod(floor(tex.r * 255.0 + 0.5), 16.0));
		vec3 nc = abs(normalize(cross(chunkDx, chunkDy)));
		vec2 p;
		vec2 along;
		if (nc.y >= nc.x && nc.y >= nc.z) { p = chunkPos.xz; }
		else if (nc.x >= nc.z) { p = chunkPos.zy; }
		else { p = chunkPos.xy; }
		along = p;
		p = fract(p);
		float t = GameTime * 1200.0;
		bool negU = (bits & 1) != 0, posU = (bits & 2) != 0, negV = (bits & 4) != 0, posV = (bits & 8) != 0;
		// Inset each unconnected side by 0.06 plus a slow wobble; connected sides run out past the cell.
		float w0 = 0.02 * sin(along.y * 12.0 + t * 1.1), w1 = 0.02 * sin(along.y * 12.0 + 2.0 + t * 0.9);
		float w2 = 0.02 * sin(along.x * 12.0 + 4.0 + t * 1.3), w3 = 0.02 * sin(along.x * 12.0 + 1.0 + t * 0.8);
		float lo_u = negU ? -1.0 : 0.06 + w0, hi_u = posU ? 2.0 : 0.94 + w1;
		float lo_v = negV ? -1.0 : 0.06 + w2, hi_v = posV ? 2.0 : 0.94 + w3;
		vec2 centre = vec2(lo_u + hi_u, lo_v + hi_v) * 0.5;
		vec2 half = vec2(hi_u - lo_u, hi_v - lo_v) * 0.5;
		// Corner radius only where both sides meeting at that corner are unconnected.
		bool cu = p.x < centre.x ? !negU : !posU;
		bool cv = p.y < centre.y ? !negV : !posV;
		float r = (cu && cv) ? 0.28 : 0.0;
		vec2 q = abs(p - centre) - half + r;
		float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
		if (d > 0.0) discard;
		vec3 n = normalize(cross(viewDx, viewDy));
		vec3 v = normalize(-viewPos);
		vec3 l = normalize(vec3(0.3 + 0.15 * sin(t), 0.8, 0.5 + 0.15 * cos(t)));
		vec3 wave = vec3(
			sin(chunkPos.x * 6.0 + t * 0.9) * 0.4 + sin((chunkPos.x + chunkPos.z) * 11.0 + t * 1.4) * 0.35 + sin(chunkPos.z * 17.0 - t * 2.3) * 0.25,
			0.0,
			cos(chunkPos.z * 6.0 + t * 0.9) * 0.4 + cos((chunkPos.z - chunkPos.x) * 11.0 + t * 1.4) * 0.35 + cos(chunkPos.x * 17.0 - t * 2.3) * 0.25);
		n = normalize(n + 0.05 * wave);
		vec3 lightened = mix(color.rgb, vec3(1.0), 0.35);
		// The meniscus: the outer 0.08 of the shape is a bevel, lit on the side facing the light.
		float rim = smoothstep(-0.08, 0.0, d);
		vec2 edgeDir = normalize(p - centre + vec2(0.0001));
		float facing = dot(edgeDir, normalize(vec2(l.x, l.z)));
		color.rgb = mix(color.rgb, lightened, 0.30 * rim * max(facing, 0.0));
		color.rgb *= 1.0 - 0.12 * rim * max(-facing, 0.0);
		float glint = pow(max(dot(reflect(-l, n), v), 0.0), 60.0) * 0.55;
		float sheen = pow(max(dot(reflect(-l, n), v), 0.0), 5.0) * 0.14;
		float fresnel = pow(1.0 - max(dot(n, v), 0.0), 4.0) * 0.18;
		color.rgb = mix(color.rgb, vec3(1.0), glint);
		color.rgb = mix(color.rgb, lightened, sheen);
		color.rgb = mix(color.rgb, lightened, fresnel);
		color.a = 1.0;
	}
```

Notes: no `dFdx`/`dFdy`/texture sampling with implicit LOD inside the branch (the four derivative values above are the only ones used). The `&` on `int` needs GLSL ≥ 1.30 (vanilla's `#version` line is higher; keep it). The `color` variable at this point is `tex * vertexColor` mixed with fog per vanilla — leave the preceding lines as they are; the bits are read from `tex`, never from `color`. Delete the v3 wobble-UV sampling and the neighbour-texel meniscus loop (they belong to the sprite era). The red nibble shifts the paint colour by at most 15/255 — invisible; do not compensate.

- [x] **Step 2: Read the file back** and check braces, that `chunkPos`/`viewPos` are declared `in vec3` at the top, and that no `sampleNearest`/`texel` identifiers from the removed code remain unused (unused is fine; undefined is not).

- [x] **Step 3: Run the tests** — Expected still `All 47 required tests passed` (the pack test asserts the shader files are present).

- [x] **Step 4: Commit**

```bash
git add mods/metacraft-rivals/src/main/resources/rivals_shaders/terrain.fsh
git commit -m "rivals: the shader draws the paint border — bits from the texel, a rounded wobbling box, the wet rim from its distance"
```

---

### Task 7: README, setup message, final check

**Files:**
- Modify: `README.md` (teams, how connected paint works, donor caveat incl. tripwire, test count), `RivalsCommands.java` (setup message lists `data, it`; it already uses `PaintColor.idList()` — verify), `docs/superpowers/plans/2026-09-12-metacraft-rivals-v4-connected-paint.md` (tick the boxes)

- [x] **Step 1: README.** Replace the paint section: two teams (ovve colours with the hex values), connected cells vs corner fallback, the 306/317 state table, the shader-drawn border, "real sculk veins, glow lichen, resin clumps and tripwire in an arena render as paint", the count of our tests (`grep -c @GameTest`) and that runGameTest reports one more. Remove the Kenney splat-variant paragraph for blocks; keep the Kenney credit for the display-quad silhouettes and the gun models.

- [x] **Step 2: Full run** — `./gradlew --offline mods:metacraft-rivals:runGameTest` → `All 47 required tests passed`.

- [x] **Step 3: Commit**

```bash
git add mods/metacraft-rivals/README.md mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/RivalsCommands.java docs/superpowers/plans/2026-09-12-metacraft-rivals-v4-connected-paint.md
git commit -m "rivals: README for v4 — two teams, connected paint, the donor table"
```

---

## Self-review

- Spec coverage: §1 (Task 1, 3, 6), §2 (Task 2), §3 (Task 3), §4 (Task 3, 4), §5 (Task 5, 6), §6 (Task 1, 7; `/rivals setup` iterates `PaintColor.values()` so it needs no change), §7 (Tasks 2–5), §8 out of scope.
- Types: `Paint.color()`/`faceMask(BlockState)` used in Tasks 3–5; `PaintStates.connected/splat/all/entry/DONORS` used in Tasks 3, 5; `ConnectedPaintBlock.FACE/bits/withBits/inPlane/neighbourBits` used in Tasks 3, 4; `PaintArt.textureName/modelName/variantKey/encodeRed` used in Task 5; `SplatArt.PAINT_ALPHA`, `SplatArt.png` used in Task 5.
- Test counts: start 39 ours (verify), +1 (T2) → 40, +2 (T3) → 42, +3 (T4) → 45, +1 (T5) → 46; runGameTest totals 41/43/46/47 as written above.
