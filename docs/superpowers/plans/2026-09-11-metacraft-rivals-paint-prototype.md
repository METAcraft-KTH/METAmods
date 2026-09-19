# Metacraft Rivals paint prototype — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new standalone METAmods module, `mods/metacraft-rivals`, where a paint gun shoots coloured blobs that leave paint on block faces for vanilla clients, with minimal teams and a coverage score.

**Architecture:** Paint is a server-side `MultifaceBlock` per colour that Polymer sends to clients as one of vanilla's three multiface blocks (sculk vein, resin clump, glow lichen), whose textures the required resource pack replaces with a generated splat. The gun is a Polymer item with a custom 3D model whose tank is dye-tinted to the shooter's team colour; it throws a `Snowball` subclass that paints a 3×3 blob on hit. A per-level tally walks the painted cells once a second and drives one bossbar per colour.

**Tech Stack:** Java 25, Fabric Loader 0.19.5, Fabric API 0.159.0+26.2, Minecraft 26.2 (Mojang mappings), Polymer 0.17.5+26.2 (core, resource-pack, autohost), Fabric GameTest API, Gradle + Fabric Loom 1.17.

**Spec:** `docs/superpowers/specs/2026-09-11-metacraft-rivals-paint-prototype-design.md` and the gun sheet `docs/superpowers/specs/2026-09-11-paint-gun-design-sheet.md`.

## Global Constraints

- Target versions are exactly those pinned in `gradle.properties` on `dev`: `minecraft_version=26.2`, `loader_version=0.19.5`, `fabric_api_version=0.159.0+26.2`, `polymer_version=0.17.5+26.2`, Java 25. Do not touch these properties.
- Vanilla clients only: no client code, no client mixins. Everything a player sees is the auto-served Polymer resource pack.
- The module is **not** bundled into `dist`; it runs alone via `./gradlew mods:metacraft-rivals:runServer`.
- Mod id `metacraft-rivals`, package `nu.metacraft.rivals`, version property `rivals_version = 0.1.0`.
- Indentation is tabs (checkstyle fails the build on a line starting with tab-then-spaces). No mixins, no access widener, no datagen.
- Commits go on branch `worktree-rivals` (off `dev`) in this worktree. Commit messages: `rivals: <what>`. No `Co-Authored-By` trailer.
- Git commands must be plain (no `&&`, pipes or variables in the same shell line as `git`); the worktree guard rejects compound git commands.
- Game tests: `./gradlew mods:metacraft-rivals:runGameTest` (the task name is confirmed in Task 1). A full check is `./gradlew mods:metacraft-rivals:build mods:metacraft-rivals:runGameTest`.
- Every file below is created under `mods/metacraft-rivals/` unless the path starts elsewhere.

---

### Task 1: Module scaffold, standalone from dist, game tests wired

**Files:**
- Modify: `settings.gradle` (add one include)
- Modify: `gradle.properties` (add `rivals_version`)
- Modify: `build.gradle` (root; `standaloneMods` list and `initDist` skip)
- Modify: `dist/src/main/java/nu/metacraft/CheckMods.java:19-21` and the include loop at `:41-50`
- Create: `mods/metacraft-rivals/build.gradle`
- Create: `mods/metacraft-rivals/src/main/resources/fabric.mod.json`
- Create: `mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/Rivals.java`
- Create: `mods/metacraft-rivals/src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java`
- Create (local, gitignored): `mods/metacraft-rivals/run/eula.txt`, `mods/metacraft-rivals/run/server.properties`, `mods/metacraft-rivals/run/config/polymer/auto-host.json`

**Interfaces:**
- Produces: `nu.metacraft.rivals.Rivals` with `public static final String MOD_ID = "metacraft-rivals"`, `public static final Logger LOGGER`, and `public static Identifier id(String path)`. Later tasks add one `X.register()` / `X.init()` call each to `Rivals.onInitialize()`.
- Produces: `nu.metacraft.rivals.gametest.RivalsGameTests`, the single game-test class every later task adds methods to.

- [ ] **Step 1: Register the module and its version**

In `settings.gradle`, after `include "mods:metacraft-revival"` add:

```groovy
include "mods:metacraft-rivals"
```

In `gradle.properties`, after the line `	pause_version = 1.0.0` add:

```properties
	rivals_version = 0.1.0
```

- [ ] **Step 2: Keep the module out of the dist jar**

In the root `build.gradle`, replace

```groovy
def nonModSubProjects = [
        "mods"
]
```

with

```groovy
def nonModSubProjects = [
        "mods"
]

// Mods that build here but are never bundled into the dist jar: they run on a server of their own.
// dist's CheckMods keeps the same list.
def standaloneMods = [
        "metacraft-rivals"
]
```

and in `ext.initDist` replace

```groovy
                if (nonModSubProjects.contains(it.name)) {
                    return
                }
                if (it.name == "dist") {
                    return
                }
```

with

```groovy
                if (nonModSubProjects.contains(it.name) || standaloneMods.contains(it.name)) {
                    return
                }
                if (it.name == "dist") {
                    return
                }
```

In `dist/src/main/java/nu/metacraft/CheckMods.java`, after the `PROJECT_TO_MOD_ID` map add:

```java
	/**
	 * Modules that build in this repository but are not part of the dist jar (see standaloneMods in the
	 * root build.gradle). They are included in settings.gradle, so the include scan must skip them.
	 */
	private static final Set<String> STANDALONE = Set.of("metacraft-rivals");
```

(add `import java.util.Set;`), and inside the `reader.lines().forEach(line -> { ... })` lambda, replace

```java
						var name = path.substring(path.lastIndexOf(":")+1);
						modsThatMustBePresent.add(PROJECT_TO_MOD_ID.getOrDefault(name, name));
```

with

```java
						var name = path.substring(path.lastIndexOf(":")+1);
						if (STANDALONE.contains(name)) {
							return;
						}
						modsThatMustBePresent.add(PROJECT_TO_MOD_ID.getOrDefault(name, name));
```

- [ ] **Step 3: Create the module build file**

`mods/metacraft-rivals/build.gradle`:

```groovy
// Metacraft Rivals — Splatoon-style paint prototype for vanilla clients (Polymer). See README.md.
// Standalone: not bundled into dist (root build.gradle standaloneMods); run with mods:metacraft-rivals:runServer.
version = project.rivals_version

// Server-side game tests (Fabric GameTest API) live in the main source set:
// ./gradlew mods:metacraft-rivals:runGameTest
fabricApi {
	configureTests {
		enableGameTests = true
		eula = true
	}
}

addPolymerDependency()
dependencies {
	implementation("eu.pb4:polymer-resource-pack:${project.polymer_version}")
	implementation("eu.pb4:polymer-autohost:${project.polymer_version}")
}
```

- [ ] **Step 4: Create fabric.mod.json**

`mods/metacraft-rivals/src/main/resources/fabric.mod.json`:

```json
{
	"schemaVersion": 1,
	"id": "metacraft-rivals",
	"version": "${version}",
	"name": "Metacraft Rivals",
	"description": "Splatoon-style paint prototype for vanilla clients, via Polymer. Players only need the auto-served resource pack.",
	"authors": [
		"Edvin"
	],
	"contact": {
		"sources": "${git_repo}",
		"homepage": "${website}",
		"issues": "${issues}"
	},
	"license": "MIT",
	"environment": "*",
	"entrypoints": {
		"main": [
			"nu.metacraft.rivals.Rivals"
		],
		"fabric-gametest": [
			"nu.metacraft.rivals.gametest.RivalsGameTests"
		]
	},
	"depends": {
		"fabricloader": ">=${loader_version}",
		"minecraft": "~${minecraft_version}",
		"java": ">=25",
		"fabric-api": "*",
		"polymer-core": "*",
		"polymer-resource-pack": "*"
	}
}
```

- [ ] **Step 5: Write the entrypoint and a smoke game test**

`src/main/java/nu/metacraft/rivals/Rivals.java`:

```java
package nu.metacraft.rivals;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metacraft Rivals: a Splatoon-style paint prototype for vanilla clients, via Polymer.
 *
 * Order matters: blocks and the entity first (the gun refers to both), then the pack (which must be
 * required because a client without it sees sculk veins instead of paint), then commands and score.
 */
public class Rivals implements ModInitializer {
	public static final String MOD_ID = "metacraft-rivals";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("[{}] ready", MOD_ID);
	}
}
```

`src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java`:

```java
package nu.metacraft.rivals.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Server-side game tests (Fabric GameTest API). Run headless with
 * {@code ./gradlew mods:metacraft-rivals:runGameTest}; each test gets an empty 8×8×8 structure and
 * positions passed to the helper are relative to it.
 */
public final class RivalsGameTests {
	@GameTest
	public void modLoads(GameTestHelper helper) {
		helper.succeed();
	}
}
```

- [ ] **Step 6: Create the local run directory (untracked; `run/` is gitignored)**

```
mods/metacraft-rivals/run/eula.txt                      → eula=true
mods/metacraft-rivals/run/server.properties             → online-mode=false
                                                          level-type=minecraft\:flat
                                                          gamemode=creative
                                                          motd=Metacraft Rivals dev
mods/metacraft-rivals/run/config/polymer/auto-host.json → {"enabled": true, "required": true, "mod_override": true, "type": "polymer:automatic", "settings": {"forced_address": ""}}
```

- [ ] **Step 7: Build, confirm the game-test task exists, run it**

Run: `./gradlew --offline mods:metacraft-rivals:build`
Expected: BUILD SUCCESSFUL, jar at `mods/metacraft-rivals/build/libs/metacraft-rivals-0.1.0.jar`.

Run: `./gradlew --offline mods:metacraft-rivals:tasks --all | grep -i gametest`
Expected: a line starting with `runGameTest`. If Loom named it differently (e.g. `runGameTest`), use that name everywhere below and note it in the README of Task 7.

Run: `./gradlew --offline mods:metacraft-rivals:runGameTest`
Expected: BUILD SUCCESSFUL and a log line reporting 1 test passed (`modLoads`).

- [ ] **Step 8: Confirm dist still compiles and excludes the module**

Run: `./gradlew --offline dist:compileJava`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew --offline dist:dependencies --configuration runtimeClasspath | grep -c metacraft-rivals`
Expected: `0`.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle gradle.properties build.gradle dist/src/main/java/nu/metacraft/CheckMods.java mods/metacraft-rivals/build.gradle mods/metacraft-rivals/src
git commit -m "rivals: standalone module scaffold with game tests wired"
```

---

### Task 2: Paint colours and paint blocks mapped to multiface donors

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/PaintColor.java`
- Create: `src/main/java/nu/metacraft/rivals/paint/PaintBlock.java`
- Create: `src/main/java/nu/metacraft/rivals/paint/PaintBlocks.java`
- Modify: `src/main/java/nu/metacraft/rivals/Rivals.java` (call `PaintBlocks.register()`)
- Test: `src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java`

**Interfaces:**
- Produces: `enum PaintColor { MAGENTA, LIME, CYAN }` with public final fields `String id`, `String displayName`, `int rgb`, `Block donor`, `TeamColor teamColor`, `BossEvent.BossBarColor barColor`; `String donorTexturePath()`; `static Optional<PaintColor> byId(String)`; `static Optional<PaintColor> byTeam(@Nullable PlayerTeam)`.
- Produces: `final class PaintBlock extends MultifaceBlock implements PolymerBlock` with `public final PaintColor color`.
- Produces: `PaintBlocks.register()` and `static PaintBlock PaintBlocks.of(PaintColor)`.

- [ ] **Step 1: Write the failing test**

Add to `RivalsGameTests` (imports: `nu.metacraft.rivals.PaintColor`, `nu.metacraft.rivals.paint.PaintBlock`, `nu.metacraft.rivals.paint.PaintBlocks`, `net.fabricmc.fabric.api.networking.v1.context.PacketContext`, `net.minecraft.core.Direction`, `net.minecraft.network.chat.Component`, `net.minecraft.world.level.block.MultifaceBlock`, `net.minecraft.world.level.block.state.BlockState`):

```java
	/** Every paint state is sent as its donor block with the same six face flags and never waterlogged. */
	@GameTest
	public void donorMappingKeepsFaces(GameTestHelper helper) {
		for (PaintColor color : PaintColor.values()) {
			PaintBlock block = PaintBlocks.of(color);
			BlockState state = block.defaultBlockState()
					.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
					.setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true);
			BlockState client = block.getPolymerBlockState(state, PacketContext.get());
			helper.assertTrue(client.is(color.donor), Component.literal(color.id + " maps to " + client));
			for (Direction d : Direction.values()) {
				boolean expected = d == Direction.DOWN || d == Direction.NORTH;
				helper.assertTrue(client.getValue(MultifaceBlock.getFaceProperty(d)) == expected,
						Component.literal(color.id + ": face " + d + " should be " + expected));
			}
			helper.assertTrue(!client.getValue(MultifaceBlock.WATERLOGGED), Component.literal(color.id + " sent waterlogged"));
		}
		helper.succeed();
	}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew --offline mods:metacraft-rivals:compileJava`
Expected: compilation FAILS with "package nu.metacraft.rivals.paint does not exist" (the test references classes that do not exist yet).

- [ ] **Step 3: Write PaintColor**

`src/main/java/nu/metacraft/rivals/PaintColor.java`:

```java
package nu.metacraft.rivals;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The paint colours. Each one owns a vanilla multiface block that clients are shown instead of the
 * paint block and whose texture the resource pack replaces with a splat. Sculk vein and resin clump
 * emit no light; glow lichen glows (light 7, lit by the client itself), which is why it is the third,
 * "special" colour. The id doubles as the vanilla team name.
 */
public enum PaintColor {
	MAGENTA("magenta", "Magenta", 0xEA2C8E, Blocks.SCULK_VEIN, TeamColor.LIGHT_PURPLE, BossEvent.BossBarColor.PINK),
	LIME("lime", "Lime", 0x8DE800, Blocks.RESIN_CLUMP, TeamColor.GREEN, BossEvent.BossBarColor.GREEN),
	CYAN("cyan", "Cyan", 0x00D5F5, Blocks.GLOW_LICHEN, TeamColor.AQUA, BossEvent.BossBarColor.BLUE);

	public final String id;
	public final String displayName;
	public final int rgb;
	public final Block donor;
	public final TeamColor teamColor;
	public final BossEvent.BossBarColor barColor;

	PaintColor(String id, String displayName, int rgb, Block donor, TeamColor teamColor, BossEvent.BossBarColor barColor) {
		this.id = id;
		this.displayName = displayName;
		this.rgb = rgb;
		this.donor = donor;
		this.teamColor = teamColor;
		this.barColor = barColor;
	}

	/** Pack path of the donor's block texture, the file the splat replaces. */
	public String donorTexturePath() {
		return "assets/minecraft/textures/block/" + BuiltInRegistries.BLOCK.getKey(donor).getPath() + ".png";
	}

	public static Optional<PaintColor> byId(String id) {
		for (PaintColor color : values()) {
			if (color.id.equals(id)) return Optional.of(color);
		}
		return Optional.empty();
	}

	/** The colour of a vanilla scoreboard team, matched by team name; empty for no team or an unknown name. */
	public static Optional<PaintColor> byTeam(@Nullable PlayerTeam team) {
		return team == null ? Optional.empty() : byId(team.getName());
	}
}
```

- [ ] **Step 4: Write PaintBlock and PaintBlocks**

`src/main/java/nu/metacraft/rivals/paint/PaintBlock.java`:

```java
package nu.metacraft.rivals.paint;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;

/**
 * Paint of one colour. A vanilla multiface block on the server (six independent face flags, no
 * collision, faces drop off when their support goes), shown to clients as the colour's donor block
 * with the same faces. One block per cell, so one colour per cell.
 */
public final class PaintBlock extends MultifaceBlock implements PolymerBlock {
	public final PaintColor color;

	public PaintBlock(Properties properties, PaintColor color) {
		super(properties);
		this.color = color;
		verifyDonor();
	}

	/** Fail startup, not gameplay, if a donor ever stops being a multiface block. */
	private void verifyDonor() {
		BlockState donor = color.donor.defaultBlockState();
		for (Direction d : Direction.values()) {
			if (!donor.hasProperty(getFaceProperty(d))) {
				throw new IllegalStateException("[" + Rivals.MOD_ID + "] donor " + color.donor + " for paint colour "
						+ color.id + " has no " + d + " face property; it cannot show paint");
			}
		}
		if (!donor.hasProperty(WATERLOGGED)) {
			throw new IllegalStateException("[" + Rivals.MOD_ID + "] donor " + color.donor + " for paint colour "
					+ color.id + " has no waterlogged property");
		}
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		BlockState out = color.donor.defaultBlockState().setValue(WATERLOGGED, false);
		for (Direction d : Direction.values()) {
			BooleanProperty face = getFaceProperty(d);
			out = out.setValue(face, state.getValue(face));
		}
		return out;
	}
}
```

`src/main/java/nu/metacraft/rivals/paint/PaintBlocks.java`:

```java
package nu.metacraft.rivals.paint;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;

import java.util.EnumMap;
import java.util.Map;

/** Registers one {@link PaintBlock} per colour as {@code metacraft-rivals:paint_<id>}. No items. */
public final class PaintBlocks {
	private static final Map<PaintColor, PaintBlock> BLOCKS = new EnumMap<>(PaintColor.class);

	private PaintBlocks() {}

	public static PaintBlock of(PaintColor color) {
		return BLOCKS.get(color);
	}

	public static void register() {
		for (PaintColor color : PaintColor.values()) {
			Identifier id = Rivals.id("paint_" + color.id);
			BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
					.noCollision()
					.noOcclusion()
					.instabreak()
					.noLootTable()
					.pushReaction(PushReaction.DESTROY)
					.setId(ResourceKey.create(Registries.BLOCK, id));
			BLOCKS.put(color, Registry.register(BuiltInRegistries.BLOCK, id, new PaintBlock(properties, color)));
		}
	}
}
```

In `Rivals.onInitialize()`, before the log line, add `PaintBlocks.register();` (import `nu.metacraft.rivals.paint.PaintBlocks`).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew --offline mods:metacraft-rivals:runGameTest`
Expected: BUILD SUCCESSFUL, 2 tests passed (`modLoads`, `donorMappingKeepsFaces`).

- [ ] **Step 6: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: paint colours and paint blocks mapped to multiface donors"
```

---

### Task 3: Splat texture and resource pack wiring

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/pack/SplatTexture.java`
- Create: `src/main/java/nu/metacraft/rivals/pack/RivalsPack.java`
- Modify: `src/main/java/nu/metacraft/rivals/Rivals.java` (call `RivalsPack.init()`)
- Test: `src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java`

**Interfaces:**
- Produces: `static byte[] SplatTexture.png(int rgb, int seed)` (16×16 RGBA PNG bytes) and `static final int SplatTexture.SIZE = 16`.
- Produces: `RivalsPack.init()`: registers mod assets, marks the pack required, adds one splat PNG per colour at `PaintColor.donorTexturePath()`.

- [ ] **Step 1: Write the failing test**

Add to `RivalsGameTests` (imports: `nu.metacraft.rivals.pack.SplatTexture`, `javax.imageio.ImageIO`, `java.awt.image.BufferedImage`, `java.io.ByteArrayInputStream`, `java.io.IOException`):

```java
	/** The generated splat is a 16×16 PNG: transparent outside the blob, the colour inside. */
	@GameTest
	public void splatTextureIsColouredBlob(GameTestHelper helper) throws IOException {
		int rgb = 0xEA2C8E;
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(SplatTexture.png(rgb, 0)));
		helper.assertTrue(image != null, "PNG decodes");
		helper.assertValueEqual(image.getWidth(), SplatTexture.SIZE, "width");
		helper.assertValueEqual(image.getHeight(), SplatTexture.SIZE, "height");
		int opaque = 0;
		int transparent = 0;
		int exactColour = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha == 0) transparent++;
				else if (alpha == 0xFF) opaque++;
				if (argb == (0xFF000000 | rgb)) exactColour++;
			}
		}
		helper.assertTrue(opaque > 0, "has opaque pixels");
		helper.assertTrue(transparent > 0, "has transparent pixels");
		helper.assertTrue(opaque + transparent == SplatTexture.SIZE * SplatTexture.SIZE, "no half-transparent pixels");
		helper.assertTrue(exactColour > 0, "fill pixels are the exact colour");
		helper.succeed();
	}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew --offline mods:metacraft-rivals:compileJava`
Expected: compilation FAILS with "package nu.metacraft.rivals.pack does not exist".

- [ ] **Step 3: Write SplatTexture**

`src/main/java/nu/metacraft/rivals/pack/SplatTexture.java`:

```java
package nu.metacraft.rivals.pack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

/**
 * A procedural 16×16 paint splat: a few overlapping discs plus stray drips, filled with the colour and
 * rimmed one texel darker. Deterministic per seed so every server build produces the same pack.
 * Replacing it with real art is dropping a PNG at the donor texture path instead.
 */
public final class SplatTexture {
	public static final int SIZE = 16;

	private SplatTexture() {}

	public static byte[] png(int rgb, int seed) {
		boolean[][] mask = mask(seed);
		int fill = 0xFF000000 | (rgb & 0xFFFFFF);
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int rim = 0xFF000000 | ((r * 3 / 4) << 16) | ((g * 3 / 4) << 8) | (b * 3 / 4);
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				if (!mask[y][x]) continue;
				image.setRGB(x, y, isRim(mask, x, y) ? rim : fill);
			}
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(image, "png", out)) throw new IOException("no PNG writer available");
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("could not encode the splat texture", e);
		}
	}

	/** Union of three or four discs of radius 3 to 6 near the centre, plus four single-texel drips. */
	static boolean[][] mask(int seed) {
		Random random = new Random(0x5EED1234L + seed * 7919L);
		boolean[][] mask = new boolean[SIZE][SIZE];
		int discs = 3 + random.nextInt(2);
		for (int i = 0; i < discs; i++) {
			double cx = 4 + random.nextDouble() * 8;
			double cy = 4 + random.nextDouble() * 8;
			double radius = 3 + random.nextDouble() * 3;
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					double dx = x + 0.5 - cx;
					double dy = y + 0.5 - cy;
					if (dx * dx + dy * dy <= radius * radius) mask[y][x] = true;
				}
			}
		}
		for (int i = 0; i < 4; i++) {
			mask[random.nextInt(SIZE)][random.nextInt(SIZE)] = true;
		}
		return mask;
	}

	private static boolean isRim(boolean[][] mask, int x, int y) {
		return !inside(mask, x - 1, y) || !inside(mask, x + 1, y) || !inside(mask, x, y - 1) || !inside(mask, x, y + 1);
	}

	private static boolean inside(boolean[][] mask, int x, int y) {
		return x >= 0 && y >= 0 && x < SIZE && y < SIZE && mask[y][x];
	}
}
```

- [ ] **Step 4: Write RivalsPack and wire it**

`src/main/java/nu/metacraft/rivals/pack/RivalsPack.java`:

```java
package nu.metacraft.rivals.pack;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;

/**
 * The resource pack: the mod's own assets (gun model, palette, lang) plus one generated splat texture
 * per colour written over the donor block's vanilla texture. Required, because without it players see
 * sculk veins and resin clumps where the paint is.
 */
public final class RivalsPack {
	private RivalsPack() {}

	public static void init() {
		PolymerResourcePackUtils.addModAssets(Rivals.MOD_ID);
		PolymerResourcePackUtils.markAsRequired();
		PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> {
			for (PaintColor color : PaintColor.values()) {
				builder.addData(color.donorTexturePath(), SplatTexture.png(color.rgb, color.ordinal()));
				Rivals.LOGGER.info("[{}] pack: {} splat over {}", Rivals.MOD_ID, color.id, color.donorTexturePath());
			}
		});
	}
}
```

In `Rivals.onInitialize()`, after `PaintBlocks.register();` add `RivalsPack.init();` (import `nu.metacraft.rivals.pack.RivalsPack`).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew --offline mods:metacraft-rivals:runGameTest`
Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 6: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: generated splat textures over the donor blocks in the required pack"
```

---

### Task 4: Painter and tally

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/paint/Painter.java`
- Create: `src/main/java/nu/metacraft/rivals/paint/PaintTally.java`
- Test: `src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java`

**Interfaces:**
- Consumes: `PaintBlocks.of(PaintColor)`, `PaintBlock.color` (Task 2).
- Produces: `static int Painter.splat(ServerLevel level, BlockPos struck, Direction face, PaintColor color, RandomSource random)` returning the number of faces newly painted; `static boolean Painter.paintFace(ServerLevel level, BlockPos surface, Direction face, PaintColor color)`; `static final int Painter.RADIUS = 1`.
- Produces: `PaintTally` with `static PaintTally of(ServerLevel)`, `static void clearAll()`, `void track(BlockPos)`, `int cells()`, `Map<PaintColor, Integer> count(ServerLevel)`, `static float share(Map<PaintColor, Integer>, PaintColor)`, `int reset(ServerLevel)`. The constructor is public so tests can use a private instance.

- [ ] **Step 1: Write the failing tests**

Add to `RivalsGameTests` (imports: `nu.metacraft.rivals.paint.Painter`, `nu.metacraft.rivals.paint.PaintTally`, `net.minecraft.core.BlockPos`, `net.minecraft.world.level.block.Blocks`, `java.util.Map`):

```java
	/** Stone floor at relative y=1 over x,z in [0,size). */
	private static void stoneFloor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	private static int faces(BlockState state) {
		int n = 0;
		for (Direction d : Direction.values()) {
			if (state.getValue(MultifaceBlock.getFaceProperty(d))) n++;
		}
		return n;
	}

	/** A splat on the top of a floor block paints the cell above it, on its down face, in that colour. */
	@GameTest
	public void floorSplatPaintsCellAbove(GameTestHelper helper) {
		stoneFloor(helper, 5);
		BlockPos struck = new BlockPos(2, 1, 2);
		int painted = Painter.splat(helper.getLevel(), helper.absolutePos(struck), Direction.UP, PaintColor.MAGENTA,
				helper.getLevel().getRandom());
		helper.assertTrue(painted >= 1 && painted <= 9, "painted " + painted + " faces, expected 1..9");
		BlockState cell = helper.getBlockState(struck.above());
		helper.assertTrue(cell.is(PaintBlocks.of(PaintColor.MAGENTA)), Component.literal("cell above the hit is magenta paint, got " + cell));
		helper.assertTrue(cell.getValue(MultifaceBlock.getFaceProperty(Direction.DOWN)), "paint sits on its down face");
		helper.succeed();
	}

	/** The blob stays within one block of the hit in the plane, and never where the surface is missing. */
	@GameTest
	public void blobStaysWithinRadiusAndOnSurfaces(GameTestHelper helper) {
		stoneFloor(helper, 5);
		helper.setBlock(new BlockPos(1, 1, 2), Blocks.AIR); // a hole beside the hit, not a corner
		Painter.splat(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, PaintColor.LIME,
				helper.getLevel().getRandom());
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				BlockState cell = helper.getBlockState(new BlockPos(x, 2, z));
				boolean inBlob = Math.abs(x - 2) <= Painter.RADIUS && Math.abs(z - 2) <= Painter.RADIUS;
				boolean overHole = x == 1 && z == 2;
				if (!inBlob || overHole) {
					helper.assertTrue(cell.isAir(), Component.literal("no paint expected at " + x + "," + z + " but found " + cell));
				}
			}
		}
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).is(PaintBlocks.of(PaintColor.LIME)), "centre is painted");
		helper.succeed();
	}

	/** A hit in another colour recolours the whole cell and keeps its faces. */
	@GameTest
	public void otherColourRecoloursCellKeepingFaces(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE); // floor under the cell
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE); // wall north of the cell
		BlockPos cell = new BlockPos(2, 2, 2);
		helper.setBlock(cell, PaintBlocks.of(PaintColor.MAGENTA).defaultBlockState()
				.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
				.setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true));
		boolean painted = Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, PaintColor.LIME);
		helper.assertTrue(painted, "the cell counts as newly painted");
		BlockState after = helper.getBlockState(cell);
		helper.assertTrue(after.is(PaintBlocks.of(PaintColor.LIME)), Component.literal("cell is lime now, got " + after));
		helper.assertTrue(after.getValue(MultifaceBlock.getFaceProperty(Direction.DOWN)), "down face kept");
		helper.assertTrue(after.getValue(MultifaceBlock.getFaceProperty(Direction.NORTH)), "north face kept");
		helper.assertValueEqual(faces(after), 2, "face count");
		helper.succeed();
	}

	/** The tally counts faces per colour from the cells it tracks, and reset removes them. */
	@GameTest
	public void tallyCountsFacesAndResets(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
		BlockPos magentaCell = new BlockPos(2, 2, 2);
		BlockPos limeCell = new BlockPos(4, 2, 4);
		helper.setBlock(magentaCell, PaintBlocks.of(PaintColor.MAGENTA).defaultBlockState()
				.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
				.setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true));
		helper.setBlock(limeCell, PaintBlocks.of(PaintColor.LIME).defaultBlockState()
				.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true));
		PaintTally tally = new PaintTally();
		tally.track(helper.absolutePos(magentaCell));
		tally.track(helper.absolutePos(limeCell));
		tally.track(helper.absolutePos(new BlockPos(0, 5, 0))); // air: must be dropped, not counted
		Map<PaintColor, Integer> counts = tally.count(helper.getLevel());
		helper.assertValueEqual(counts.get(PaintColor.MAGENTA), 2, "magenta faces");
		helper.assertValueEqual(counts.get(PaintColor.LIME), 1, "lime faces");
		helper.assertValueEqual(counts.get(PaintColor.CYAN), 0, "cyan faces");
		helper.assertValueEqual(tally.cells(), 2, "the air cell was dropped");
		helper.assertTrue(Math.abs(PaintTally.share(counts, PaintColor.LIME) - 1f / 3f) < 1e-6, "lime share is a third");
		int removed = tally.reset(helper.getLevel());
		helper.assertValueEqual(removed, 2, "reset removed both cells");
		helper.assertTrue(helper.getBlockState(magentaCell).isAir() && helper.getBlockState(limeCell).isAir(), "cells are air after reset");
		helper.assertValueEqual(tally.count(helper.getLevel()).get(PaintColor.MAGENTA), 0, "nothing left to count");
		helper.succeed();
	}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew --offline mods:metacraft-rivals:compileJava`
Expected: compilation FAILS with "cannot find symbol: class Painter" (and PaintTally).

- [ ] **Step 3: Write PaintTally**

`src/main/java/nu/metacraft/rivals/paint/PaintTally.java`:

```java
package nu.metacraft.rivals.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.rivals.PaintColor;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Which cells the painter has painted, per level, and how many faces of each colour they hold now.
 *
 * <p>No deltas: {@link #count} reads every tracked cell's current state and drops cells that no longer
 * hold paint (removed by a player, by {@code updateShape} when the support went, or by reset). Vanilla
 * only calls {@code onPlace} when the block changes, so a face added to a same-colour cell would be
 * invisible to hooks; walking a few thousand positions once a second is cheap and cannot drift.
 * In memory only: a restart forgets the cells until paint is shot again.
 */
public final class PaintTally {
	private static final Map<ResourceKey<Level>, PaintTally> TALLIES = new HashMap<>();

	private final Set<BlockPos> cells = new HashSet<>();

	public static PaintTally of(ServerLevel level) {
		return TALLIES.computeIfAbsent(level.dimension(), key -> new PaintTally());
	}

	/** Forget every level's cells (server stop). */
	public static void clearAll() {
		TALLIES.clear();
	}

	public void track(BlockPos cell) {
		cells.add(cell.immutable());
	}

	public int cells() {
		return cells.size();
	}

	/** Faces per colour over the tracked cells, pruning cells that hold no paint any more. Every colour has an entry. */
	public Map<PaintColor, Integer> count(ServerLevel level) {
		Map<PaintColor, Integer> counts = new EnumMap<>(PaintColor.class);
		for (PaintColor color : PaintColor.values()) counts.put(color, 0);
		Iterator<BlockPos> it = cells.iterator();
		while (it.hasNext()) {
			BlockPos pos = it.next();
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof PaintBlock paint)) {
				it.remove();
				continue;
			}
			int faces = 0;
			for (Direction d : Direction.values()) {
				if (state.getValue(MultifaceBlock.getFaceProperty(d))) faces++;
			}
			counts.merge(paint.color, faces, Integer::sum);
		}
		return counts;
	}

	/** This colour's fraction of all painted faces; 0 when nothing is painted. */
	public static float share(Map<PaintColor, Integer> counts, PaintColor color) {
		int total = 0;
		for (int n : counts.values()) total += n;
		return total == 0 ? 0f : counts.getOrDefault(color, 0) / (float) total;
	}

	/** Remove every tracked paint block from the level and forget the cells. Returns how many were removed. */
	public int reset(ServerLevel level) {
		int removed = 0;
		for (BlockPos pos : cells) {
			if (level.getBlockState(pos).getBlock() instanceof PaintBlock) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				removed++;
			}
		}
		cells.clear();
		return removed;
	}
}
```

- [ ] **Step 4: Write Painter**

`src/main/java/nu/metacraft/rivals/paint/Painter.java`:

```java
package nu.metacraft.rivals.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import nu.metacraft.rivals.PaintColor;

/**
 * Where a hit puts paint. The struck block is the surface; the paint lives in the cell in front of the
 * struck face, as a paint block whose face flag points back at the surface. A splat covers the 3×3 of
 * surface blocks around the hit in the plane of the face, corners dropped at random.
 */
public final class Painter {
	public static final int RADIUS = 1;

	private Painter() {}

	/** Paint a blob around {@code struck}'s {@code face}. Returns how many faces are newly this colour. */
	public static int splat(ServerLevel level, BlockPos struck, Direction face, PaintColor color, RandomSource random) {
		int painted = 0;
		for (int a = -RADIUS; a <= RADIUS; a++) {
			for (int b = -RADIUS; b <= RADIUS; b++) {
				boolean corner = Math.abs(a) == RADIUS && Math.abs(b) == RADIUS;
				if (corner && random.nextBoolean()) continue;
				if (paintFace(level, offsetInPlane(struck, face.getAxis(), a, b), face, color)) painted++;
			}
		}
		return painted;
	}

	/** Offset {@code origin} by (a, b) within the plane perpendicular to {@code normal}. */
	static BlockPos offsetInPlane(BlockPos origin, Direction.Axis normal, int a, int b) {
		return switch (normal) {
			case Y -> origin.offset(a, 0, b);
			case X -> origin.offset(0, a, b);
			case Z -> origin.offset(a, b, 0);
		};
	}

	/**
	 * Paint one face: the {@code face} side of the block at {@code surface}. The cell in front must be air
	 * or paint, and the surface must be something a multiface block can attach to. An air cell becomes
	 * this colour with that face; a same-colour cell gains the face; another colour's cell is recoloured
	 * whole, keeping its faces. Returns whether anything changed.
	 */
	public static boolean paintFace(ServerLevel level, BlockPos surface, Direction face, PaintColor color) {
		BlockPos cell = surface.relative(face);
		Direction attach = face.getOpposite();
		BooleanProperty attachFace = MultifaceBlock.getFaceProperty(attach);
		BlockState existing = level.getBlockState(cell);
		BlockState next;
		if (existing.isAir()) {
			next = PaintBlocks.of(color).defaultBlockState().setValue(attachFace, true);
		} else if (existing.getBlock() instanceof PaintBlock paint) {
			if (paint.color == color && existing.getValue(attachFace)) return false;
			next = PaintBlocks.of(color).defaultBlockState();
			for (Direction d : Direction.values()) {
				BooleanProperty property = MultifaceBlock.getFaceProperty(d);
				next = next.setValue(property, existing.getValue(property));
			}
			next = next.setValue(attachFace, true);
		} else {
			return false;
		}
		if (!MultifaceBlock.canAttachTo(level, attach, surface, level.getBlockState(surface))) return false;
		level.setBlock(cell, next, Block.UPDATE_ALL);
		PaintTally.of(level).track(cell);
		return true;
	}
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew --offline mods:metacraft-rivals:runGameTest`
Expected: BUILD SUCCESSFUL, 7 tests passed.

- [ ] **Step 6: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: painter blob geometry and the per-level paint tally"
```

---

### Task 5: Paint ball, paint gun, gun assets

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/gun/PaintBall.java`
- Create: `src/main/java/nu/metacraft/rivals/gun/PaintGun.java`
- Create: `src/main/resources/assets/metacraft-rivals/items/paint_gun.json`
- Create: `src/main/resources/assets/metacraft-rivals/lang/en_us.json`
- Existing (already committed): `src/main/resources/assets/metacraft-rivals/models/item/paint_gun.json`, `src/main/resources/assets/metacraft-rivals/textures/item/paint_gun_palette.png`
- Modify: `src/main/java/nu/metacraft/rivals/Rivals.java` (call `PaintBall.register()` and `PaintGun.register()` before `RivalsPack.init()`)
- Test: `src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java`

**Interfaces:**
- Consumes: `Painter.splat(...)` (Task 4), `PaintColor.byTeam(...)` (Task 2).
- Produces: `PaintBall extends Snowball implements PolymerEntity` with `static final EntityType<PaintBall> TYPE`, `static void register()`, constructor `PaintBall(ServerLevel level, LivingEntity shooter, PaintColor color)`, `PaintColor color()`, `static ItemStack blob(PaintColor)`.
- Produces: `PaintGun extends Item implements PolymerItem` with `static final Identifier ID`, `static PaintGun ITEM`, `static void register()`, `static PaintBall shoot(ServerLevel, LivingEntity, PaintColor)`, `static ItemStack withTankColor(ItemStack clientStack, @Nullable PlayerTeam team)`, constants `COOLDOWN_TICKS = 4`, `VELOCITY = 1.5f`, `INACCURACY = 1.0f`.

- [ ] **Step 1: Write the failing tests**

Add to `RivalsGameTests` (imports: `nu.metacraft.rivals.Rivals`, `nu.metacraft.rivals.gun.PaintBall`, `nu.metacraft.rivals.gun.PaintGun`, `com.google.gson.JsonArray`, `com.google.gson.JsonElement`, `com.google.gson.JsonObject`, `com.google.gson.JsonParser`, `net.minecraft.core.component.DataComponents`, `net.minecraft.server.ServerScoreboard`, `net.minecraft.world.InteractionHand`, `net.minecraft.world.InteractionResult`, `net.minecraft.world.entity.Entity`, `net.minecraft.world.entity.player.Player`, `net.minecraft.world.item.ItemStack`, `net.minecraft.world.item.Items`, `net.minecraft.world.item.component.DyedItemColor`, `net.minecraft.world.item.component.FireworkExplosion`, `net.minecraft.world.level.GameType`, `net.minecraft.world.phys.Vec3`, `net.minecraft.world.scores.PlayerTeam`, `java.io.InputStream`, `java.io.InputStreamReader`, `java.nio.charset.StandardCharsets`, `java.util.List`):

```java
	private static PlayerTeam team(GameTestHelper helper, PaintColor color) {
		ServerScoreboard board = helper.getLevel().getScoreboard();
		PlayerTeam team = board.getPlayerTeam(color.id);
		return team != null ? team : board.addPlayerTeam(color.id);
	}

	/** A mock survival player holding a gun, standing at relative (4, 3, 4). */
	private static Player gunner(GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(4, 3, 4));
		player.setPos(at.x, at.y, at.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintGun.ITEM));
		return player;
	}

	/** Without a team the gun refuses: no projectile, no cooldown. */
	@GameTest
	public void gunWithoutTeamDoesNotShoot(GameTestHelper helper) {
		Player player = gunner(helper);
		InteractionResult result = PaintGun.ITEM.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.FAIL, "use fails without a team");
		helper.assertTrue(helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0).isEmpty(), "no paint ball spawned");
		helper.assertTrue(!player.getCooldowns().isOnCooldown(player.getItemInHand(InteractionHand.MAIN_HAND)), "no cooldown");
		helper.succeed();
	}

	/** On a team the gun throws one paint ball carrying a firework star in the team colour, and starts the cooldown. */
	@GameTest
	public void gunOnTeamThrowsColouredBall(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.MAGENTA));
		InteractionResult result = PaintGun.ITEM.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS, "use succeeds on a team");
		List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0);
		helper.assertValueEqual(balls.size(), 1, "one paint ball");
		PaintBall ball = balls.getFirst();
		helper.assertTrue(ball.color() == PaintColor.MAGENTA, "ball is magenta");
		ItemStack shown = ball.getItem();
		helper.assertTrue(shown.is(Items.FIREWORK_STAR), Component.literal("ball shows a firework star, got " + shown));
		FireworkExplosion explosion = shown.get(DataComponents.FIREWORK_EXPLOSION);
		helper.assertTrue(explosion != null && explosion.colors().contains(PaintColor.MAGENTA.rgb), "star is tinted magenta");
		helper.assertTrue(player.getCooldowns().isOnCooldown(player.getItemInHand(InteractionHand.MAIN_HAND)), "cooldown started");
		balls.forEach(Entity::discard);
		helper.succeed();
	}

	/** The client-side gun stack carries the team colour as a dye, and nothing without a team. */
	@GameTest
	public void gunTankTakesTeamColour(GameTestHelper helper) {
		ItemStack onTeam = PaintGun.withTankColor(new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK), team(helper, PaintColor.LIME));
		DyedItemColor dye = onTeam.get(DataComponents.DYED_COLOR);
		helper.assertTrue(dye != null && dye.rgb() == PaintColor.LIME.rgb, "tank dyed lime");
		ItemStack noTeam = PaintGun.withTankColor(new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK), null);
		helper.assertTrue(noTeam.get(DataComponents.DYED_COLOR) == null, "no dye without a team");
		helper.succeed();
	}

	/** The gun's item definition, model and palette ship in the jar, and the model stays inside the item bounds. */
	@GameTest
	public void gunModelAssetsArePresent(GameTestHelper helper) throws IOException {
		String base = "/assets/" + Rivals.MOD_ID + "/";
		for (String path : new String[] {"items/paint_gun.json", "models/item/paint_gun.json", "textures/item/paint_gun_palette.png"}) {
			try (InputStream in = Rivals.class.getResourceAsStream(base + path)) {
				helper.assertTrue(in != null, "asset present: " + path);
			}
		}
		try (InputStream in = Rivals.class.getResourceAsStream(base + "models/item/paint_gun.json")) {
			JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray elements = model.getAsJsonArray("elements");
			helper.assertTrue(elements.size() >= 5, "model has elements");
			boolean tinted = false;
			for (JsonElement e : elements) {
				JsonObject box = e.getAsJsonObject();
				for (String key : new String[] {"from", "to"}) {
					for (JsonElement v : box.getAsJsonArray(key)) {
						double d = v.getAsDouble();
						helper.assertTrue(d >= -16 && d <= 32, "element coordinate in range: " + d);
					}
				}
				for (var face : box.getAsJsonObject("faces").entrySet()) {
					if (face.getValue().getAsJsonObject().has("tintindex")) tinted = true;
				}
			}
			helper.assertTrue(tinted, "some faces are tinted (the tank)");
		}
		try (InputStream in = Rivals.class.getResourceAsStream(base + "items/paint_gun.json")) {
			JsonObject definition = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject modelDef = definition.getAsJsonObject("model");
			helper.assertValueEqual(modelDef.get("model").getAsString(), Rivals.MOD_ID + ":item/paint_gun", "definition points at the model");
			helper.assertValueEqual(modelDef.getAsJsonArray("tints").get(0).getAsJsonObject().get("type").getAsString(), "minecraft:dye", "dye tint");
		}
		helper.succeed();
	}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew --offline mods:metacraft-rivals:compileJava`
Expected: compilation FAILS with "package nu.metacraft.rivals.gun does not exist".

- [ ] **Step 3: Write PaintBall**

`src/main/java/nu/metacraft/rivals/gun/PaintBall.java`:

```java
package nu.metacraft.rivals.gun;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;
import nu.metacraft.rivals.paint.Painter;

/**
 * The thrown blob. A snowball on the server (physics, hit detection, the break particles on impact)
 * that clients see as a snowball carrying a firework star tinted with the paint colour, so it is a
 * round coloured ball with no texture of its own. Paints on whatever it hits; never hurts anything.
 */
public final class PaintBall extends Snowball implements PolymerEntity {
	public static final EntityType<PaintBall> TYPE = EntityType.Builder.<PaintBall>of(PaintBall::new, MobCategory.MISC)
			.sized(0.25f, 0.25f)
			.clientTrackingRange(4)
			.updateInterval(10)
			.noSummon()
			.noSave()
			.build(ResourceKey.create(Registries.ENTITY_TYPE, Rivals.id("paint_ball")));

	private PaintColor color = PaintColor.MAGENTA;

	public PaintBall(EntityType<? extends Snowball> type, Level level) {
		super(type, level);
	}

	public PaintBall(ServerLevel level, LivingEntity shooter, PaintColor color) {
		super(TYPE, level);
		this.color = color;
		setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
		setOwner(shooter);
		setItem(blob(color));
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, Rivals.id("paint_ball"), TYPE);
		PolymerEntityUtils.registerType(TYPE);
	}

	public PaintColor color() {
		return color;
	}

	/** What clients see flying: a firework star whose explosion colour is the paint colour. */
	public static ItemStack blob(PaintColor color) {
		ItemStack stack = new ItemStack(Items.FIREWORK_STAR);
		stack.set(DataComponents.FIREWORK_EXPLOSION,
				new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(color.rgb), IntList.of(), false, false));
		return stack;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.FIREWORK_STAR;
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityType.SNOWBALL;
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (level() instanceof ServerLevel serverLevel) {
			Painter.splat(serverLevel, hit.getBlockPos(), hit.getDirection(), color, random);
		}
	}

	/** No damage (the snowball would hurt blazes); paint the ground under whoever was hit. */
	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (level() instanceof ServerLevel serverLevel) {
			BlockPos below = hit.getEntity().blockPosition().below();
			Painter.splat(serverLevel, below, Direction.UP, color, random);
		}
	}
}
```

- [ ] **Step 4: Write PaintGun**

`src/main/java/nu/metacraft/rivals/gun/PaintGun.java`:

```java
package nu.metacraft.rivals.gun;

import com.mojang.authlib.GameProfile;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The paint gun. Right-click throws a {@link PaintBall} in the colour of the holder's vanilla team;
 * no team, no shot. Vanilla clients keep sending use packets while the button is held, so the item
 * cooldown is the fire rate. Clients see a warped fungus on a stick wearing our 3D model; the model's
 * tank is dye-tinted, and the client-side stack carries the team colour as that dye.
 */
public final class PaintGun extends Item implements PolymerItem {
	public static final Identifier ID = Rivals.id("paint_gun");
	public static final int COOLDOWN_TICKS = 4;
	public static final float VELOCITY = 1.5f;
	public static final float INACCURACY = 1.0f;
	public static PaintGun ITEM;

	public PaintGun(Properties properties) {
		super(properties);
	}

	public static void register() {
		ITEM = Registry.register(BuiltInRegistries.ITEM, ID,
				new PaintGun(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, ID))));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
		Optional<PaintColor> color = PaintColor.byTeam(player.getTeam());
		if (color.isEmpty()) {
			// The connection is null for the mock players game tests make; they get no message.
			if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
				serverPlayer.sendSystemMessage(Component.literal("Join a team first: /team join magenta").withStyle(ChatFormatting.RED), true);
			}
			return InteractionResult.FAIL;
		}
		shoot(serverLevel, player, color.get());
		player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}

	/** Throw one paint ball from the shooter's eyes along their view. */
	public static PaintBall shoot(ServerLevel level, LivingEntity shooter, PaintColor color) {
		PaintBall ball = new PaintBall(level, shooter, color);
		ball.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0.0f, VELOCITY, INACCURACY);
		level.addFreshEntity(ball);
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.8f);
		return ball;
	}

	/** Dye the client-side stack with the team's paint colour (the model's tank reads it); leave it undyed otherwise. */
	public static ItemStack withTankColor(ItemStack clientStack, @Nullable PlayerTeam team) {
		PaintColor.byTeam(team).ifPresent(color -> clientStack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.rgb)));
		return clientStack;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.WARPED_FUNGUS_ON_A_STICK;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return ID;
	}

	@Override
	public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext context, HolderLookup.Provider lookup) {
		ItemStack out = PolymerItem.super.getPolymerItemStack(stack, flag, context, lookup);
		ServerPlayer holder = holder(context);
		return withTankColor(out, holder == null ? null : holder.getTeam());
	}

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		tooltip.add(Component.literal("Shoots paint in your team's colour").withStyle(ChatFormatting.GRAY));
	}

	/** The player a stack is being sent to, if the packet context knows one. */
	private static @Nullable ServerPlayer holder(@Nullable PacketContext context) {
		if (context == null) return null;
		MinecraftServer server = context.get(PacketContext.SERVER_INSTANCE);
		GameProfile profile = context.get(PacketContext.GAME_PROFILE);
		if (server == null || profile == null) return null;
		return server.getPlayerList().getPlayer(profile.id());
	}
}
```

- [ ] **Step 5: Add the item definition and lang file, wire registration**

`src/main/resources/assets/metacraft-rivals/items/paint_gun.json`:

```json
{
	"model": {
		"type": "minecraft:model",
		"model": "metacraft-rivals:item/paint_gun",
		"tints": [
			{
				"type": "minecraft:dye",
				"default": 16777215
			}
		]
	}
}
```

`src/main/resources/assets/metacraft-rivals/lang/en_us.json`:

```json
{
	"item.metacraft-rivals.paint_gun": "Paint Gun",
	"block.metacraft-rivals.paint_magenta": "Magenta Paint",
	"block.metacraft-rivals.paint_lime": "Lime Paint",
	"block.metacraft-rivals.paint_cyan": "Cyan Paint",
	"entity.metacraft-rivals.paint_ball": "Paint Ball"
}
```

In `Rivals.onInitialize()` the order becomes:

```java
		PaintBlocks.register();
		PaintBall.register();
		PaintGun.register();
		RivalsPack.init();
```

(imports `nu.metacraft.rivals.gun.PaintBall`, `nu.metacraft.rivals.gun.PaintGun`).

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew --offline mods:metacraft-rivals:runGameTest`
Expected: BUILD SUCCESSFUL, 11 tests passed.

If `gunOnTeamThrowsColouredBall` fails on `result == InteractionResult.SUCCESS`, print the actual result in the message and check whether the mock player's `getTeam()` sees the team (it reads `level().getScoreboard().getPlayersTeam(getScoreboardName())`); the fix belongs in the test's team setup, not in the gun.

- [ ] **Step 7: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: paint gun and paint ball, tank dyed to the team colour"
```

---

### Task 6: Commands and score bossbars

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/RivalsCommands.java`
- Create: `src/main/java/nu/metacraft/rivals/ScoreBars.java`
- Modify: `src/main/java/nu/metacraft/rivals/Rivals.java` (call `RivalsCommands.register()` and `ScoreBars.init()` after `RivalsPack.init()`)
- Test: `src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java`

**Interfaces:**
- Consumes: `PaintTally.of/count/share/reset/clearAll` (Task 4), `PaintGun.ITEM` (Task 5), `PaintColor` fields (Task 2).
- Produces: `static int RivalsCommands.setupTeams(MinecraftServer)` (returns the number of teams touched) used by the command and the test; `ScoreBars.init()`.

- [ ] **Step 1: Write the failing test**

Add to `RivalsGameTests` (imports: `nu.metacraft.rivals.RivalsCommands`, `net.minecraft.world.scores.Team`, `java.util.Optional`):

```java
	/** Setup creates one vanilla team per colour with the matching colour, no friendly fire, no collisions. */
	@GameTest
	public void setupCreatesTeams(GameTestHelper helper) {
		int touched = RivalsCommands.setupTeams(helper.getLevel().getServer());
		helper.assertValueEqual(touched, PaintColor.values().length, "teams touched");
		ServerScoreboard board = helper.getLevel().getScoreboard();
		for (PaintColor color : PaintColor.values()) {
			PlayerTeam team = board.getPlayerTeam(color.id);
			helper.assertTrue(team != null, "team exists: " + color.id);
			helper.assertTrue(team.getColor().equals(Optional.of(color.teamColor)), "team colour: " + color.id);
			helper.assertTrue(!team.isAllowFriendlyFire(), "friendly fire off: " + color.id);
			helper.assertTrue(team.getCollisionRule() == Team.CollisionRule.NEVER, "no collisions: " + color.id);
		}
		helper.succeed();
	}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew --offline mods:metacraft-rivals:compileJava`
Expected: compilation FAILS with "cannot find symbol: class RivalsCommands".

- [ ] **Step 3: Write RivalsCommands**

`src/main/java/nu/metacraft/rivals/RivalsCommands.java`:

```java
package nu.metacraft.rivals;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import nu.metacraft.rivals.gun.PaintGun;
import nu.metacraft.rivals.paint.PaintTally;

import java.util.Map;
import java.util.Optional;

import static net.minecraft.commands.Commands.literal;

/** {@code /rivals setup | gun | score | reset}, for game masters (permission {@code metacraft.rivals}). */
public final class RivalsCommands {
	private RivalsCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				literal("rivals")
						.requires(source -> Permissions.check(source, "metacraft.rivals", PermissionLevel.GAMEMASTERS))
						.then(literal("setup").executes(ctx -> {
							int touched = setupTeams(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal("Teams ready: magenta, lime, cyan. Join with /team join <colour> @s"), true);
							return touched;
						}))
						.then(literal("gun").executes(ctx -> gun(ctx.getSource())))
						.then(literal("score").executes(ctx -> score(ctx.getSource())))
						.then(literal("reset").executes(ctx -> reset(ctx.getSource())))));
	}

	/** Create or update one vanilla team per colour. Returns the number of teams touched. */
	public static int setupTeams(MinecraftServer server) {
		ServerScoreboard board = server.getScoreboard();
		int touched = 0;
		for (PaintColor color : PaintColor.values()) {
			PlayerTeam team = board.getPlayerTeam(color.id);
			if (team == null) team = board.addPlayerTeam(color.id);
			team.setDisplayName(Component.literal(color.displayName));
			team.setColor(Optional.of(color.teamColor));
			team.setAllowFriendlyFire(false);
			team.setCollisionRule(Team.CollisionRule.NEVER);
			touched++;
		}
		return touched;
	}

	private static int gun(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack gun = new ItemStack(PaintGun.ITEM);
		if (!player.getInventory().add(gun)) player.drop(gun, false);
		source.sendSuccess(() -> Component.literal("Here is a paint gun. Right-click to shoot; join a team for colour."), false);
		return 1;
	}

	private static int score(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Map<PaintColor, Integer> counts = PaintTally.of(level).count(level);
		int total = 0;
		for (int n : counts.values()) total += n;
		if (total == 0) {
			source.sendSuccess(() -> Component.literal("Nothing painted"), false);
			return 0;
		}
		for (PaintColor color : PaintColor.values()) {
			int faces = counts.get(color);
			int percent = Math.round(PaintTally.share(counts, color) * 100);
			source.sendSuccess(() -> Component.literal(color.displayName + ": " + faces + " faces, " + percent + " %")
					.withStyle(style -> style.withColor(color.teamColor.textColor())), false);
		}
		return total;
	}

	private static int reset(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		int removed = PaintTally.of(level).reset(level);
		if (removed == 0) {
			source.sendSuccess(() -> Component.literal("Nothing painted"), false);
		} else {
			source.sendSuccess(() -> Component.literal("Removed " + removed + " paint blocks").withStyle(ChatFormatting.YELLOW), true);
		}
		return removed;
	}
}
```

- [ ] **Step 4: Write ScoreBars**

`src/main/java/nu/metacraft/rivals/ScoreBars.java`:

```java
package nu.metacraft.rivals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import nu.metacraft.rivals.paint.PaintTally;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * One bossbar per colour that has been painted this session, showing that colour's share of all
 * paint in the overworld, refreshed once a second for every online player. Cleared on server stop,
 * together with the tallies.
 */
public final class ScoreBars {
	private static final int REFRESH_TICKS = 20;
	private static final Map<PaintColor, ServerBossEvent> BARS = new EnumMap<>(PaintColor.class);

	private ScoreBars() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_TICKS == 0) refresh(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
			PaintTally.clearAll();
		});
	}

	static void refresh(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Map<PaintColor, Integer> counts = PaintTally.of(level).count(level);
		for (PaintColor color : PaintColor.values()) {
			int faces = counts.get(color);
			if (faces == 0 && !BARS.containsKey(color)) continue;
			ServerBossEvent bar = BARS.computeIfAbsent(color, c -> new ServerBossEvent(UUID.randomUUID(),
					Component.literal(c.displayName), c.barColor, BossEvent.BossBarOverlay.PROGRESS));
			float share = PaintTally.share(counts, color);
			bar.setName(Component.literal(color.displayName + " " + Math.round(share * 100) + " %"));
			bar.setProgress(share);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				bar.addPlayer(player);
			}
		}
	}
}
```

In `Rivals.onInitialize()`, after `RivalsPack.init();` add:

```java
		RivalsCommands.register();
		ScoreBars.init();
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew --offline mods:metacraft-rivals:runGameTest`
Expected: BUILD SUCCESSFUL, 12 tests passed.

- [ ] **Step 6: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: /rivals commands and per-colour score bossbars"
```

---

### Task 7: README, full build, and the visual check on a real server

**Files:**
- Create: `mods/metacraft-rivals/README.md`

- [ ] **Step 1: Write the README**

`mods/metacraft-rivals/README.md`:

```markdown
# Metacraft Rivals

METAmods module `mods/metacraft-rivals` (mod id `metacraft-rivals`). A Splatoon-style paint
prototype for vanilla clients: Fabric + [Polymer](https://polymer.pb4.eu), Minecraft 26.2, Java 25.
Players need only the auto-served resource pack. Design: `docs/superpowers/specs/2026-09-11-metacraft-rivals-paint-prototype-design.md`;
the gun's design sheet is next to it.

**Standalone.** This module is not bundled into the `dist` jar (root `build.gradle`, `standaloneMods`):
its pack retextures sculk vein, resin clump and glow lichen as paint, which only a dedicated
Rivals server wants.

## How it works

- Paint is a server-side multiface block per colour (`metacraft-rivals:paint_<colour>`), sent to
  clients as the colour's donor: magenta → sculk vein, lime → resin clump, cyan → glow lichen (this
  one glows). The pack replaces the donors' textures with a splat generated at pack build.
- One colour per cell: a hit in another colour recolours the cell and keeps its faces.
- The paint gun throws a snowball-based paint ball shown as a tinted firework star; on impact it
  paints a 3×3 blob (corners at random) on the struck face. Colour comes from the shooter's
  vanilla team, whose name is the colour id.
- The gun's 3D model is `assets/metacraft-rivals/models/item/paint_gun.json`; its tank is
  dye-tinted to the team colour. Re-render the design sheet after editing it:
  `python3 mods/metacraft-rivals/tools/gun_sheet.py <model.json> <out.svg>`.
- Score: bossbars show each colour's share of painted faces in the overworld, counted once a
  second from the cells the painter has touched (in memory; a restart forgets them).

## Play

```
/rivals setup            teams magenta, lime, cyan
/team join magenta @s
/rivals gun
/rivals score
/rivals reset
```

## Build, run, test

```
./gradlew mods:metacraft-rivals:build
./gradlew mods:metacraft-rivals:runServer      # run/ has eula, offline mode and the pack autohost enabled
./gradlew mods:metacraft-rivals:runGameTest    # server-side game tests
```

Join the dev server with a vanilla 26.2 client (offline mode) and accept the pack.

## Not yet

Arena bounds and a round loop; blobs wrapping onto walls; speed on own paint and damage on enemy
paint; a persistent tally; real splat art.
```

- [ ] **Step 2: Full build and tests**

Run: `./gradlew --offline mods:metacraft-rivals:build mods:metacraft-rivals:runGameTest dist:compileJava`
Expected: BUILD SUCCESSFUL, 12 game tests passed.

- [ ] **Step 3: Start the dev server and check the pack builds**

Run in the background (it keeps running): `./gradlew --offline mods:metacraft-rivals:runServer`
Expected in the log: `[metacraft-rivals] pack: magenta splat over assets/minecraft/textures/block/sculk_vein.png` (and lime, cyan), Polymer reporting the resource pack generated, and no exception. Stop the server with `stop` on its console (or kill the Gradle process) once seen.

- [ ] **Step 4: Visual check (manual, needs a vanilla 26.2 client; skip if none is available and say so)**

Join `localhost` with a vanilla 26.2 client in offline mode, accept the pack, run `/rivals setup`, `/team join magenta @s`, `/rivals gun`, shoot the ground and a wall. Expect: coloured splats on floor and wall faces with no hitbox, the gun held with the tank on top in the team colour, a magenta bossbar. If the tank sits on the side rather than on top, rotate every hand transform in `paint_gun.json`'s `display` block by 90° about Y (same edit in all four), rebuild, re-check, and re-render the SVG sheet. Record what was seen (or that it was skipped) in the final report.

- [ ] **Step 5: Commit**

```bash
git add mods/metacraft-rivals/README.md
git commit -m "rivals: README and run instructions"
```
