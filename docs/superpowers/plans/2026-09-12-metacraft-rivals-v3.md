# Metacraft Rivals v3 — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gloss that actually renders, a squid form that changes how you play, blobby bouncing shots, and three more weapons (sprayer, charger, slosher).

**Architecture:** The gloss moves to the terrain shader pair. `PlayerTick` grows a per-player `SquidState` that applies and removes vanilla attribute modifiers (scale, speed, jump, step) plus wall swimming and enemy-ink penalties. `PaintBall` hides its snowball from clients and drives a Polymer item-display blob attached to itself, gains bounces and a droplet lifetime. `PaintGun` becomes `PaintWeapon` parameterised by a `Weapon` enum (shooter, sprayer, charger, slosher); the charger is a spyglass on the client so hold/release arrive as vanilla use packets. Models come from the Kenney converter (blaster-o, blaster-p) and one hand-built bucket.

**Tech Stack:** Java 25, Minecraft 26.2 (Mojang mappings), Fabric API 0.159.0+26.2, Polymer 0.17.5+26.2 (core, resource-pack, virtual-entity), Fabric GameTest API, Python 3 + Pillow for the converter.

**Spec:** `docs/superpowers/specs/2026-09-12-metacraft-rivals-v3-design.md` (v3) over the v2 and v1 specs.

## Global Constraints

- Versions pinned by `gradle.properties` on `dev` (`minecraft_version=26.2`, `polymer_version=0.17.5+26.2`); never edit them. Vanilla clients only; no client code; no mixins; no access widener; no datagen.
- Standalone module, never in `dist`. Branch `worktree-rivals`; commit messages `rivals: …`; no `Co-Authored-By` trailer; plain, separate git commands only (no `&&`, pipes or variables on a git line).
- Tabs everywhere (Java, JSON, GLSL, Python). Gradle always `--offline`. Game tests: `./gradlew --offline mods:metacraft-rivals:runGameTest` ("All N required tests passed" includes vanilla's always_pass; currently N = 25 with 24 of ours). Compile: `./gradlew --offline mods:metacraft-rivals:compileJava`. Never run the module `build` without `-x mods:metacraft-lib:test`.
- Anything under `assets/minecraft/...` is written with `builder.addData` in `RivalsPack`'s creation event; the mod's own assets ride in via `addModAssets`.
- Mock players come from `helper.makeMockPlayer(GameType)` (not `ServerPlayer`, no connection): every packet or action-bar send is guarded on `instanceof ServerPlayer sp && sp.connection != null`. Game tests may run concurrently in one level and the test world is reused between runs (teams persist; the `gunner()`/`team()` helpers in `RivalsGameTests` handle both).
- Verified 26.2 API names used below: `Attributes.SCALE/MOVEMENT_SPEED/JUMP_STRENGTH/STEP_HEIGHT` (`Holder<Attribute>`); `AttributeModifier(Identifier, double, AttributeModifier.Operation)` with `Operation.ADD_VALUE/ADD_MULTIPLIED_BASE/ADD_MULTIPLIED_TOTAL`; `LivingEntity.getAttribute(Holder)` → `AttributeInstance` with `addOrUpdateTransientModifier(AttributeModifier)`, `hasModifier(Identifier)`, `removeModifier(Identifier)`; `Entity.horizontalCollision` (public field); `LivingEntity.hurtServer(ServerLevel, DamageSource, float)`, `getHealth()`; `Level.damageSources().magic()`; `Item.getUseDuration(ItemStack, LivingEntity)`, `getUseAnimation(ItemStack)` → `ItemUseAnimation.SPYGLASS`, `releaseUsing(ItemStack, Level, LivingEntity, int timeLeft)`; `Player.startUsingItem(InteractionHand)`; `Items.SPYGLASS`; Polymer `EntityAttachment.ofTicking(ElementHolder, Entity)`, `DisplayElement.setScale(Vector3fc)`, `setInterpolationDuration(int)`, `setTeleportDuration(int)`, `startInterpolationIfDirty()`, `ElementHolder.getAttachment()` (null once destroyed), `PolymerEntity.sendPacketsTo(ServerPlayer)` (return false to hide the entity from clients).
- File paths are relative to `mods/metacraft-rivals/` unless they start with `docs/`.

---

### Task 1: Gloss on the terrain shaders

**Files:**
- Create: `src/main/resources/rivals_shaders/terrain.vsh`, `src/main/resources/rivals_shaders/terrain.fsh`
- Delete: `src/main/resources/rivals_shaders/block.vsh`, `src/main/resources/rivals_shaders/block.fsh`
- Modify: `src/main/java/nu/metacraft/rivals/pack/RivalsPack.java` (the two `addData` lines)
- Test: `RivalsGameTests.glossShaderCarriesTheMarkerGuard` (rewrite)

**Interfaces:** unchanged (`RivalsPack.shader(String)`).

- [ ] **Step 1: Rewrite the test**

```java
	/** The gloss lives in the terrain shader pair (what actually draws chunks in 26.2), keyed on the paint alpha marker. */
	@GameTest
	public void glossShaderCarriesTheMarkerGuard(GameTestHelper helper) {
		String fsh = new String(RivalsPack.shader("terrain.fsh"), StandardCharsets.UTF_8);
		String vsh = new String(RivalsPack.shader("terrain.vsh"), StandardCharsets.UTF_8);
		helper.assertTrue(fsh.contains("RIVALS_GLOSS") && fsh.contains("0.898") && fsh.contains("0.004"), "fragment shader guards on the marker alpha");
		helper.assertTrue(fsh.contains("sampleRGSS") && fsh.contains("#ifdef ALPHA_CUTOUT"), "vanilla terrain sampling and cutout kept");
		helper.assertTrue(vsh.contains("out vec3 viewPos") && vsh.contains("ChunkPosition"), "vertex shader exports the view position from the chunk-relative position");
		helper.assertTrue(RivalsPack.class.getResource("/rivals_shaders/block.fsh") == null, "the block shader override is gone");
		helper.succeed();
	}
```

- [ ] **Step 2: Run to verify it fails** (`compileJava` passes; `runGameTest` fails on the missing terrain resource).

- [ ] **Step 3: Write the terrain shaders**

Extract vanilla's files: `unzip -p ~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar assets/minecraft/shaders/core/terrain.vsh` (and `.fsh`). `terrain.vsh`: copy verbatim (converting indentation to tabs), add `out vec3 viewPos;` and, after `vec3 pos = …;`, `vec4 view = ModelViewMat * vec4(pos, 1.0); gl_Position = ProjMat * view; viewPos = view.xyz;` (replacing the original `gl_Position` line). `terrain.fsh`: copy verbatim (tabs), add `in vec3 viewPos;`, and change `main()` to:

```glsl
void main() {
	vec4 tex = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize));
	vec4 color = tex * vertexColor;
	color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
#ifdef ALPHA_CUTOUT
	if (color.a < ALPHA_CUTOUT) {
		discard;
	}
#endif
	// RIVALS_GLOSS: paint texels carry alpha 229/255 = 0.898 as a marker; the window admits 228..230
	// (three steps, filtering tolerance) and stays five steps clear of the nearest vanilla value (224).
	if (abs(tex.a - 0.898) < 0.004) {
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

(`GameTime` is in `globals.glsl`, already imported by vanilla's terrain.fsh.) In `RivalsPack` replace the two `block.*` `addData` lines with `terrain.vsh`/`terrain.fsh`; delete the block shader files; update the class javadoc.

- [ ] **Step 4: Run the tests** (expect 25 passed) and commit: `rivals: gloss moved to the terrain shaders that draw chunks`.

---

### Task 2: Squid form overhaul and enemy ink

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/SquidState.java`
- Modify: `src/main/java/nu/metacraft/rivals/PlayerTick.java`
- Test: `RivalsGameTests` (extend `squidFormAndEnemySlowness`; add `squidWallSwim`, `enemyInkDripDamage`)

**Interfaces:**
- Produces: `SquidState` with `static final Identifier SCALE_ID = Rivals.id("squid/scale")` (and `SPEED_ID`, `JUMP_ID`, `STEP_ID`, `NO_JUMP_ID = Rivals.id("ink/no_jump")`); `static void SquidState.enter(Player)`, `static void exit(Player)`, `static boolean isSquid(Player)`, `static void clearAll()`, `static void applyEnemyInk(Player)`, `static void clearEnemyInk(Player)`. `PlayerTick.isSquid` delegates to `SquidState.isSquid`.
- Keeps: `PlayerTick.tick(Player, long)`, `paintUnder(Player)`.

- [ ] **Step 1: Tests**

Extend `squidFormAndEnemySlowness` after the existing "invisible and fast" assertion:

```java
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		helper.assertTrue(scale != null && scale.hasModifier(SquidState.SCALE_ID) && scale.getValue() < 0.6, "squid is half size");
		helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SquidState.SPEED_ID), "squid is fast");
		helper.assertTrue(player.getAttribute(Attributes.JUMP_STRENGTH).hasModifier(SquidState.JUMP_ID), "squid hops");
		helper.assertTrue(player.getAttribute(Attributes.STEP_HEIGHT).hasModifier(SquidState.STEP_ID), "squid glides over steps");
```

and after the "squid form off" assertion: `helper.assertTrue(!player.getAttribute(Attributes.SCALE).hasModifier(SquidState.SCALE_ID) && !player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SquidState.SPEED_ID), "modifiers removed on exit");`. After the "enemy paint slows" assertion: `helper.assertTrue(player.getAttribute(Attributes.JUMP_STRENGTH).hasModifier(SquidState.NO_JUMP_ID), "enemy ink kills the jump");` and check Slowness amplifier 1: `helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 1, "Slowness II");`.

New tests:

```java
	/** Pushing against an own-colour painted wall while a squid lifts the player. */
	@GameTest
	public void squidWallSwim(GameTestHelper helper) {
		stoneFloor(helper, 5);
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(4, y, 2), Blocks.STONE);
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.MAGENTA));
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 2)), Direction.UP, PaintColor.MAGENTA); // floor under
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 2)), Direction.WEST, PaintColor.MAGENTA); // wall beside
		player.setShiftKeyDown(true);
		player.horizontalCollision = true;
		player.setDeltaMovement(0.1, 0, 0);
		PlayerTick.tick(player, 0);
		helper.assertTrue(SquidState.isSquid(player), "squid");
		helper.assertTrue(player.getDeltaMovement().y > 0.2, "lifted up the inked wall, dy=" + player.getDeltaMovement().y);
		helper.succeed();
	}

	/** Enemy ink drips: 1 damage every 20 ticks in survival, never below 1 health. */
	@GameTest
	public void enemyInkDripDamage(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.MAGENTA));
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), Direction.UP, PaintColor.LIME);
		float before = player.getHealth();
		PlayerTick.tick(player, 20);
		helper.assertTrue(player.getHealth() <= before - 1.0f, "hurt on a damage tick, health " + player.getHealth());
		player.setHealth(1.5f);
		PlayerTick.tick(player, 40);
		helper.assertTrue(player.getHealth() >= 1.0f, "never below one health");
		player.setHealth(before);
		helper.succeed();
	}
```

(imports: `net.minecraft.world.entity.ai.attributes.AttributeInstance`, `net.minecraft.world.entity.ai.attributes.Attributes`, `nu.metacraft.rivals.SquidState`.)

- [ ] **Step 2: Run to verify they fail** (compile error: `SquidState`).

- [ ] **Step 3: Write `SquidState`**

```java
package nu.metacraft.rivals;

/** Attribute modifiers and bookkeeping for squid form and enemy ink, applied/removed exactly once per player. */
public final class SquidState {
	public static final Identifier SCALE_ID = Rivals.id("squid/scale");
	public static final Identifier SPEED_ID = Rivals.id("squid/speed");
	public static final Identifier JUMP_ID = Rivals.id("squid/jump");
	public static final Identifier STEP_ID = Rivals.id("squid/step");
	public static final Identifier NO_JUMP_ID = Rivals.id("ink/no_jump");
	private static final Set<UUID> SQUIDS = new HashSet<>();

	public static boolean isSquid(Player player) { return SQUIDS.contains(player.getUUID()); }

	public static void enter(Player player) {
		if (!SQUIDS.add(player.getUUID())) return;
		modifier(player, Attributes.SCALE, SCALE_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.8, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, 0.2, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.STEP_HEIGHT, STEP_ID, 0.5, AttributeModifier.Operation.ADD_VALUE);
	}

	public static void exit(Player player) {
		if (!SQUIDS.remove(player.getUUID())) return;
		remove(player, Attributes.SCALE, SCALE_ID);
		remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
		remove(player, Attributes.JUMP_STRENGTH, JUMP_ID);
		remove(player, Attributes.STEP_HEIGHT, STEP_ID);
	}

	public static void applyEnemyInk(Player player) { modifier(player, Attributes.JUMP_STRENGTH, NO_JUMP_ID, -1.0, AttributeModifier.Operation.ADD_VALUE); }
	public static void clearEnemyInk(Player player) { remove(player, Attributes.JUMP_STRENGTH, NO_JUMP_ID); }

	/** Server stop: forget everyone (modifiers are transient and die with the entities). */
	public static void clearAll() { SQUIDS.clear(); }

	private static void modifier(Player player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && !instance.hasModifier(id)) instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
	}

	private static void remove(Player player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) instance.removeModifier(id);
	}
}
```

- [ ] **Step 4: Rewrite `PlayerTick.tick`**

```java
	private static final int DRIP_EVERY = 20;
	private static final float DRIP_DAMAGE = 1.0f;
	private static final double WALL_SWIM_SPEED = 0.28;

	public static void tick(Player player, long now) {
		if (player.isSpectator()) { SquidState.exit(player); SquidState.clearEnemyInk(player); return; }
		PaintColor under = paintUnder(player);
		Optional<PaintColor> own = PaintColor.byTeam(player.getTeam());
		boolean inOwn = under != null && own.isPresent() && under == own.get();
		boolean inEnemy = under != null && own.isPresent() && under != own.get();
		boolean squid = inOwn && player.isShiftKeyDown();
		if (squid) {
			SquidState.enter(player);
			keep(player, MobEffects.INVISIBILITY, 0);
			if (player.horizontalCollision && paintedWallBeside(player, own.get())) {
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(v.x, WALL_SWIM_SPEED, v.z);
				player.hurtMarked = true;
			}
		} else {
			SquidState.exit(player);
		}
		if (inEnemy) {
			keep(player, MobEffects.SLOWNESS, 1);
			SquidState.applyEnemyInk(player);
			if (now % DRIP_EVERY == 0 && !player.isCreative() && player.getHealth() - DRIP_DAMAGE >= 1.0f
					&& player.level() instanceof ServerLevel level) {
				player.hurtServer(level, level.damageSources().magic(), DRIP_DAMAGE);
			}
		} else {
			SquidState.clearEnemyInk(player);
		}
		if (inOwn && now % TOPUP_EVERY == 0) { … as before, Ink.add(stack, squid ? 4 : 1) for every held PaintGun/PaintWeapon … }
	}

	/** Is there own-colour paint on a wall face the player is pushing against (any of the four horizontal neighbours)? */
	static boolean paintedWallBeside(Player player, PaintColor own) {
		if (!(player.level() instanceof ServerLevel level)) return false;
		BlockPos feet = player.blockPosition();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos wall = feet.relative(d);
			// Block paint sits in the player's own cell with the face flag pointing at the wall …
			BlockState cell = level.getBlockState(feet);
			if (cell.getBlock() instanceof PaintBlock paint && paint.color == own && cell.getValue(MultifaceBlock.getFaceProperty(d))) return true;
			BlockState head = level.getBlockState(feet.above());
			if (head.getBlock() instanceof PaintBlock paint && paint.color == own && head.getValue(MultifaceBlock.getFaceProperty(d))) return true;
			// … or as quads keyed at the cell in front of the wall face (the player's cell).
			if (PaintDisplays.of(level).colorAt(feet) == own && Painter.paintable(level.getBlockState(wall))) return true;
		}
		return false;
	}
```

`isSquid` in `PlayerTick` delegates to `SquidState.isSquid`; the `SERVER_STOPPING` hook calls `SquidState.clearAll()`. Note the speed comes from the attribute modifier now, so drop `keep(player, MobEffects.SPEED, 1)`; keep invisibility. Update the class javadoc. `Player.hurtServer` on a mock survival player reduces health (no armour); `isCreative()` exists on Player.

- [ ] **Step 5: Run the tests** (expect 27 passed); commit `rivals: squid form shrinks, speeds and swims up inked walls; enemy ink slows, grounds and drips`.

---

### Task 3: Blobby, bouncy paint balls

**Files:**
- Modify: `src/main/java/nu/metacraft/rivals/gun/PaintBall.java`
- Create: `src/main/resources/assets/metacraft-rivals/models/item/blob.json`, `src/main/resources/assets/metacraft-rivals/items/blob.json`, `src/main/resources/assets/metacraft-rivals/textures/item/blob.png` (16×16 opaque white, generated with Pillow or committed from `SplatArt.whiteMask`-style code — simplest: a Python one-liner writing a white 16×16 PNG)
- Test: `RivalsGameTests` (`paintBallBouncesOnce`, `dropletSplashesAfterLifetime`, `blobFollowsTheBall`)

**Interfaces:**
- Produces: `PaintBall(ServerLevel level, LivingEntity shooter, PaintColor color, int bounces, int lifetime)` (the old 3-arg constructor delegates with `bounces = 1, lifetime = 0`); `int bouncesLeft()`; `@Nullable ElementHolder blobHolder()`; constants `BOUNCE_RESTITUTION = 0.45`, `GRAVITY = 0.05`; override `getDefaultGravity()` to return `GRAVITY`.

- [ ] **Step 1: Tests**

```java
	/** A shooter ball's first floor hit paints, then bounces up at reduced speed; the second impact ends it. */
	@GameTest
	public void paintBallBouncesOnce(GameTestHelper helper) {
		stoneFloor(helper, 5);
		PaintBall ball = new PaintBall(helper.getLevel(), gunner(helper), PaintColor.CYAN, 1, 0);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 4, 2.5));
		ball.setPos(at.x, at.y, at.z);
		ball.setDeltaMovement(0, -0.8, 0);
		helper.getLevel().addFreshEntity(ball);
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(!ball.isRemoved(), "still flying after the first impact");
			helper.assertValueEqual(ball.bouncesLeft(), 0, "one bounce used");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).is(PaintBlocks.of(PaintColor.CYAN)), "first impact painted");
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(ball.isRemoved(), "gone after the second impact");
			helper.succeed();
		});
	}

	/** A sprayer droplet with a 12-tick lifetime splashes the floor beneath it when time runs out. */
	@GameTest
	public void dropletSplashesAfterLifetime(GameTestHelper helper) {
		stoneFloor(helper, 5);
		PaintBall drop = new PaintBall(helper.getLevel(), gunner(helper), PaintColor.LIME, 0, 12);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 3.2, 2.5));
		drop.setPos(at.x, at.y, at.z);
		drop.setDeltaMovement(0, 0.02, 0); // hovering: only the lifetime can end it
		drop.setNoGravity(true);
		helper.getLevel().addFreshEntity(drop);
		helper.runAfterDelay(16, () -> {
			helper.assertTrue(drop.isRemoved(), "droplet expired");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).is(PaintBlocks.of(PaintColor.LIME)), "floor under the droplet painted");
			helper.succeed();
		});
	}

	/** The blob display follows the ball and is torn down with it. */
	@GameTest
	public void blobFollowsTheBall(GameTestHelper helper) {
		PaintBall ball = new PaintBall(helper.getLevel(), gunner(helper), PaintColor.MAGENTA, 0, 0);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 5, 2.5));
		ball.setPos(at.x, at.y, at.z);
		ball.setNoGravity(true);
		helper.getLevel().addFreshEntity(ball);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(ball.blobHolder() != null && ball.blobHolder().getAttachment() != null, "blob attached while flying");
			ball.discard();
		});
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(ball.blobHolder() == null || ball.blobHolder().getAttachment() == null, "blob gone with the ball");
			helper.succeed();
		});
	}
```

(`Entity.setNoGravity(boolean)`, `isRemoved()`, `discard()` exist; `runAfterDelay(long, Runnable)` is on `GameTestHelper` — check its exact name with `javap` if the compile complains: the v2 tests already use it.)

- [ ] **Step 2: Run to verify they fail** (compile error on the new constructor).

- [ ] **Step 3: Implement**

`PaintBall`:
- fields `private int bounces; private int lifetime; private int age; private @Nullable ElementHolder blob; private @Nullable ItemDisplayElement blobElement;`
- constructor `(level, shooter, color, bounces, lifetime)`; old constructor delegates `(…, 1, 0)`.
- `getDefaultGravity()` → `GRAVITY` (0.05).
- `sendPacketsTo(ServerPlayer)` → `false` (the snowball itself is never sent; only the blob is seen). Keep `getPolymerEntityType` → `EntityTypes.SNOWBALL` for safety.
- `tick()`: `super.tick()`; if `blob == null && !isRemoved()` create it (`ElementHolder`, `ItemDisplayElement` with item = stick + `ITEM_MODEL = Rivals.id("blob")` + `DYED_COLOR`, `setItemDisplayContext(ItemDisplayContext.FIXED)`, `setInterpolationDuration(1)`, `setTeleportDuration(1)`, `holder.addElement(element)`, `EntityAttachment.ofTicking(holder, this)`); then every tick set the wobble scale `s = 0.55; w = 0.12 * sin(age * 1.1)`; `element.setScale(new Vector3f(s + w, s - w, s + w)); element.startInterpolationIfDirty();`; `age++`; if `lifetime > 0 && age >= lifetime` → `expire()`.
- `expire()`: clip a ray from `position()` straight down 4 blocks (COLLIDER/NONE, this); if it hits a block → `Painter.splash(level, hit.getLocation(), hit.getBlockPos(), hit.getDirection(), color, random, this)`; then `discard()`.
- `remove(RemovalReason)` override (or `onRemoval`): destroy the holder (`blob.destroy(); blob = null;`) then `super`.
- `onHitBlock(hit)`: `super.onHitBlock(hit)` — careful: `Snowball.onHit` discards after `onHitBlock`; to bounce we must NOT let `Snowball.onHit` run its discard. Override `onHit(HitResult)` instead: if `hit` is a `BlockHitResult` and `bounces > 0`: splash, `bounces--`, reflect velocity: `Vec3 v = getDeltaMovement(); Vec3 n = normal(hit.getDirection()); Vec3 r = v.subtract(n.scale(2 * v.dot(n))).scale(BOUNCE_RESTITUTION); setDeltaMovement(r); setPos(hit.getLocation().add(n.scale(0.05)));` and return without calling super. Otherwise fall through to the v2 behaviour (`super.onHit(hit)` which calls our `onHitBlock`/`onHitEntity` then discards). Adjust `onHitBlock` to only splash (no bounce logic) so the no-bounce path is unchanged. The `hitTargetOrDeflectSelf` path in `Projectile` calls `onHit`; verify with `javap -c` on `Snowball`/`ThrowableProjectile` if in doubt which method the block hit reaches.
- `bouncesLeft()` and `blobHolder()` accessors.

Assets: `models/item/blob.json`: a 10×10×10 cube centred at 8 (`from [3,3,3] to [13,13,13]`) plus six chamfer boxes `[4,2,4]-[12,3,12]` style on each side (top/bottom/north/south/east/west, 8 units wide and 1 unit thick) so the silhouette reads round; every face `"texture": "#0", "tintindex": 0, "uv": [0,0,16,16]`; textures `"0": "metacraft-rivals:item/blob"`; `display.fixed` identity. `items/blob.json`: `minecraft:model` for `metacraft-rivals:item/blob` with a `minecraft:dye` tint, default white. `textures/item/blob.png`: 16×16 opaque white.

- [ ] **Step 4: Run the tests** (expect 30 passed); commit `rivals: paint balls are blobs that wobble and bounce; droplets expire onto the floor`.

---

### Task 4: `PaintWeapon` and the shooter/sprayer/slosher fire modes

**Files:**
- Create: `src/main/java/nu/metacraft/rivals/gun/Weapon.java`, `src/main/java/nu/metacraft/rivals/gun/PaintWeapon.java`
- Modify: `gun/PaintGun.java` → delete after moving; `gun/InkHud.java` (`heldGun` matches `PaintWeapon`), `PlayerTick.java` (top-up matches `PaintWeapon`), `RivalsCommands.java` (`gun [weapon]`, `kit`), `Rivals.java` (`PaintWeapon.register()`), lang file (names), existing tests referencing `PaintGun.ITEM` → `PaintWeapon.of(Weapon.SHOOTER)`.
- Test: `RivalsGameTests` (`sprayerThrowsThreeDroplets`, `slosherThrowsFourInAFan`, `kitGivesEveryWeapon`; existing gun tests keep passing via the shooter)

**Interfaces:**
- Produces: `enum Weapon { SHOOTER("paint_gun"), SPRAYER("sprayer"), CHARGER("charger"), SLOSHER("slosher") }` with `id`, `displayName`, `inkPerShot`, `cooldownTicks`, `velocity`, `inaccuracy`, `kickPitch`; `static Optional<Weapon> byId(String)`.
- Produces: `PaintWeapon extends Item implements PolymerItem` with `static PaintWeapon of(Weapon)`, `static void register()`, `Weapon weapon()`, `static ItemStack withTankColor(ItemStack, PlayerTeam)`, `static boolean isSquid(Player)`, `static void actionBar(Player, Component)`, `void fire(ServerLevel, Player, PaintColor)` (per weapon), and the shared `use` preamble (team, refill, squid, ink) from v2. The charger's `fire` is a stub in this task (Task 5 fills it in); its `use` starts using the item.
- `PaintGun.ID` stays as the shooter's identifier (`metacraft-rivals:paint_gun`) so the existing model/item definition keep working.

- [ ] **Step 1: Tests**

```java
	@GameTest
	public void sprayerThrowsThreeDroplets(GameTestHelper helper) {
		Player player = gunner(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.SPRAYER)));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.LIME));
		InteractionResult result = PaintWeapon.of(Weapon.SPRAYER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS, "sprays");
		List<PaintBall> drops = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0);
		helper.assertValueEqual(drops.size(), 3, "three droplets");
		helper.assertValueEqual(Ink.get(player.getItemInHand(InteractionHand.MAIN_HAND)), Ink.MAX - Weapon.SPRAYER.inkPerShot, "ink cost");
		drops.forEach(Entity::discard);
		helper.succeed();
	}

	@GameTest
	public void slosherThrowsFourInAFan(GameTestHelper helper) { … same shape: 4 balls, ink cost Weapon.SLOSHER.inkPerShot, and assert the four horizontal velocity directions are pairwise different (compare yaw of getDeltaMovement) … }

	@GameTest
	public void kitGivesEveryWeapon(GameTestHelper helper) {
		Player player = gunner(helper);
		player.getInventory().clearContent();
		int given = PaintWeapon.giveKit(player);
		helper.assertValueEqual(given, Weapon.values().length, "one of each");
		for (Weapon weapon : Weapon.values()) {
			helper.assertTrue(player.getInventory().contains(new ItemStack(PaintWeapon.of(weapon))), "has " + weapon.id);
		}
		helper.succeed();
	}
```

(`Inventory.clearContent()`, `contains(ItemStack)` exist.) Rename existing test references from `PaintGun.ITEM` to `PaintWeapon.of(Weapon.SHOOTER)` and `PaintGun.withTankColor` to `PaintWeapon.withTankColor`.

- [ ] **Step 2: Run to verify they fail** (compile errors).

- [ ] **Step 3: Implement**

`Weapon` enum (values per the spec table: SHOOTER 1 ink / 4 ticks / v1.8 / spread 2 / kick −2.5; SPRAYER 1 / 4 / v0.9 / spread 9 / kick −1; CHARGER 4 base / 20 / – / – / kick −6; SLOSHER 15 / 14 / v1.1 / spread 0 / kick −3). `PaintWeapon.fire` per weapon:

- SHOOTER: one `PaintBall(level, player, color, 1, 0)` shot with `shootFromRotation(player, xRot, yRot, 0, velocity, inaccuracy)`.
- SPRAYER: three `PaintBall(level, player, color, 0, 12)` with spread 9.
- SLOSHER: four `PaintBall(level, player, color, 0, 0)` with `shootFromRotation(player, xRot - 20, yRot + offset, 0, 1.1f, 0f)` for offsets −15, −5, 5, 15; each ball has splat radius 2 — add a `radius` field to `PaintBall` passed to a new `Painter.splash(…, int radius, …)` overload (the 3×3 becomes `RADIUS = radius`); default radius 1 elsewhere; droplets use radius 0 (`splat` with radius 0 paints just the struck face).
- CHARGER: `use` → `player.startUsingItem(hand); return InteractionResult.CONSUME;` after the shared preamble (no ink taken yet); `getUseDuration` → 72000; `getUseAnimation` → `ItemUseAnimation.SPYGLASS`; `releaseUsing` → Task 5 (stub returns false here).
- `feel` takes the weapon's kick (Recoil.kick gains a pitch parameter) and, for the slosher, an extra `SLIME_BLOCK_STEP` layer at pitch 0.6.
- `getPolymerItem`: `Items.SPYGLASS` for CHARGER, `Items.WARPED_FUNGUS_ON_A_STICK` otherwise. `getPolymerItemModel`: `Rivals.id(weapon.id)`.
- `register()` registers all four items (`metacraft-rivals:<id>`); `of(Weapon)`; `giveKit(Player)` adds one of each (dropping if full) and returns the count.
- Commands: `gun` with an optional `StringArgumentType.word()` argument resolved by `Weapon.byId` (default SHOOTER; unknown → failure message listing ids) and `kit`.
- Lang: `item.metacraft-rivals.sprayer = Paint Sprayer`, `charger = Paint Charger`, `slosher = Paint Slosher`.
- Item definitions for sprayer/charger/slosher point at models `metacraft-rivals:item/<id>` with the dye tint (the models themselves come in Task 6; until then reuse the shooter's model by pointing the definitions at `metacraft-rivals:item/paint_gun` and note it — Task 6 flips them).

- [ ] **Step 4: Run the tests** (expect 33 passed); commit `rivals: PaintWeapon with shooter, sprayer and slosher fire modes; /rivals gun <weapon> and kit`.

---

### Task 5: The charger

**Files:**
- Modify: `gun/PaintWeapon.java` (`releaseUsing`, charger fire), `paint/Painter.java` (`static int line(ServerLevel, Vec3 from, Vec3 to, PaintColor, RandomSource, @Nullable Entity)`)
- Test: `RivalsGameTests` (`chargerPaintsALineUnderTheScan`, `chargerIgnoresShortRelease`)

**Interfaces:**
- Produces: `Painter.line(...)`: dust every 0.5 blocks along the segment, then for every whole block position along it a downward clip of 6 blocks painting the face hit (`paintFace`), and a `splash` at the far end if the segment ended on a block; returns cells changed. `PaintWeapon.charge(ItemStack, LivingEntity, int timeLeft)` → charge in [0,1]; `static int CHARGE_FULL_TICKS = 20`, `MIN_CHARGE_TICKS = 5`.

- [ ] **Step 1: Tests**

```java
	/** Releasing a charged charger paints the floor under the scanned line and splats where it ends. */
	@GameTest
	public void chargerPaintsALineUnderTheScan(GameTestHelper helper) {
		stoneFloor(helper, 7); // floor at y=1, x/z 0..6
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(6, y, 3), Blocks.STONE); // end wall
		Player player = gunner(helper);
		ItemStack charger = new ItemStack(PaintWeapon.of(Weapon.CHARGER));
		player.setItemInHand(InteractionHand.MAIN_HAND, charger);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.CYAN));
		Vec3 at = helper.absoluteVec(new Vec3(0.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(-90f); // look +X
		player.setXRot(0f);
		boolean fired = PaintWeapon.of(Weapon.CHARGER).releaseUsing(charger, helper.getLevel(), player, 72000 - PaintWeapon.CHARGE_FULL_TICKS);
		helper.assertTrue(fired, "full charge fires");
		int painted = 0;
		for (int x = 1; x <= 5; x++) {
			if (helper.getBlockState(new BlockPos(x, 2, 3)).is(PaintBlocks.of(PaintColor.CYAN))) painted++;
		}
		helper.assertTrue(painted >= 3, "floor painted along the line, got " + painted);
		helper.assertTrue(helper.getBlockState(new BlockPos(5, 2, 3)).getValue(MultifaceBlock.getFaceProperty(Direction.EAST)), "end wall splatted");
		helper.assertValueEqual(Ink.get(charger), Ink.MAX - 12, "full charge costs 12");
		helper.succeed();
	}

	@GameTest
	public void chargerIgnoresShortRelease(GameTestHelper helper) {
		Player player = gunner(helper);
		ItemStack charger = new ItemStack(PaintWeapon.of(Weapon.CHARGER));
		player.setItemInHand(InteractionHand.MAIN_HAND, charger);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.CYAN));
		boolean fired = PaintWeapon.of(Weapon.CHARGER).releaseUsing(charger, helper.getLevel(), player, 72000 - 2);
		helper.assertTrue(!fired && Ink.get(charger) == Ink.MAX, "a tap does nothing and costs nothing");
		helper.succeed();
	}
```

(`Entity.setYRot/setXRot` exist; yaw −90 looks toward +X in Minecraft.)

- [ ] **Step 2: Run to verify they fail.**

- [ ] **Step 3: Implement**

`releaseUsing(stack, level, entity, timeLeft)`: `held = getUseDuration(stack, entity) - timeLeft`; if `held < MIN_CHARGE_TICKS` return false; `charge = min(1, held / (float) CHARGE_FULL_TICKS)`; shared preamble checks (team, refill, squid; ink ≥ cost where `cost = round(4 + 8 * charge)`, else start refill); `range = 10 + 30 * charge`; `from = entity.getEyePosition()`, `to = from.add(look.scale(range))`; `hit = level.clip(new ClipContext(from, to, COLLIDER, NONE, entity))`; `end = hit.getType() == BLOCK ? hit.getLocation() : to`; `Painter.line(level, from, end, color, random, entity)` then if block hit `Painter.splash(level, end, hit.getBlockPos(), hit.getDirection(), color, random, entity)`; ink −cost; cooldown 20; `Recoil.kick(player, -6 * charge)`; feel sounds; `InkHud.show`. Return true.

`Painter.line`: step along the segment in 0.5-block steps emitting 2 dust particles each; for each distinct `BlockPos.containing(point)` along it, clip straight down 6 blocks (from the point) and `paintFace(hitPos, hitDirection)` when it hits; return cells changed.

- [ ] **Step 4: Run the tests** (expect 35 passed); commit `rivals: charger — hold the spyglass to charge, release for a hitscan line that paints the floor under its trail`.

---

### Task 6: Weapon models and assets

**Files:**
- Modify: `tools/obj2mc.py` (`--accent "r,g,b;r,g,b"` option)
- Create (generated, committed): `models/item/sprayer.json` + `textures/item/sprayer_palette.png` (from `tools/kenney/blaster-o.obj`), `models/item/charger.json` + `textures/item/charger_palette.png` (from `blaster-p.obj`), hand-built `models/item/slosher.json` + `textures/item/slosher_palette.png`
- Modify: item definitions `items/sprayer.json`, `items/charger.json`, `items/slosher.json` → their own models
- Test: extend `gunModelAssetsArePresent` to loop over all four weapons (definition, model, palette present; elements ≤ 400; some `tintindex 0`).

Steps: add the accent option (defaults to blaster-b's oranges; for blaster-o pass the greens `97,203,139;…`, for blaster-p the greens/greys — inspect `python3 -c` colour histograms of each mesh's colormap usage first and pick the largest non-grey family as the tank); run the converter twice with `--res 40` (fall back to 32 if > 400 elements), copying the neutral display transforms from `paint_gun.json` (they were right in game for the same frame). Bucket: 6 boxes (body 8×7×8 tapering via two stacked boxes, rim, handle arc from three thin boxes), body faces `tintindex 0`, palette texels: white (tinted), dark grey rim, light grey handle. Run the tests (expect 35 passed); commit `rivals: sprayer, charger and slosher models`.

---

### Task 7: README, full check

Update `README.md`: weapons table (`/rivals gun <shooter|sprayer|charger|slosher>`, `/rivals kit`), squid form and enemy ink rules, the bounce, the gloss now on the terrain shader, and the test count. Run `./gradlew --offline mods:metacraft-rivals:build -x mods:metacraft-lib:test mods:metacraft-rivals:runGameTest dist:compileJava` (BUILD SUCCESSFUL, all tests passed). Commit `rivals: README for v3`.

## Addendum (2026-09-12, user): arm swing only for the slosher

The shooter, sprayer and charger must not swing the arm on use; the slosher (bucket) must. In `PaintWeapon.use` return `InteractionResult.CONSUME` for the non-swinging weapons (the client's own prediction on a warped fungus on a stick is PASS, and CONSUME makes the server swing nobody) and `InteractionResult.SUCCESS_SERVER` for the slosher (the server broadcasts the swing, including to the thrower). Tests that compared `use(...)` to `InteractionResult.SUCCESS` assert `result.consumesAction()` instead; `slosherThrowsFourInAFan` additionally asserts the result is `SUCCESS_SERVER`. This applies to Task 4 (and to the charger's `use` in Task 5).
