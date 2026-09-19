# Metacraft Rivals v2 — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the v1 paint prototype feel like a game: splash and area paint on impact, recoil, Kenney splat art with per-cell variety, paint on any block shape, a gloss shader, a converted Kenney blaster model, ink with reload and an action-bar ammo bar, squid form and enemy-paint slowness.

**Architecture:** Everything stays inside `mods/metacraft-rivals`, server-side, for vanilla clients through Polymer. Impact behaviour lives in `Painter`; feel in `PaintGun`/`Recoil`; art and pack overrides are generated at pack build by `SplatArt`; non-full faces get Polymer display quads from `PaintDisplays`; ink is custom data on the gun stack managed by `Ink`, shown by `InkHud`; per-player ticks (`PlayerTick`) apply squid form and slowness.

**Tech Stack:** Java 25, Minecraft 26.2 (Mojang mappings), Fabric API 0.159.0+26.2, Polymer 0.17.5+26.2 (core, resource-pack, virtual-entity, autohost), Fabric GameTest API, Python 3 + Pillow for the offline converter.

**Spec:** `docs/superpowers/specs/2026-09-12-metacraft-rivals-v2-design.md` (v2) on top of `docs/superpowers/specs/2026-09-11-metacraft-rivals-paint-prototype-design.md` (v1).

## Global Constraints

- Versions pinned by `gradle.properties` on `dev`: `minecraft_version=26.2`, `polymer_version=0.17.5+26.2`, `fabric_api_version=0.159.0+26.2`, Java 25. Never edit these.
- Vanilla clients only; no client code; no mixins; no access widener; no datagen. Assets are JSON/PNG in resources or generated at pack build.
- Standalone module, never in `dist`. Branch `worktree-rivals` in this worktree; commit messages `rivals: …`; no `Co-Authored-By` trailer; plain, separate git commands (no `&&`, pipes or variables on a git line).
- Tabs for indentation (Java, JSON, GLSL alike). Gradle always `--offline`. Game tests: `./gradlew --offline mods:metacraft-rivals:runGameTest` ("All N required tests passed" counts vanilla's built-in always_pass; currently 15 = 14 ours + 1). Compile check: `./gradlew --offline mods:metacraft-rivals:compileJava`. Never run the module `build` without `-x mods:metacraft-lib:test`.
- The mod's own asset files ride in through `PolymerResourcePackUtils.addModAssets`; anything under `assets/minecraft/...` (blockstate and shader overrides) is written explicitly with `builder.addData` in the `RESOURCE_PACK_CREATION_EVENT`, so the override is guaranteed regardless of how Polymer copies mod assets.
- Kenney assets are CC0 and already committed: `src/main/resources/kenney/splat/splat{03,04,05,06,07,12,13,14}.png` (+ `LICENSE.txt`), `tools/kenney/blaster-b.obj`, `blaster-b.mtl`, `colormap.png` (+ `LICENSE.txt`).
- Mock players in game tests come from `helper.makeMockPlayer(GameType)`; they are not `ServerPlayer`s and have no connection. Any code that sends packets or action-bar text guards on `player instanceof ServerPlayer sp && sp.connection != null`.
- All file paths below are relative to `mods/metacraft-rivals/` unless they start with `docs/`.

---

### Task 1: Splash rays, impact particles and sound

**Files:**
- Modify: `src/main/java/nu/metacraft/rivals/paint/Painter.java`
- Modify: `src/main/java/nu/metacraft/rivals/gun/PaintBall.java`
- Test: `src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java`

**Interfaces:**
- Consumes: `Painter.splat(...)`, `Painter.paintFace(...)` (v1).
- Produces: `static int Painter.splash(ServerLevel level, Vec3 impact, BlockPos struck, Direction face, PaintColor color, RandomSource random, @Nullable Entity source)` returning cells changed by blob + rays; `static final Vec3[] Painter.RAY_DIRECTIONS` (14 unit vectors); `static final double Painter.RAY_LENGTH = 1.5`.

- [ ] **Step 1: Write the failing test**

Add to `RivalsGameTests` (new imports: `net.minecraft.world.phys.Vec3` if missing, `nu.metacraft.rivals.paint.Painter` exists):

```java
	/** A splash on the floor beside a wall paints the wall's face too (the rays), not only the floor. */
	@GameTest
	public void splashPaintsAdjacentWall(GameTestHelper helper) {
		stoneFloor(helper, 5);
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(4, y, 2), Blocks.STONE); // wall east of the hit
		BlockPos struck = new BlockPos(3, 1, 2);
		Vec3 impact = helper.absoluteVec(new Vec3(3.6, 2.0, 2.5));
		int changed = Painter.splash(helper.getLevel(), impact, helper.absolutePos(struck), Direction.UP, PaintColor.CYAN,
				helper.getLevel().getRandom(), null);
		helper.assertTrue(changed >= 5, "blob plus rays painted at least five cells, got " + changed);
		BlockState floorCell = helper.getBlockState(new BlockPos(3, 2, 2));
		helper.assertTrue(floorCell.is(PaintBlocks.of(PaintColor.CYAN)) && floorCell.getValue(MultifaceBlock.getFaceProperty(Direction.DOWN)),
				"floor cell painted");
		BlockState wallCell = helper.getBlockState(new BlockPos(3, 2, 2)); // same cell holds the wall's west face
		helper.assertTrue(wallCell.getValue(MultifaceBlock.getFaceProperty(Direction.EAST)),
				Component.literal("the wall face east of the hit is painted, got " + wallCell));
		helper.succeed();
	}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew --offline mods:metacraft-rivals:compileJava`
Expected: FAILS with "cannot find symbol: method splash".

- [ ] **Step 3: Implement `Painter.splash`**

Add to `Painter` (imports: `net.minecraft.core.particles.DustParticleOptions`, `net.minecraft.sounds.SoundEvents`, `net.minecraft.sounds.SoundSource`, `net.minecraft.world.entity.Entity`, `net.minecraft.world.level.ClipContext`, `net.minecraft.world.phys.BlockHitResult`, `net.minecraft.world.phys.HitResult`, `net.minecraft.world.phys.Vec3`, `net.minecraft.world.phys.shapes.CollisionContext`, `org.jspecify.annotations.Nullable`):

```java
	/** Ray length from the impact point, in blocks. */
	public static final double RAY_LENGTH = 1.5;
	/** The six axis directions and the eight body diagonals, unit length. */
	public static final Vec3[] RAY_DIRECTIONS = rayDirections();

	private static Vec3[] rayDirections() {
		Vec3[] rays = new Vec3[14];
		int i = 0;
		for (Direction d : DIRECTIONS) rays[i++] = Vec3.atLowerCornerOf(d.getUnitVec3i());
		for (int x = -1; x <= 1; x += 2) {
			for (int y = -1; y <= 1; y += 2) {
				for (int z = -1; z <= 1; z += 2) rays[i++] = new Vec3(x, y, z).normalize();
			}
		}
		return rays;
	}

	/**
	 * A full impact: the 3×3 blob on the struck face, then fourteen short rays from the impact point that
	 * paint whatever face they hit (so a floor shot beside a wall also paints the wall and the corner),
	 * with a coloured dust burst and a wet sound. Returns how many cells changed.
	 */
	public static int splash(ServerLevel level, Vec3 impact, BlockPos struck, Direction face, PaintColor color,
			RandomSource random, @Nullable Entity source) {
		int changed = splat(level, struck, face, color, random);
		Vec3 from = impact.add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.05));
		DustParticleOptions dust = new DustParticleOptions(color.rgb, 1.6f);
		for (Vec3 ray : RAY_DIRECTIONS) {
			Vec3 to = from.add(ray.scale(RAY_LENGTH));
			ClipContext context = source != null
					? new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)
					: new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
			BlockHitResult hit = level.clip(context);
			if (hit.getType() != HitResult.Type.BLOCK) continue;
			if (paintFace(level, hit.getBlockPos(), hit.getDirection(), color)) changed++;
			Vec3 at = hit.getLocation();
			level.sendParticles(dust, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.01);
		}
		level.sendParticles(dust, impact.x, impact.y, impact.z, 24, 0.35, 0.35, 0.35, 0.02);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.SLIME_BLOCK_HIT, SoundSource.BLOCKS, 0.8f, 1.3f);
		return changed;
	}
```

`Direction.getUnitVec3i()` returns the `Vec3i` step; if that name does not exist in 26.2, `javap -p` `net.minecraft.core.Direction` in `~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar` and use the accessor returning `Vec3i` (older name `getNormal()`).

- [ ] **Step 4: Route the ball through `splash`**

In `PaintBall`, replace the two hit handlers:

```java
	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (level() instanceof ServerLevel serverLevel) {
			Painter.splash(serverLevel, hit.getLocation(), hit.getBlockPos(), hit.getDirection(), color, random, this);
		}
	}

	/** No damage (the snowball would hurt blazes); splash from where the ball is, over the ground under the target. */
	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (level() instanceof ServerLevel serverLevel) {
			BlockPos below = hit.getEntity().blockPosition().below();
			Painter.splash(serverLevel, position(), below, Direction.UP, color, random, this);
		}
	}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew --offline mods:metacraft-rivals:runGameTest`
Expected: BUILD SUCCESSFUL, "All 16 required tests passed". The two existing ball tests still pass (they only assert the floor cell).

- [ ] **Step 6: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: splash rays, impact particles and sound"
```

---

### Task 2: Firing feel — recoil, push, muzzle burst, sounds, faster balls

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/gun/Recoil.java`
- Modify: `src/main/java/nu/metacraft/rivals/gun/PaintGun.java`
- Modify: `src/main/java/nu/metacraft/rivals/Rivals.java` (call `Recoil.init()` after `ScoreBars.init()`)
- Test: `src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java`

**Interfaces:**
- Produces: `Recoil.init()`; `static void Recoil.kick(Player shooter)` (sends the kick, queues the settle); `static int Recoil.pending()` (queued settles, for the test); constants `KICK_PITCH = -2.5f`, `SETTLE_PITCH = 1.8f`.
- Changes: `PaintGun.VELOCITY = 1.8f`, `PaintGun.INACCURACY = 2.0f`.

- [ ] **Step 1: Write the failing test**

```java
	/** Recoil on a mock player (no connection) sends nothing and leaves nothing queued; a shot still succeeds. */
	@GameTest
	public void recoilIsSafeWithoutConnection(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.LIME));
		int before = Recoil.pending();
		InteractionResult result = PaintGun.ITEM.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS, "shot succeeds");
		helper.assertValueEqual(Recoil.pending(), before, "no settle queued for a connectionless player");
		helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0).forEach(Entity::discard);
		helper.succeed();
	}
```

(import `nu.metacraft.rivals.gun.Recoil`.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --offline mods:metacraft-rivals:compileJava` — Expected: FAILS, "package nu.metacraft.rivals.gun … Recoil" missing.

- [ ] **Step 3: Write `Recoil`**

```java
package nu.metacraft.rivals.gun;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A camera kick for vanilla clients: a relative pitch nudge up on the shot, eased back most of the way on
 * the next tick. Only real server players with a connection get packets; mock players are skipped.
 */
public final class Recoil {
	public static final float KICK_PITCH = -2.5f;
	public static final float SETTLE_PITCH = 1.8f;
	private static final List<ServerPlayer> SETTLE_NEXT_TICK = new ArrayList<>();

	private Recoil() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SETTLE_NEXT_TICK.isEmpty()) return;
			for (ServerPlayer player : List.copyOf(SETTLE_NEXT_TICK)) {
				if (player.connection != null && !player.isRemoved()) {
					player.connection.send(new ClientboundPlayerRotationPacket(0f, true, SETTLE_PITCH, true));
				}
			}
			SETTLE_NEXT_TICK.clear();
		});
	}

	public static void kick(Player shooter) {
		if (!(shooter instanceof ServerPlayer player) || player.connection == null) return;
		player.connection.send(new ClientboundPlayerRotationPacket(0f, true, KICK_PITCH, true));
		SETTLE_NEXT_TICK.add(player);
	}

	public static int pending() {
		return SETTLE_NEXT_TICK.size();
	}
}
```

- [ ] **Step 4: Update `PaintGun`**

Change the constants to `VELOCITY = 1.8f` and `INACCURACY = 2.0f`. Replace `shoot` and the success path of `use`:

```java
		PaintBall ball = shoot(serverLevel, player, color.get());
		feel(serverLevel, player, color.get());
		player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
```

```java
	/** Throw one paint ball from the shooter's eyes along their view. */
	public static PaintBall shoot(ServerLevel level, LivingEntity shooter, PaintColor color) {
		PaintBall ball = new PaintBall(level, shooter, color);
		ball.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0.0f, VELOCITY, INACCURACY);
		level.addFreshEntity(ball);
		return ball;
	}

	/** The chunk of a shot: camera kick, a nudge back, a muzzle burst in the team colour, two layered sounds. */
	static void feel(ServerLevel level, Player shooter, PaintColor color) {
		Recoil.kick(shooter);
		Vec3 look = shooter.getLookAngle();
		shooter.push(-look.x * 0.06, 0, -look.z * 0.06);
		shooter.hurtMarked = true;
		Vec3 muzzle = shooter.getEyePosition().add(look.scale(0.9));
		level.sendParticles(new DustParticleOptions(color.rgb, 1.2f), muzzle.x, muzzle.y, muzzle.z, 10, 0.1, 0.1, 0.1, 0.02);
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.7f, 0.7f);
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.5f, 1.4f);
	}
```

(imports: `net.minecraft.core.particles.DustParticleOptions`, `net.minecraft.world.phys.Vec3`.) In `Rivals.onInitialize()` add `Recoil.init();` after `ScoreBars.init();` (import `nu.metacraft.rivals.gun.Recoil`).

- [ ] **Step 5: Run the tests** — Expected: "All 17 required tests passed".

- [ ] **Step 6: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: recoil kick, push, muzzle burst and layered shot sounds"
```

---

### Task 3: Kenney splat art with 32 variants per colour and blockstate overrides

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/pack/SplatArt.java`
- Delete: `src/main/java/nu/metacraft/rivals/pack/SplatTexture.java`
- Modify: `src/main/java/nu/metacraft/rivals/pack/RivalsPack.java`
- Modify: `src/main/java/nu/metacraft/rivals/PaintColor.java` (remove `donorTexturePath`, add `donorPath()`)
- Test: `src/main/java/nu/metacraft/rivals/gametest/RivalsGameTests.java` (replace `splatTextureIsColouredBlob`)

**Interfaces:**
- Produces: `SplatArt.SHAPES = {"03","04","05","06","07","12","13","14"}`, `SplatArt.SIZE = 32`, `SplatArt.ROTATIONS = 4`, `SplatArt.PAINT_ALPHA = 229`; `static byte[] SplatArt.texture(int rgb, String shape, int rotation)`; `static byte[] SplatArt.whiteMask(String shape)` (for Task 4); `static String SplatArt.quadModel(String texturePath)` (JSON of the lichen quad); `static String SplatArt.blockstate(PaintColor color)` (JSON of the multipart override); `static Map<String, byte[]> SplatArt.packFiles()` (every path → bytes the pack needs, block art only).
- Produces: `String PaintColor.donorPath()` = the donor's registry path (`sculk_vein` …).

- [ ] **Step 1: Write the failing tests**

Replace `splatTextureIsColouredBlob` with:

```java
	/** Every generated splat is 32×32, paint texels carry the alpha marker 229, the rest is fully transparent. */
	@GameTest
	public void splatArtCarriesTheMarkerAlpha(GameTestHelper helper) throws IOException {
		for (PaintColor color : PaintColor.values()) {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(SplatArt.texture(color.rgb, SplatArt.SHAPES[0], 0)));
			helper.assertValueEqual(image.getWidth(), SplatArt.SIZE, color.id + " width");
			helper.assertValueEqual(image.getHeight(), SplatArt.SIZE, color.id + " height");
			int paint = 0;
			int clear = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
					if (alpha == SplatArt.PAINT_ALPHA) paint++;
					else if (alpha == 0) clear++;
					else helper.fail(color.id + ": unexpected alpha " + alpha + " at " + x + "," + y);
				}
			}
			helper.assertTrue(paint > 100 && clear > 50, color.id + ": paint=" + paint + " clear=" + clear);
		}
		helper.succeed();
	}

	/** The blockstate override lists 32 variants per face and every model/texture it names is in the pack file set. */
	@GameTest
	public void blockstateOverridesReferenceGeneratedModels(GameTestHelper helper) {
		Map<String, byte[]> files = SplatArt.packFiles();
		for (PaintColor color : PaintColor.values()) {
			String path = "assets/minecraft/blockstates/" + color.donorPath() + ".json";
			helper.assertTrue(files.containsKey(path), "override present: " + path);
			JsonObject state = JsonParser.parseString(new String(files.get(path), StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray multipart = state.getAsJsonArray("multipart");
			helper.assertValueEqual(multipart.size(), 7, color.id + " multipart entries (6 faces + none)");
			for (JsonElement part : multipart) {
				JsonArray apply = part.getAsJsonObject().getAsJsonArray("apply");
				helper.assertValueEqual(apply.size(), SplatArt.SHAPES.length * SplatArt.ROTATIONS, color.id + " variants per face");
				for (JsonElement variant : apply) {
					String model = variant.getAsJsonObject().get("model").getAsString(); // metacraft-rivals:block/splat_x_y_z
					String modelPath = "assets/metacraft-rivals/models/block/" + model.substring(model.indexOf('/') + 1) + ".json";
					helper.assertTrue(files.containsKey(modelPath), "model in pack: " + modelPath);
					JsonObject modelJson = JsonParser.parseString(new String(files.get(modelPath), StandardCharsets.UTF_8)).getAsJsonObject();
					String texture = modelJson.getAsJsonObject("textures").get("splat").getAsString();
					String texturePath = "assets/metacraft-rivals/textures/" + texture.substring(texture.indexOf(':') + 1) + ".png";
					helper.assertTrue(files.containsKey(texturePath), "texture in pack: " + texturePath);
				}
			}
		}
		helper.succeed();
	}
```

(imports: `nu.metacraft.rivals.pack.SplatArt` replacing `SplatTexture`; `java.util.Map` exists.)

- [ ] **Step 2: Run to verify it fails** — compile error, `SplatArt` missing.

- [ ] **Step 3: Write `SplatArt`**

```java
package nu.metacraft.rivals.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paint art from Kenney's CC0 Splat Pack: eight chunky silhouettes, cropped so the blob overfills the
 * tile, scaled to 32 px, tinted per colour with a darker rim, and baked in four rotations because a
 * vanilla blockstate cannot rotate a north/south face about its own normal. Paint texels carry alpha
 * {@link #PAINT_ALPHA} as the marker the gloss shader reads. The donor blockstates are replaced by a
 * multipart file whose every face lists all 32 variants, so the client picks one per block position.
 */
public final class SplatArt {
	public static final String[] SHAPES = {"03", "04", "05", "06", "07", "12", "13", "14"};
	public static final int SIZE = 32;
	public static final int ROTATIONS = 4;
	public static final int PAINT_ALPHA = 229;
	private static final double CROP = 0.72;
	private static final int RIM = 2;
	private static final double RIM_BRIGHTNESS = 0.62;
	/** Vanilla lichen face → model rotation, copied from glow_lichen.json. */
	private static final String[][] FACES = {
			{"north", "", ""}, {"east", "", "90"}, {"south", "", "180"}, {"west", "", "270"}, {"up", "270", ""}, {"down", "90", ""}};

	private SplatArt() {}

	/** Every generated file for the block art: textures, models, and the three blockstate overrides. */
	public static Map<String, byte[]> packFiles() {
		Map<String, byte[]> files = new LinkedHashMap<>();
		for (PaintColor color : PaintColor.values()) {
			for (String shape : SHAPES) {
				for (int rotation = 0; rotation < ROTATIONS; rotation++) {
					String name = "splat_" + color.id + "_" + shape + "_" + rotation;
					files.put("assets/" + Rivals.MOD_ID + "/textures/block/" + name + ".png", texture(color.rgb, shape, rotation));
					files.put("assets/" + Rivals.MOD_ID + "/models/block/" + name + ".json",
							quadModel(Rivals.MOD_ID + ":block/" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
				}
			}
			files.put("assets/minecraft/blockstates/" + color.donorPath() + ".json",
					blockstate(color).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		return files;
	}

	/** The tinted, rimmed, rotated 32×32 splat. */
	public static byte[] texture(int rgb, String shape, int rotation) {
		boolean[][] mask = rotate(mask(shape), rotation);
		boolean[][] inner = erode(mask, RIM);
		int fill = (PAINT_ALPHA << 24) | (rgb & 0xFFFFFF);
		int r = (int) (((rgb >> 16) & 0xFF) * RIM_BRIGHTNESS);
		int g = (int) (((rgb >> 8) & 0xFF) * RIM_BRIGHTNESS);
		int b = (int) ((rgb & 0xFF) * RIM_BRIGHTNESS);
		int rim = (PAINT_ALPHA << 24) | (r << 16) | (g << 8) | b;
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				if (mask[y][x]) image.setRGB(x, y, inner[y][x] ? fill : rim);
			}
		}
		return png(image);
	}

	/** The same silhouette as an opaque white shape (alpha 255) for the dye-tinted display quads. */
	public static byte[] whiteMask(String shape) {
		boolean[][] mask = mask(shape);
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				if (mask[y][x]) image.setRGB(x, y, 0xFFFFFFFF);
			}
		}
		return png(image);
	}

	/** Vanilla's lichen quad model, pointing at one of our textures. */
	public static String quadModel(String texture) {
		return """
				{
					"ambientocclusion": false,
					"textures": {"particle": "%1$s", "splat": "%1$s"},
					"elements": [{
						"from": [0, 0, 0.1],
						"to": [16, 16, 0.1],
						"faces": {
							"north": {"uv": [16, 0, 0, 16], "texture": "#splat"},
							"south": {"uv": [0, 0, 16, 16], "texture": "#splat"}
						}
					}]
				}
				""".formatted(texture);
	}

	/** The multipart override for a colour's donor block: vanilla's structure, every face listing all 32 variants. */
	public static String blockstate(PaintColor color) {
		JsonArray multipart = new JsonArray();
		for (String[] face : FACES) {
			JsonObject when = new JsonObject();
			when.addProperty(face[0], "true");
			multipart.add(part(when, color, face[1], face[2]));
		}
		JsonObject none = new JsonObject();
		for (String[] face : FACES) none.addProperty(face[0], "false");
		multipart.add(part(none, color, "", ""));
		JsonObject root = new JsonObject();
		root.add("multipart", multipart);
		return root.toString();
	}

	private static JsonObject part(JsonObject when, PaintColor color, String x, String y) {
		JsonArray apply = new JsonArray();
		for (String shape : SHAPES) {
			for (int rotation = 0; rotation < ROTATIONS; rotation++) {
				JsonObject variant = new JsonObject();
				variant.addProperty("model", Rivals.MOD_ID + ":block/splat_" + color.id + "_" + shape + "_" + rotation);
				if (!x.isEmpty()) variant.addProperty("x", Integer.parseInt(x));
				if (!y.isEmpty()) variant.addProperty("y", Integer.parseInt(y));
				variant.addProperty("uvlock", true);
				variant.addProperty("weight", 1);
				apply.add(variant);
			}
		}
		JsonObject part = new JsonObject();
		part.add("when", when);
		part.add("apply", apply);
		return part;
	}

	/** The Kenney sprite's alpha, centre-cropped and scaled to 32×32, thresholded. */
	static boolean[][] mask(String shape) {
		BufferedImage source;
		try (InputStream in = SplatArt.class.getResourceAsStream("/kenney/splat/splat" + shape + ".png")) {
			if (in == null) throw new IllegalStateException("[" + Rivals.MOD_ID + "] missing Kenney splat " + shape);
			source = ImageIO.read(in);
		} catch (IOException e) {
			throw new UncheckedIOException("could not read Kenney splat " + shape, e);
		}
		int side = (int) (source.getWidth() * CROP);
		int offset = (source.getWidth() - side) / 2;
		boolean[][] mask = new boolean[SIZE][SIZE];
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				// Box-filter the source block that maps onto this texel: opaque if more than half of it is.
				int sx0 = offset + x * side / SIZE, sx1 = offset + (x + 1) * side / SIZE;
				int sy0 = offset + y * side / SIZE, sy1 = offset + (y + 1) * side / SIZE;
				long alpha = 0;
				int n = 0;
				for (int sy = sy0; sy < sy1; sy++) {
					for (int sx = sx0; sx < sx1; sx++) {
						alpha += (source.getRGB(sx, sy) >>> 24) & 0xFF;
						n++;
					}
				}
				mask[y][x] = n > 0 && alpha / n > 128;
			}
		}
		return mask;
	}

	static boolean[][] rotate(boolean[][] mask, int quarterTurns) {
		boolean[][] out = mask;
		for (int t = 0; t < quarterTurns % 4; t++) {
			boolean[][] next = new boolean[SIZE][SIZE];
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) next[x][SIZE - 1 - y] = out[y][x];
			}
			out = next;
		}
		return out;
	}

	static boolean[][] erode(boolean[][] mask, int radius) {
		boolean[][] out = new boolean[SIZE][SIZE];
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				boolean keep = mask[y][x];
				for (int dy = -radius; keep && dy <= radius; dy++) {
					for (int dx = -radius; keep && dx <= radius; dx++) {
						int nx = x + dx, ny = y + dy;
						if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE || !mask[ny][nx]) keep = false;
					}
				}
				out[y][x] = keep;
			}
		}
		return out;
	}

	private static byte[] png(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(image, "png", out)) throw new IOException("no PNG writer available");
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("could not encode splat art", e);
		}
	}
}
```

- [ ] **Step 4: Wire the pack, retire `SplatTexture`, add `donorPath`**

In `PaintColor`, replace `donorTexturePath()` with:

```java
	/** The donor block's registry path, e.g. {@code sculk_vein}: the blockstate file the pack overrides. */
	public String donorPath() {
		return BuiltInRegistries.BLOCK.getKey(donor).getPath();
	}
```

`RivalsPack.init()` becomes:

```java
	public static void init() {
		PolymerResourcePackUtils.addModAssets(Rivals.MOD_ID);
		PolymerResourcePackUtils.markAsRequired();
		PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> {
			Map<String, byte[]> files = SplatArt.packFiles();
			files.forEach(builder::addData);
			Rivals.LOGGER.info("[{}] pack: {} splat files written ({} colours × {} shapes × {} rotations)", Rivals.MOD_ID,
					files.size(), PaintColor.values().length, SplatArt.SHAPES.length, SplatArt.ROTATIONS);
		});
	}
```

(import `java.util.Map`; update the class javadoc to say the donor blockstates are overridden.) Delete `SplatTexture.java`. Grep for `donorTexturePath` and `SplatTexture` — no other references may remain.

- [ ] **Step 5: Run the tests** — Expected: "All 18 required tests passed".

- [ ] **Step 6: Commit**

```bash
git add -A mods/metacraft-rivals/src
git commit -m "rivals: Kenney splat art, 32 variants per colour, donor blockstate overrides"
```

---

### Task 4: Paint on any block — display quads for non-full faces

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/paint/PaintDisplays.java`
- Modify: `src/main/java/nu/metacraft/rivals/paint/Painter.java` (`paintFace` acceptance and the non-full branch)
- Modify: `src/main/java/nu/metacraft/rivals/paint/PaintBlock.java` (`isValidStateForPlacement` override)
- Modify: `src/main/java/nu/metacraft/rivals/paint/PaintTally.java` (count and reset include displays)
- Modify: `src/main/java/nu/metacraft/rivals/pack/SplatArt.java` (`packFiles` adds the quad item art)
- Modify: `build.gradle` (add `polymer-virtual-entity`), `src/main/resources/fabric.mod.json` (`"polymer-virtual-entity": "*"`)
- Test: `RivalsGameTests`

**Interfaces:**
- Produces: `PaintDisplays.of(ServerLevel)`, `static void clearAll()`, `boolean paint(ServerLevel level, BlockPos surface, Direction face, PaintColor color)` (true if a quad set was created/recoloured), `Map<PaintColor, Integer> count()`, `int clear()`, `int holders()`, `@Nullable PaintColor colorAt(BlockPos cell)`.
- Produces: pack files `assets/metacraft-rivals/items/splat_quad_<shape>.json`, `models/item/splat_quad_<shape>.json`, `textures/item/splat_quad_<shape>.png`.

- [ ] **Step 1: Write the failing test**

```java
	/** A stair top takes paint as display quads: tracked, counted in the colour, removed by reset. */
	@GameTest
	public void stairTakesDisplayPaint(GameTestHelper helper) {
		BlockPos stair = new BlockPos(2, 1, 2);
		helper.setBlock(stair, Blocks.STONE_STAIRS.defaultBlockState());
		PaintDisplays displays = PaintDisplays.of(helper.getLevel());
		int before = displays.holders();
		boolean painted = Painter.paintFace(helper.getLevel(), helper.absolutePos(stair), Direction.UP, PaintColor.LIME);
		helper.assertTrue(painted, "stair top accepted paint");
		helper.assertTrue(helper.getBlockState(stair.above()).isAir(), "no paint block above a stair (quads instead)");
		helper.assertValueEqual(displays.holders(), before + 1, "one holder for the cell");
		helper.assertTrue(displays.colorAt(helper.absolutePos(stair.above())) == PaintColor.LIME, "cell is lime");
		helper.assertTrue(displays.count().get(PaintColor.LIME) >= 1, "counted as lime faces");
		boolean recoloured = Painter.paintFace(helper.getLevel(), helper.absolutePos(stair), Direction.UP, PaintColor.CYAN);
		helper.assertTrue(recoloured && displays.colorAt(helper.absolutePos(stair.above())) == PaintColor.CYAN, "recoloured to cyan");
		helper.assertValueEqual(displays.holders(), before + 1, "recolour reuses the cell");
		PaintTally tally = new PaintTally();
		int removed = displays.clear();
		helper.assertTrue(removed >= 1 && displays.holders() == 0, "clear removed the quads");
		helper.succeed();
	}
```

(imports `nu.metacraft.rivals.paint.PaintDisplays`.) Note `displays.clear()` clears the whole level's displays; the test runs alone in that respect, and other tests never create displays.

- [ ] **Step 2: Run to verify it fails** — compile error, `PaintDisplays` missing.

- [ ] **Step 3: Dependencies**

`build.gradle` dependencies block gains `implementation("eu.pb4:polymer-virtual-entity:${project.polymer_version}")`; `fabric.mod.json` `depends` gains `"polymer-virtual-entity": "*"`.

- [ ] **Step 4: Write `PaintDisplays`**

```java
package nu.metacraft.rivals.paint;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;
import nu.metacraft.rivals.pack.SplatArt;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paint for faces a multiface block cannot sit on (stairs, slabs, fences, panes …): one flat splat quad
 * per collision-box face on the struck side, as Polymer item displays that wrap the block's real shape.
 * One holder per cell, one colour per cell; recolouring rebuilds the holder. In memory only, like the
 * tally: a restart drops them.
 */
public final class PaintDisplays {
	private static final Map<ResourceKey<Level>, PaintDisplays> ALL = new HashMap<>();
	private static final double LIFT = 0.01;

	private record Painted(PaintColor color, ElementHolder holder, int quads) {}

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

	/** Quads per colour, counted as faces. */
	public Map<PaintColor, Integer> count() {
		Map<PaintColor, Integer> counts = new EnumMap<>(PaintColor.class);
		for (PaintColor color : PaintColor.values()) counts.put(color, 0);
		for (Painted painted : cells.values()) counts.merge(painted.color, painted.quads, Integer::sum);
		return counts;
	}

	/** Destroy every holder in this level. Returns how many cells were cleared. */
	public int clear() {
		int n = cells.size();
		cells.values().forEach(painted -> painted.holder.destroy());
		cells.clear();
		return n;
	}

	/**
	 * Cover the {@code face} side of every collision box of the block at {@code surface} with quads in the
	 * cell in front. Returns false when the cell already holds this colour or the shape has no boxes.
	 */
	public boolean paint(ServerLevel level, BlockPos surface, Direction face, PaintColor color) {
		BlockPos cell = surface.relative(face).immutable();
		Painted existing = cells.get(cell);
		if (existing != null && existing.color == color) return false;
		BlockState state = level.getBlockState(surface);
		VoxelShape shape = state.getCollisionShape(level, surface);
		if (shape.isEmpty()) shape = state.getShape(level, surface);
		List<AABB> boxes = shape.toAabbs();
		if (boxes.isEmpty()) return false;
		if (existing != null) existing.holder.destroy();
		ElementHolder holder = new ElementHolder();
		Vec3 origin = Vec3.atLowerCornerOf(cell);
		int quads = 0;
		for (AABB box : boxes) {
			holder.addElement(quad(box, surface, face, color, origin, level.getRandom().nextInt(SplatArt.SHAPES.length)));
			quads++;
		}
		ChunkAttachment.of(holder, level, origin);
		cells.put(cell, new Painted(color, holder, quads));
		return true;
	}

	/** One splat quad on the {@code face} side of {@code box} (box coordinates are local to the surface block). */
	private static ItemDisplayElement quad(AABB box, BlockPos surface, Direction face, PaintColor color, Vec3 origin, int shape) {
		Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
		// In-plane axes: u is the model's X, v the model's Y.
		Vector3f u = face.getAxis() == Direction.Axis.X ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
		Vector3f v = face.getAxis() == Direction.Axis.Y ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
		double w = face.getAxis() == Direction.Axis.X ? box.getZsize() : box.getXsize();
		double h = face.getAxis() == Direction.Axis.Y ? box.getZsize() : box.getYsize();
		Vec3 centre = box.getCenter();
		double along = switch (face) {
			case UP -> box.maxY; case DOWN -> box.minY; case EAST -> box.maxX; case WEST -> box.minX; case SOUTH -> box.maxZ; case NORTH -> box.minZ;
		};
		Vec3 faceCentre = switch (face.getAxis()) {
			case X -> new Vec3(along, centre.y, centre.z);
			case Y -> new Vec3(centre.x, along, centre.z);
			case Z -> new Vec3(centre.x, centre.y, along);
		};
		Vec3 world = Vec3.atLowerCornerOf(surface).add(faceCentre).add(new Vec3(n.x, n.y, n.z).scale(LIFT));
		ItemStack stack = new ItemStack(Items.STICK);
		stack.set(DataComponents.ITEM_MODEL, Rivals.id("splat_quad_" + SplatArt.SHAPES[shape]));
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.rgb));
		ItemDisplayElement element = new ItemDisplayElement(stack);
		element.setItemDisplayContext(ItemDisplayContext.FIXED);
		element.setOffset(world.subtract(origin));
		element.setScale(new Vector3f((float) w, (float) h, 1f));
		element.setLeftRotation(new Quaternionf().setFromNormalized(new Matrix3f(u, v, n)));
		return element;
	}
}
```

`ItemDisplayElement` renders the item model centred on the element, so a 16×16 quad at scale 1 spans one block; the scale stretches it to the box face. If `org.jetbrains.annotations.Nullable` is not on the classpath use `org.jspecify.annotations.Nullable` (the module already uses jspecify elsewhere).

- [ ] **Step 5: Relax `Painter.paintFace` and route non-full faces**

Replace the acceptance and attach check in `paintFace` with:

```java
	/** Anything solid: not air, not replaceable (grass, snow), not a fluid, not paint. */
	static boolean paintable(BlockState surface) {
		return !surface.isAir() && !surface.canBeReplaced() && surface.getFluidState().isEmpty() && !(surface.getBlock() instanceof PaintBlock);
	}
```

and, at the top of `paintFace`, before reading `existing`:

```java
		BlockState surfaceState = level.getBlockState(surface);
		if (!paintable(surfaceState)) return false;
		if (!Block.isFaceFull(surfaceState.getCollisionShape(level, surface), face)) {
			return PaintDisplays.of(level).paint(level, surface, face, color);
		}
```

Remove the `MultifaceBlock.canAttachTo(...)` line (the full-face check above replaces it). Update the class javadoc: full faces get paint blocks, other shapes get display quads.

- [ ] **Step 6: Keep paint blocks alive next to non-full supports**

In `PaintBlock` add (imports `net.minecraft.core.BlockPos`, `net.minecraft.world.level.BlockGetter`):

```java
	/** The relaxed support rule: any solid block on that side, not only a full face. */
	@Override
	public boolean isValidStateForPlacement(BlockGetter level, BlockState state, BlockPos pos, Direction direction) {
		return Painter.paintable(level.getBlockState(pos.relative(direction)));
	}
```

(`Painter.paintable` is `public static`.) Vanilla's `canSurvive`/`updateShape` go through `isValidStateForPlacement`, so faces only drop when the support becomes air/replaceable.

- [ ] **Step 7: Tally and reset include displays**

In `PaintTally.count`, after the loop: `PaintDisplays.of(level).count().forEach((c, n) -> counts.merge(c, n, Integer::sum));`. In `PaintTally.reset`, before `cells.clear()`: `removed += PaintDisplays.of(level).clear();`. In `PaintTally.clearAll()` also call `PaintDisplays.clearAll();`.

- [ ] **Step 8: Quad item art in the pack**

In `SplatArt.packFiles()`, after the colour loop:

```java
		for (String shape : SHAPES) {
			String name = "splat_quad_" + shape;
			files.put("assets/" + Rivals.MOD_ID + "/textures/item/" + name + ".png", whiteMask(shape));
			files.put("assets/" + Rivals.MOD_ID + "/models/item/" + name + ".json", """
					{
						"textures": {"particle": "%1$s", "splat": "%1$s"},
						"elements": [{
							"from": [0, 0, 8],
							"to": [16, 16, 8],
							"faces": {
								"north": {"uv": [16, 0, 0, 16], "texture": "#splat", "tintindex": 0},
								"south": {"uv": [0, 0, 16, 16], "texture": "#splat", "tintindex": 0}
							}
						}],
						"display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}}
					}
					""".formatted(Rivals.MOD_ID + ":item/" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			files.put("assets/" + Rivals.MOD_ID + "/items/" + name + ".json", """
					{"model": {"type": "minecraft:model", "model": "%s", "tints": [{"type": "minecraft:dye", "default": 16777215}]}}
					""".formatted(Rivals.MOD_ID + ":item/" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
```

- [ ] **Step 9: Run the tests** — Expected: "All 19 required tests passed". `blobStaysWithinRadiusAndOnSurfaces` still passes (its hole is air, which `paintable` rejects).

- [ ] **Step 10: Commit**

```bash
git add -A mods/metacraft-rivals
git commit -m "rivals: paint on any block — display quads wrap non-full shapes"
```

---

### Task 5: Gloss core shader

**Files:**
- Create: `src/main/resources/rivals_shaders/block.vsh`, `src/main/resources/rivals_shaders/block.fsh`
- Modify: `src/main/java/nu/metacraft/rivals/pack/RivalsPack.java`
- Test: `RivalsGameTests`

**Interfaces:**
- Produces: `static byte[] RivalsPack.shader(String name)` reading `/rivals_shaders/<name>`; the pack gets `assets/minecraft/shaders/core/block.vsh` and `block.fsh`.

- [ ] **Step 1: Write the failing test**

```java
	/** The gloss shader ships in the pack and keys on the paint alpha marker. */
	@GameTest
	public void glossShaderCarriesTheMarkerGuard(GameTestHelper helper) {
		String fsh = new String(RivalsPack.shader("block.fsh"), StandardCharsets.UTF_8);
		String vsh = new String(RivalsPack.shader("block.vsh"), StandardCharsets.UTF_8);
		helper.assertTrue(fsh.contains("RIVALS_GLOSS") && fsh.contains("0.85") && fsh.contains("0.95"), "fragment shader guards on the marker alpha");
		helper.assertTrue(fsh.contains("#ifdef ALPHA_CUTOUT"), "vanilla cutout path kept");
		helper.assertTrue(vsh.contains("out vec3 viewPos"), "vertex shader exports the view position");
		helper.succeed();
	}
```

- [ ] **Step 2: Run to verify it fails** — compile error, `RivalsPack.shader` missing.

- [ ] **Step 3: Write the shaders**

`src/main/resources/rivals_shaders/block.vsh` (vanilla 26.2 plus the `viewPos` varying):

```glsl
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewPos;

void main() {
	vec3 pos = Position + ModelOffset;
	vec4 view = ModelViewMat * vec4(pos, 1.0);
	gl_Position = ProjMat * view;
	viewPos = view.xyz;

	sphericalVertexDistance = fog_spherical_distance(pos);
	cylindricalVertexDistance = fog_cylindrical_distance(pos);
	vertexColor = Color * sample_lightmap(Sampler2, UV2);
	texCoord0 = UV0;
}
```

`src/main/resources/rivals_shaders/block.fsh`:

```glsl
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;

out vec4 fragColor;

void main() {
	vec4 tex = texture(Sampler0, texCoord0);
	vec4 color = tex * vertexColor * ColorModulator;
#ifdef ALPHA_CUTOUT
	if (color.a < ALPHA_CUTOUT) {
		discard;
	}
#endif
	// RIVALS_GLOSS: paint texels carry alpha 0.9 as a marker; everything else is vanilla.
	if (tex.a > 0.85 && tex.a < 0.95) {
		vec3 n = normalize(cross(dFdx(viewPos), dFdy(viewPos)));
		vec3 v = normalize(-viewPos);
		float t = GameTime * 1200.0;
		vec3 l = normalize(vec3(0.3 + 0.15 * sin(t), 0.8, 0.5 + 0.15 * cos(t)));
		float spec = pow(max(dot(reflect(-l, n), v), 0.0), 24.0) * 0.55;
		float fresnel = pow(1.0 - max(dot(n, v), 0.0), 3.0) * 0.25;
		color.rgb += spec + fresnel;
		color.a = 1.0;
	}
	fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
```

If `GameTime` is not a member of the `Globals` block in 26.2's `globals.glsl` (check the vanilla include in the jar), drop the animation: `vec3 l = normalize(vec3(0.3, 0.8, 0.5));` and remove the globals import.

- [ ] **Step 4: Ship them in the pack**

In `RivalsPack`:

```java
	/** A shader file from the mod's resources, as shipped under assets/minecraft/shaders/core. */
	public static byte[] shader(String name) {
		try (InputStream in = RivalsPack.class.getResourceAsStream("/rivals_shaders/" + name)) {
			if (in == null) throw new IllegalStateException("[" + Rivals.MOD_ID + "] missing shader " + name);
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("could not read shader " + name, e);
		}
	}
```

and in the creation event, after the splat files: `builder.addData("assets/minecraft/shaders/core/block.vsh", shader("block.vsh")); builder.addData("assets/minecraft/shaders/core/block.fsh", shader("block.fsh"));` (imports `java.io.InputStream`, `java.io.IOException`, `java.io.UncheckedIOException`).

- [ ] **Step 5: Run the tests** — Expected: "All 20 required tests passed".

- [ ] **Step 6: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: gloss core shader keyed on the paint alpha marker"
```

---

### Task 6: Gun model converted from Kenney's blaster-b

**Files:**
- Create: `tools/obj2mc.py`
- Regenerate: `src/main/resources/assets/metacraft-rivals/models/item/paint_gun.json`, `src/main/resources/assets/metacraft-rivals/textures/item/paint_gun_palette.png`
- Modify: `docs/superpowers/specs/2026-09-11-paint-gun-design-sheet.md` (one paragraph: the model now comes from the converter; the old sheet describes the v1 hand-built model)
- Test: `RivalsGameTests.gunModelAssetsArePresent` (extend)

**Interfaces:** the converter is run by hand; its output is committed.

- [ ] **Step 1: Extend the existing test**

In `gunModelAssetsArePresent`, after `helper.assertTrue(elements.size() >= 5, ...)`, add `helper.assertTrue(elements.size() <= 400, "model stays under 400 elements, got " + elements.size());`.

- [ ] **Step 2: Write the converter**

`tools/obj2mc.py`:

```python
#!/usr/bin/env python3
"""Convert a low-poly OBJ (Kenney Blaster Kit) into a Minecraft item element model.

Surface-voxelises the mesh, colours every voxel from the OBJ's colormap texture, greedy-merges
equal-colour voxels into boxes, writes each colour as one texel of a palette PNG, and emits the
element JSON. The accent family (orange in blaster-b) becomes a white, dye-tinted tank.

    python3 mods/metacraft-rivals/tools/obj2mc.py \
        mods/metacraft-rivals/tools/kenney/blaster-b.obj \
        mods/metacraft-rivals/src/main/resources/assets/metacraft-rivals/models/item/paint_gun.json \
        mods/metacraft-rivals/src/main/resources/assets/metacraft-rivals/textures/item/paint_gun_palette.png \
        [--res 40] [--length 14] [--flip] [--max-elements 400]

Frame of the output: Y up, the mesh's longest axis along Z, muzzle toward -Z (use --flip if the
in-game screenshot shows it backwards), centred on (8, 8, 8). Standard library plus Pillow.
"""
import argparse
import json
import math
import os
import sys
from collections import defaultdict

from PIL import Image

ACCENT = [(255, 126, 68), (207, 83, 79)]  # blaster-b's orange body and its shade: the tank
ACCENT_TOLERANCE = 40


def load_obj(path):
	verts, uvs, tris = [], [], []
	for line in open(path):
		parts = line.split()
		if not parts:
			continue
		if parts[0] == "v":
			verts.append(tuple(float(p) for p in parts[1:4]))
		elif parts[0] == "vt":
			uvs.append(tuple(float(p) for p in parts[1:3]))
		elif parts[0] == "f":
			corners = []
			for token in parts[1:]:
				fields = token.split("/")
				vi = int(fields[0]) - 1
				ti = int(fields[1]) - 1 if len(fields) > 1 and fields[1] else None
				corners.append((vi, ti))
			for i in range(1, len(corners) - 1):
				tris.append((corners[0], corners[i], corners[i + 1]))
	return verts, uvs, tris


def colormap_path(obj_path):
	mtl = obj_path[:-4] + ".mtl"
	if os.path.exists(mtl):
		for line in open(mtl):
			if line.startswith("map_Kd"):
				return os.path.join(os.path.dirname(obj_path), line.split(None, 1)[1].strip())
	return os.path.join(os.path.dirname(obj_path), "colormap.png")


def near(a, b, tol):
	return all(abs(x - y) <= tol for x, y in zip(a, b))


def voxelise(verts, uvs, tris, tex, res):
	xs, ys, zs = zip(*verts)
	mins = (min(xs), min(ys), min(zs))
	extent = (max(xs) - mins[0], max(ys) - mins[1], max(zs) - mins[2])
	longest = max(extent)
	size = longest / res
	grid = {}
	for (a, b, c) in tris:
		pa, pb, pc = verts[a[0]], verts[b[0]], verts[c[0]]
		uva = uvs[a[1]] if a[1] is not None else (0, 0)
		uvb = uvs[b[1]] if b[1] is not None else (0, 0)
		uvc = uvs[c[1]] if c[1] is not None else (0, 0)
		edge = max(math.dist(pa, pb), math.dist(pb, pc), math.dist(pa, pc))
		steps = max(2, int(edge / (size * 0.5)) + 1)
		for i in range(steps + 1):
			for j in range(steps + 1 - i):
				u, v = i / steps, j / steps
				w = 1 - u - v
				p = tuple(pa[k] * w + pb[k] * u + pc[k] * v for k in range(3))
				tu = uva[0] * w + uvb[0] * u + uvc[0] * v
				tv = uva[1] * w + uvb[1] * u + uvc[1] * v
				colour = tex.getpixel((int(tu * (tex.width - 1)) % tex.width, int((1 - tv) * (tex.height - 1)) % tex.height))[:3]
				key = tuple(min(res - 1, max(0, int((p[k] - mins[k]) / size))) for k in range(3))
				grid.setdefault(key, colour)
	return grid, mins, size


def quantise(grid):
	"""Snap voxel colours to the most common ones so the palette stays small (≤ 16)."""
	counts = defaultdict(int)
	for colour in grid.values():
		counts[colour] += 1
	palette = [c for c, _ in sorted(counts.items(), key=lambda kv: -kv[1])[:16]]
	def nearest(colour):
		return min(palette, key=lambda p: sum((x - y) ** 2 for x, y in zip(p, colour)))
	return {k: nearest(c) for k, c in grid.items()}, palette


def greedy_boxes(grid, res):
	used = set()
	boxes = []
	for key in sorted(grid):
		if key in used:
			continue
		colour = grid[key]
		x, y, z = key
		# grow along x
		x1 = x
		while (x1 + 1, y, z) in grid and (x1 + 1, y, z) not in used and grid[(x1 + 1, y, z)] == colour:
			x1 += 1
		# grow along y
		y1 = y
		while all((xx, y1 + 1, z) in grid and (xx, y1 + 1, z) not in used and grid[(xx, y1 + 1, z)] == colour for xx in range(x, x1 + 1)):
			y1 += 1
		# grow along z
		z1 = z
		while all((xx, yy, z1 + 1) in grid and (xx, yy, z1 + 1) not in used and grid[(xx, yy, z1 + 1)] == colour
				for xx in range(x, x1 + 1) for yy in range(y, y1 + 1)):
			z1 += 1
		for xx in range(x, x1 + 1):
			for yy in range(y, y1 + 1):
				for zz in range(z, z1 + 1):
					used.add((xx, yy, zz))
		boxes.append(((x, y, z), (x1 + 1, y1 + 1, z1 + 1), colour))
	return boxes


def main():
	ap = argparse.ArgumentParser()
	ap.add_argument("obj")
	ap.add_argument("model_json")
	ap.add_argument("palette_png")
	ap.add_argument("--res", type=int, default=40)
	ap.add_argument("--length", type=float, default=14.0, help="model units for the longest axis")
	ap.add_argument("--flip", action="store_true", help="rotate 180° about Y (muzzle was pointing the wrong way)")
	ap.add_argument("--max-elements", type=int, default=400)
	args = ap.parse_args()

	verts, uvs, tris = load_obj(args.obj)
	tex = Image.open(colormap_path(args.obj)).convert("RGB")
	grid, mins, size = voxelise(verts, uvs, tris, tex, args.res)
	grid, palette = quantise(grid)
	boxes = greedy_boxes(grid, args.res)
	if len(boxes) > args.max_elements:
		sys.exit(f"{len(boxes)} boxes > {args.max_elements}; rerun with a lower --res")

	unit = args.length / args.res  # model units per voxel
	xs, ys, zs = zip(*verts)
	extent = ((max(xs) - mins[0]) / size, (max(ys) - mins[1]) / size, (max(zs) - mins[2]) / size)
	centre = tuple(e * unit / 2 for e in extent)

	# Palette: accent → white (tinted); everything else its own texel.
	tinted = {c for c in palette if any(near(c, a, ACCENT_TOLERANCE) for a in ACCENT)}
	order = [c for c in palette]
	pal = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for i, c in enumerate(order):
		shade = (255, 255, 255) if c in tinted and c == max(tinted, key=sum) else ((200, 200, 200) if c in tinted else c)
		for y in range(16):
			pal.putpixel((i, y), shade + (255,))
	pal.save(args.palette_png)

	def to_model(voxel):
		x, y, z = voxel
		mx = x * unit - centre[0] + 8
		my = y * unit - centre[1] + 8
		mz = z * unit - centre[2] + 8
		if args.flip:
			mx, mz = 16 - mx, 16 - mz
		return mx, my, mz

	elements = []
	for (lo, hi, colour) in boxes:
		a = to_model(lo)
		b = to_model(hi)
		frm = [round(min(a[i], b[i]), 3) for i in range(3)]
		to = [round(max(a[i], b[i]), 3) for i in range(3)]
		col = order.index(colour)
		face = {"uv": [col, 0, col + 1, 1], "texture": "#0"}
		if colour in tinted:
			face["tintindex"] = 0
		elements.append({"from": frm, "to": to, "faces": {d: dict(face) for d in ("north", "east", "south", "west", "up", "down")}})

	model = {
		"credit": "Kenney Blaster Kit (CC0) blaster-b, converted by tools/obj2mc.py; tank faces tinted",
		"texture_size": [16, 16],
		"textures": {"0": "metacraft-rivals:item/paint_gun_palette", "particle": "metacraft-rivals:item/paint_gun_palette"},
		"elements": elements,
		"gui_light": "side",
		"display": {
			"thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
			"thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
			"firstperson_righthand": {"rotation": [0, 0, 0], "translation": [1, 2, 1], "scale": [0.6, 0.6, 0.6]},
			"firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [1, 2, 1], "scale": [0.6, 0.6, 0.6]},
			"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
			"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
			"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
		},
	}
	with open(args.model_json, "w") as f:
		json.dump(model, f, indent="\t")
		f.write("\n")
	print(f"{len(elements)} elements, {len(order)} colours ({len(tinted)} tinted), voxel {unit:.3f} units")


if __name__ == "__main__":
	main()
```

The uv `[col, 0, col + 1, 1]` addresses one texel of a 16×16 palette in the model's 0..16 uv space (`texture_size` 16 → one uv unit = one texel).

- [ ] **Step 3: Run it and check the output**

```
python3 mods/metacraft-rivals/tools/obj2mc.py mods/metacraft-rivals/tools/kenney/blaster-b.obj \
  mods/metacraft-rivals/src/main/resources/assets/metacraft-rivals/models/item/paint_gun.json \
  mods/metacraft-rivals/src/main/resources/assets/metacraft-rivals/textures/item/paint_gun_palette.png
```

Expected: a line like `N elements, K colours (2 tinted)` with N ≤ 400; if N > 400 rerun with `--res 32`. Re-render the (now obsolete) design-sheet SVG is not required; instead add to `docs/superpowers/specs/2026-09-11-paint-gun-design-sheet.md` a top note: "v2: the shipped model is converted from Kenney's blaster-b by `tools/obj2mc.py`; the parts table below describes the v1 hand-built model and is kept for reference."

- [ ] **Step 4: Run the tests** — Expected: all pass (20).

- [ ] **Step 5: Commit**

```bash
git add mods/metacraft-rivals/tools/obj2mc.py mods/metacraft-rivals/src/main/resources/assets/metacraft-rivals docs/superpowers/specs/2026-09-11-paint-gun-design-sheet.md
git commit -m "rivals: paint gun model converted from Kenney's blaster-b"
```

The display transforms are deliberately neutral; the controller tunes them against in-game screenshots after this plan runs (one edit per round). Do not spend time guessing them here.

---

### Task 7: Ink, reload and the action-bar ammo bar

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/gun/Ink.java`
- Create: `src/main/java/nu/metacraft/rivals/gun/InkHud.java`
- Modify: `src/main/java/nu/metacraft/rivals/gun/PaintGun.java`
- Modify: `src/main/java/nu/metacraft/rivals/Rivals.java` (`InkHud.init()` after `Recoil.init()`)
- Test: `RivalsGameTests`

**Interfaces:**
- Produces: `Ink.MAX = 40`, `Ink.REFILL_TICKS = 30`; `static int Ink.get(ItemStack)`, `static void Ink.set(ItemStack, int)`, `static boolean Ink.isRefilling(ItemStack, long now)`, `static void Ink.startRefill(ItemStack, long now)`, `static void Ink.finishIfDue(ItemStack, long now)`, `static void Ink.add(ItemStack, int)`.
- Produces: `InkHud.init()`; `static Component InkHud.bar(PaintColor color, int ink, boolean refilling, boolean squid)`; `static void InkHud.show(ServerPlayer player)`.
- Produces (for Task 8): `static boolean PaintGun.canShoot(Player)` hook — Task 8 adds the squid check there; in this task it always returns true.

- [ ] **Step 1: Write the failing test**

```java
	/** A fresh gun holds 40 ink, a shot costs one, an empty gun refills after the delay, own paint tops it up. */
	@GameTest
	public void inkDrainsRefillsAndTopsUp(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.MAGENTA));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "fresh gun is full");
		PaintGun.ITEM.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertValueEqual(Ink.get(gun), Ink.MAX - 1, "a shot costs one");
		Ink.set(gun, 0);
		long now = helper.getLevel().getServer().getTickCount();
		InteractionResult empty = PaintGun.ITEM.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(empty == InteractionResult.FAIL && Ink.isRefilling(gun, now), "empty gun starts refilling");
		Ink.finishIfDue(gun, now + Ink.REFILL_TICKS);
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "refilled after the delay");
		Ink.set(gun, 10);
		Ink.add(gun, 1);
		helper.assertValueEqual(Ink.get(gun), 11, "top-up adds");
		Ink.add(gun, 100);
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "top-up clamps");
		helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0).forEach(Entity::discard);
		helper.succeed();
	}

	/** The action-bar text has ten cells, one per four ink, and says REFILLING while a refill runs. */
	@GameTest
	public void inkBarText(GameTestHelper helper) {
		String full = InkHud.bar(PaintColor.LIME, Ink.MAX, false, false).getString();
		helper.assertTrue(full.startsWith("INK ") && full.contains("40/40") && full.chars().filter(c -> c == '█').count() == 10, "full bar: " + full);
		String half = InkHud.bar(PaintColor.LIME, 20, false, false).getString();
		helper.assertTrue(half.chars().filter(c -> c == '█').count() == 5 && half.chars().filter(c -> c == '░').count() == 5, "half bar: " + half);
		helper.assertTrue(InkHud.bar(PaintColor.LIME, 0, true, false).getString().contains("REFILLING"), "refilling text");
		helper.assertTrue(InkHud.bar(PaintColor.LIME, 5, false, true).getString().contains("SQUID"), "squid tag");
		helper.succeed();
	}
```

(imports `nu.metacraft.rivals.gun.Ink`, `nu.metacraft.rivals.gun.InkHud`.)

- [ ] **Step 2: Run to verify it fails** — compile error.

- [ ] **Step 3: Write `Ink`**

```java
package nu.metacraft.rivals.gun;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The gun's ink tank, kept in the stack's custom data so it survives Polymer's client mapping and
 * item moves. Absent means full. A refill is a deadline in server ticks; it completes on the first
 * tick at or past it.
 */
public final class Ink {
	public static final int MAX = 40;
	public static final int REFILL_TICKS = 30;
	static final String INK = "rivals_ink";
	static final String REFILL_UNTIL = "rivals_refill_until";

	private Ink() {}

	public static int get(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(INK, MAX);
	}

	public static void set(ItemStack stack, int ink) {
		int clamped = Math.max(0, Math.min(MAX, ink));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(INK, clamped));
	}

	public static void add(ItemStack stack, int amount) {
		set(stack, get(stack) + amount);
	}

	public static boolean isRefilling(ItemStack stack, long now) {
		long until = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLongOr(REFILL_UNTIL, -1L);
		return until >= 0 && now < until;
	}

	public static void startRefill(ItemStack stack, long now) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(REFILL_UNTIL, now + REFILL_TICKS));
	}

	/** Complete a refill whose deadline has passed: full tank, deadline cleared. */
	public static void finishIfDue(ItemStack stack, long now) {
		long until = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLongOr(REFILL_UNTIL, -1L);
		if (until < 0 || now < until) return;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putInt(INK, MAX);
			tag.remove(REFILL_UNTIL);
		});
	}
}
```

- [ ] **Step 4: Write `InkHud`**

```java
package nu.metacraft.rivals.gun;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.rivals.PaintColor;

import java.util.Optional;

/** The ammo bar: action-bar text for every player holding a gun, refreshed every ten ticks and after each shot. */
public final class InkHud {
	private static final int REFRESH_TICKS = 10;
	private static final int CELLS = 10;

	private InkHud() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_TICKS != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) show(player);
		});
	}

	/** Send the bar to a player holding a gun in either hand; silent otherwise. */
	public static void show(ServerPlayer player) {
		if (player.connection == null) return;
		ItemStack gun = heldGun(player);
		if (gun == null) return;
		Optional<PaintColor> color = PaintColor.byTeam(player.getTeam());
		if (color.isEmpty()) return;
		long now = player.level().getServer().getTickCount();
		player.sendSystemMessage(bar(color.get(), Ink.get(gun), Ink.isRefilling(gun, now), PaintGun.isSquid(player)), true);
	}

	static ItemStack heldGun(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof PaintGun) return stack;
		}
		return null;
	}

	public static Component bar(PaintColor color, int ink, boolean refilling, boolean squid) {
		StringBuilder text = new StringBuilder("INK ");
		if (refilling) {
			text.append("REFILLING…");
		} else {
			int filled = (int) Math.round(ink * (double) CELLS / Ink.MAX);
			text.append("█".repeat(filled)).append("░".repeat(CELLS - filled)).append(' ').append(ink).append('/').append(Ink.MAX);
		}
		if (squid) text.append("  SQUID");
		return Component.literal(text.toString()).withStyle(style -> style.withColor(TextColor.fromRgb(color.rgb)));
	}
}
```

- [ ] **Step 5: Wire the gun**

In `PaintGun`:

```java
	/** Task 8 turns this into the squid-form check; until then nobody is a squid. */
	public static boolean isSquid(Player player) {
		return false;
	}
```

and the success path of `use` becomes:

```java
		ItemStack gun = player.getItemInHand(hand);
		long now = serverLevel.getServer().getTickCount();
		Ink.finishIfDue(gun, now);
		if (Ink.isRefilling(gun, now)) return InteractionResult.FAIL;
		if (isSquid(player)) {
			actionBar(player, Component.literal("Can't shoot in squid form").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (Ink.get(gun) <= 0) {
			Ink.startRefill(gun, now);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 0.9f);
			player.getCooldowns().addCooldown(gun, Ink.REFILL_TICKS);
			if (player instanceof ServerPlayer serverPlayer) InkHud.show(serverPlayer);
			return InteractionResult.FAIL;
		}
		shoot(serverLevel, player, color.get());
		feel(serverLevel, player, color.get());
		Ink.add(gun, -1);
		player.getCooldowns().addCooldown(gun, COOLDOWN_TICKS);
		if (player instanceof ServerPlayer serverPlayer) InkHud.show(serverPlayer);
		return InteractionResult.SUCCESS;
```

with a helper replacing the inline no-team message too:

```java
	static void actionBar(Player player, Component text) {
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) serverPlayer.sendSystemMessage(text, true);
	}
```

Also in `inventoryTick`, before the dye logic: `Ink.finishIfDue(stack, level.getServer().getTickCount());` so a refill completes even while the player is not clicking. `Rivals.onInitialize()` adds `InkHud.init();` after `Recoil.init();`.

- [ ] **Step 6: Run the tests** — Expected: "All 22 required tests passed".

- [ ] **Step 7: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: ink tank with refill and an action-bar ammo bar"
```

---

### Task 8: Squid form and enemy-paint slowness

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/PlayerTick.java`
- Modify: `src/main/java/nu/metacraft/rivals/gun/PaintGun.java` (`isSquid` reads `PlayerTick`)
- Modify: `src/main/java/nu/metacraft/rivals/Rivals.java` (`PlayerTick.init()` after `InkHud.init()`)
- Test: `RivalsGameTests`

**Interfaces:**
- Produces: `PlayerTick.init()`; `static void PlayerTick.tick(Player player, long now)` (one player's per-tick logic, callable from tests); `static boolean PlayerTick.isSquid(Player)`; `static @Nullable PaintColor PlayerTick.paintUnder(Player)`.

- [ ] **Step 1: Write the failing test**

```java
	/** Sneaking on own paint is squid form (invisible, fast, no shooting); standing on enemy paint slows. */
	@GameTest
	public void squidFormAndEnemySlowness(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
		Player player = gunner(helper); // stands at relative (4, 3, 4), i.e. in the cell above that stone
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.MAGENTA));
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), Direction.UP, PaintColor.MAGENTA);
		helper.assertTrue(PlayerTick.paintUnder(player) == PaintColor.MAGENTA, "own paint under the player");
		player.setShiftKeyDown(true);
		PlayerTick.tick(player, 0);
		helper.assertTrue(PlayerTick.isSquid(player), "squid form on");
		helper.assertTrue(player.hasEffect(MobEffects.INVISIBILITY) && player.hasEffect(MobEffects.SPEED), "invisible and fast");
		InteractionResult shot = PaintGun.ITEM.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(shot == InteractionResult.FAIL, "no shooting as a squid");
		player.setShiftKeyDown(false);
		PlayerTick.tick(player, 1);
		helper.assertTrue(!PlayerTick.isSquid(player), "squid form off when not sneaking");
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), Direction.UP, PaintColor.LIME);
		PlayerTick.tick(player, 2);
		helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "enemy paint slows");
		helper.succeed();
	}
```

(imports `nu.metacraft.rivals.PlayerTick`, `net.minecraft.world.effect.MobEffects`.)

- [ ] **Step 2: Run to verify it fails** — compile error.

- [ ] **Step 3: Write `PlayerTick`**

```java
package nu.metacraft.rivals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.rivals.gun.Ink;
import nu.metacraft.rivals.gun.PaintGun;
import nu.metacraft.rivals.paint.PaintBlock;
import nu.metacraft.rivals.paint.PaintDisplays;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player paint effects, every tick. Sneaking in own-colour paint is squid form: invisible, fast,
 * refilling, unable to shoot. Standing in another colour slows. Effects are short and re-applied each
 * tick, so leaving the paint ends them within a second with no bookkeeping.
 */
public final class PlayerTick {
	private static final int EFFECT_TICKS = 15;
	private static final int TOPUP_EVERY = 5;
	private static final Set<UUID> SQUIDS = new HashSet<>();

	private PlayerTick() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) tick(player, now);
		});
	}

	public static boolean isSquid(Player player) {
		return SQUIDS.contains(player.getUUID());
	}

	/** The colour of the paint in the cell the player stands in (block paint or display quads), or null. */
	public static @Nullable PaintColor paintUnder(Player player) {
		if (!(player.level() instanceof ServerLevel level)) return null;
		BlockPos cell = player.blockPosition();
		BlockState state = level.getBlockState(cell);
		if (state.getBlock() instanceof PaintBlock paint) return paint.color;
		return PaintDisplays.of(level).colorAt(cell);
	}

	public static void tick(Player player, long now) {
		PaintColor under = paintUnder(player);
		Optional<PaintColor> own = PaintColor.byTeam(player.getTeam());
		boolean squid = under != null && own.isPresent() && under == own.get() && player.isShiftKeyDown();
		if (squid) {
			SQUIDS.add(player.getUUID());
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EFFECT_TICKS, 0, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, EFFECT_TICKS, 1, true, false, false));
		} else {
			SQUIDS.remove(player.getUUID());
		}
		if (under != null && own.isPresent() && under != own.get()) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_TICKS, 0, true, false, false));
		}
		if (under != null && own.isPresent() && under == own.get() && now % TOPUP_EVERY == 0) {
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack stack = player.getItemInHand(hand);
				if (stack.getItem() instanceof PaintGun) Ink.add(stack, squid ? 4 : 1);
			}
		}
	}
}
```

In `PaintGun`, `isSquid` becomes `return PlayerTick.isSquid(player);` (import `nu.metacraft.rivals.PlayerTick`). `Rivals.onInitialize()` adds `PlayerTick.init();` after `InkHud.init();`.

- [ ] **Step 4: Run the tests** — Expected: "All 23 required tests passed".

- [ ] **Step 5: Commit**

```bash
git add mods/metacraft-rivals/src
git commit -m "rivals: squid form on own paint, slowness on enemy paint"
```

---

### Task 9: README, credits, full check

**Files:**
- Modify: `README.md` (module)

- [ ] **Step 1: Update the README**

Add under "How it works": splash rays and particles; recoil; Kenney splat art with 32 variants per colour and the blockstate overrides; display quads for non-full blocks; the gloss shader and its shader-pack limit; the converted blaster (`tools/obj2mc.py`, `--flip` note); ink (40 shots, right-click refill, top-up on own paint), the action-bar bar; squid form (sneak on own paint) and enemy slowness. Add a "Credits" section: "Splat sprites and the blaster model are from Kenney (kenney.nl), CC0." Update the "Not yet" list (persisting display quads, arena, damage, Iris gloss). Keep the play/build/run sections accurate (22 game tests of ours).

- [ ] **Step 2: Full check**

Run: `./gradlew --offline mods:metacraft-rivals:build -x mods:metacraft-lib:test mods:metacraft-rivals:runGameTest dist:compileJava`
Expected: BUILD SUCCESSFUL, "All 23 required tests passed" (22 ours + always_pass).

- [ ] **Step 3: Commit**

```bash
git add mods/metacraft-rivals/README.md
git commit -m "rivals: README for v2 and Kenney credits"
```

The in-game screenshot loop (gun orientation, splat look, gloss, quads on stairs) runs after this plan, driven by the controller with the running dev server.
