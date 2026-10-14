package nu.metacraft.booklet.clienttest;

import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import eu.pb4.polydecorations.block.DecorationsBlocks;
import eu.pb4.polydecorations.block.extension.AttachedSignPostBlock;
import eu.pb4.polydecorations.block.extension.SignPostBlockEntity;
import eu.pb4.polydecorations.block.extension.WallAttachedLanternBlock;
import eu.pb4.polydecorations.block.item.MailboxBlock;
import eu.pb4.polydecorations.canvas.CanvasData;
import eu.pb4.polydecorations.canvas.CanvasPixels;
import eu.pb4.polydecorations.entity.CanvasEntity;
import eu.pb4.polydecorations.entity.DecorationsEntities;
import eu.pb4.polydecorations.entity.FirstLeashFenceKnotEntity;
import eu.pb4.polydecorations.item.DecorationsDataComponents;
import eu.pb4.polydecorations.item.DecorationsItems;
import eu.pb4.simpleimagerenderer.renderer.RegionImageRenderer;
import eu.pb4.simpleimagerenderer.renderer.RendererSettings;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.content.SpotPlacements;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.qol.silence_mobs.SilenceMobs;
import nu.metacraft.qol.void_anchor.VoidAnchorBlocks;
import nu.metacraft.qol.void_anchor.rift.Rift;
import nu.metacraft.qol.void_anchor.rift.RiftStyle;

/**
 * The guidebook's page images, the way Patbox makes PolyFactory's: each scene is built in a flat
 * world and rendered isometric, on a transparent background, with his
 * <a href="https://github.com/Patbox/SimpleImageRenderer">Simple Image Renderer</a> — a client-only
 * tool, here only in the client test runtime.
 *
 * <pre>
 *   METACRAFT_BOOKLET_RENDER=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
 * </pre>
 * writes {@code build/booklet-renders/<name>.png}; {@code
 * tools/booklet_images.py} trims them into {@code assets/metacraft/textures/booklet/image/}. Without
 * the variable this does nothing: it makes pictures, it checks nothing.
 */
public final class BookletRenders implements FabricClientGameTest {
	private static final int SIZE = 768;
	/** The isometric view: 30° down, 45° round, as PolyFactory's images are. */
	private static final int PITCH = -30, YAW = 45;

	private record Scene(String name, BlockPos from, BlockPos to, int scale) {}

	/** Entities drawn mid-swing, whatever they are doing: see {@code MidSwingMixin}. */
	public static final Set<UUID> MID_SWING = ConcurrentHashMap.newKeySet();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!"1".equals(System.getenv("METACRAFT_BOOKLET_RENDER"))) return;
		// build/booklet-renders: outside the run directory, which the harness clears between runs.
		Path out = FabricLoader.getInstance().getGameDir().resolve("../../booklet-renders").normalize();

		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25603");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				// The Tester is in the sewing picture: in front of the stand, a patch in hand, aiming at
				// its chest, so ovvar shows the washed-out preview where the patch would go.
				server.runCommand("gamemode survival Tester");
				server.runCommand("item replace entity Tester weapon.mainhand with ovvar:patch_itk");
				server.runCommand("tp Tester 40.5 -60 22.3 186 14");

				server.runOnServer(mc -> build(mc.overworld()));
				buildBrewing(server);
				// Mail in the mailboxes, so their flags are up.
				for (BlockPos box : List.of(new BlockPos(50, -59, 20), new BlockPos(88, -59, 21))) {
					server.runCommand("data merge block " + box.getX() + " " + box.getY() + " " + box.getZ()
							+ " {inventory:[{uuid:[I;1,2,3,4],Items:[{Slot:0b,id:\"minecraft:paper\",count:1}]}]}");
				}
				ctx.waitTicks(100);        // ovvar builds the patch combinations the mannequins wear
				// A pack is only pushed to a player whose own ovve outgrew it; nobody here wears one, so ask.
				server.runCommand("execute as Tester run ovvar reload");
				acceptResourcePack(ctx);
				conn.waitForClientboundPackets();
				ctx.waitTicks(100);        // spawn interpolation, stand sprites, the pack's textures

				Files.createDirectories(out);
				for (Scene scene : SCENES) Files.createDirectories(out.resolve(scene.name()).getParent());
				for (Scene scene : SCENES) {
					Path file = out.resolve(scene.name() + ".png");
					ctx.runOnClient(client -> {
						RendererSettings settings = RendererSettings.defaultSettings.clone();
						settings.pitch = PITCH;
						settings.yaw = YAW;
						settings.scale = scene.scale();
						var renderer = new RegionImageRenderer(client, SIZE, SIZE, client.level, BlockBox.of(scene.from(), scene.to()), false, false);
						settings.updateMatrix(renderer);
						client.gui.setScreen(new RenderScreen(renderer, file));
					});
					ctx.waitFor(client -> !(client.gui.screen() instanceof RenderScreen), 20 * 30);
					ctx.waitTicks(5);
				}
				ctx.waitTicks(20);
				for (Scene scene : SCENES) {
					if (!Files.exists(out.resolve(scene.name() + ".png"))) throw new AssertionError("no render of " + scene.name());
				}
				stash(ctx, server, conn, out.resolve("ovvar/stash.png"));
				Files.createDirectories(out.resolve("recipe"));
				for (var recipe : RECIPES.entrySet()) {
					recipe(ctx, server, conn, out.resolve("recipe/" + recipe.getKey() + ".png"), recipe.getValue());
				}
				System.out.println("[metacraft-booklet] rendered " + SCENES.size() + " page image(s) to " + out.toAbsolutePath());
			} catch (IOException e) {
				throw new AssertionError(e);
			}
		}
	}

	/**
	 * The stash menu, cut out of a screenshot: the Tester is given a design and some patches, opens
	 * {@code /ovvar stash} at GUI scale 1, so the menu is drawn at its own pixels, and the top part
	 * of the chest screen (the wardrobe; the Tester's inventory below it is left out) is kept.
	 */
	private static void stash(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, Path file) {
		server.runCommand("gamemode survival Tester");
		// /ovvar give needs a player to run it (the console is refused), and each gift is a write to the
		// wardrobe store that the next one must see, so they go one at a time.
		server.runCommand("execute as Tester run ovvar give data itk it data spiken slaggan");
		ctx.waitTicks(20);
		for (String patch : List.of("itk", "tmeit", "jgs", "in", "kommn", "rivals", "nyckeln0x1", "maid")) {
			server.runCommand("ovvar patch give Tester " + patch + " 2");
			ctx.waitTicks(10);
		}
		ctx.waitTicks(60);
		server.runCommand("execute as Tester run ovvar reload");
		acceptResourcePack(ctx);
		ctx.runOnClient(client -> {
			client.options.guiScale().set(1);
			client.resizeGui();
		});
		server.runCommand("execute as Tester run ovvar stash");
		conn.waitForClientboundPackets();
		ctx.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen, 20 * 10);
		ctx.getInput().setCursorPos(0, 0);   // no slot hovered, no tooltip
		ctx.runOnClient(client -> client.gui.toastManager().clear());
		ctx.waitTicks(20);
		Path shot = ctx.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("stash_full"));
		try {
			BufferedImage full = ImageIO.read(shot.toFile());
			// A 9×6 chest screen is 176×222, centred; its top 17 px are the title and 6 rows of 18 the
			// menu, then a 3 px margin before the inventory's own label.
			int w = 176, h = 17 + 6 * 18 + 3;
			int x = (full.getWidth() - w) / 2, y = (full.getHeight() - 222) / 2;
			ImageIO.write(full.getSubimage(x, y, w, h), "png", file.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot cut the stash out of " + shot, e);
		}
		ctx.runOnClient(client -> client.gui.setScreen(null));
	}

	/**
	 * The crafting recipes the pages show, each as the nine slots of a crafting table, left to right
	 * and top to bottom ({@code ""} for an empty slot).
	 */
	private static final Map<String, List<String>> RECIPES = new LinkedHashMap<>();
	static {
		RECIPES.put("canvas", List.of("stick", "stick", "stick", "stick", "paper", "stick", "stick", "stick", "stick"));
		RECIPES.put("mailbox", List.of("", "oak_log", "copper_ingot", "oak_slab", "paper", "oak_slab", "", "", ""));
		RECIPES.put("sign_post", List.of("", "", "", "oak_planks", "oak_planks", "stick", "", "", ""));
		RECIPES.put("rope", List.of("", "string", "", "string", "wheat", "string", "", "string", ""));
		RECIPES.put("hammer", List.of("", "", "", "iron_nugget", "iron_ingot", "", "", "stick", ""));
		RECIPES.put("trowel", List.of("", "", "", "iron_nugget", "iron_ingot", "", "stick", "iron_nugget", ""));
		RECIPES.put("void_anchor", List.of("crying_obsidian", "crying_obsidian", "crying_obsidian",
				"ender_eye", "ender_eye", "ender_eye", "crying_obsidian", "crying_obsidian", "crying_obsidian"));
		RECIPES.put("muffler", List.of("", "", "", "white_wool", "amethyst_shard", "string", "", "", ""));
		RECIPES.put("barrel_spigot", List.of("stick", "stone", "", "oak_planks", "", "", "", "", ""));
	}

	/**
	 * A recipe, cut out of a screenshot the way the stash is: the Tester gets a crafting table's menu
	 * with the ingredients already in its grid, so the result shows, and the grid, the arrow and the
	 * result are kept.
	 */
	private static void recipe(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, Path file, List<String> grid) {
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			ServerPlayer tester = mc.getPlayerList().getPlayerByName("Tester");
			BlockPos table = new BlockPos(38, -60, 23);
			level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
			tester.openMenu(new SimpleMenuProvider((id, inventory, player) -> new CraftingMenu(id, inventory, ContainerLevelAccess.create(level, table)),
					Component.translatable("container.crafting")));
			CraftingMenu menu = (CraftingMenu) tester.containerMenu;
			for (int i = 0; i < 9; i++) {
				if (grid.get(i).isEmpty()) continue;
				menu.getInputGridSlots().get(i).set(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(grid.get(i)))));
			}
			menu.broadcastChanges();
		});
		conn.waitForClientboundPackets();
		ctx.waitFor(client -> client.gui.screen() instanceof CraftingScreen, 20 * 10);
		ctx.getInput().setCursorPos(0, 0);
		ctx.waitTicks(10);
		Path shot = ctx.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("recipe_full"));
		try {
			BufferedImage full = ImageIO.read(shot.toFile());
			// A crafting screen is 176×166, centred: the grid starts at (30, 17), the result's big slot
			// ends at (146, 57). Keep that and a little round it, between the title and the inventory's label.
			int x = (full.getWidth() - 176) / 2, y = (full.getHeight() - 166) / 2;
			ImageIO.write(full.getSubimage(x + 26, y + 15, 124, 57), "png", file.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot cut the recipe out of " + shot, e);
		}
		server.runOnServer(mc -> mc.getPlayerList().getPlayerByName("Tester").closeContainer());
		ctx.waitFor(client -> client.gui.screen() == null, 20 * 10);
	}

	private static final List<Scene> SCENES = List.of(
			new Scene("ovvar/hero", new BlockPos(20, -60, 20), new BlockPos(20, -58, 20), 60),
			new Scene("ovvar/back", new BlockPos(24, -60, 20), new BlockPos(24, -58, 20), 60),
			new Scene("ovvar/chapters", new BlockPos(28, -60, 20), new BlockPos(34, -58, 20), 70),
			new Scene("ovvar/stand", new BlockPos(40, -60, 20), new BlockPos(40, -58, 22), 75),
			new Scene("decorating/canvas", new BlockPos(42, -60, 20), new BlockPos(45, -58, 22), 70),
			new Scene("decorating/corner", new BlockPos(86, -60, 20), new BlockPos(89, -58, 21), 70),
			new Scene("decorating/mailbox", new BlockPos(50, -60, 20), new BlockPos(50, -59, 20), 90),
			new Scene("decorating/rope", new BlockPos(54, -60, 20), new BlockPos(58, -57, 20), 70),
			new Scene("decorating/sign_post", new BlockPos(62, -60, 20), new BlockPos(62, -59, 20), 90),
			new Scene("decorating/lantern", new BlockPos(66, -60, 20), new BlockPos(68, -59, 21), 80),
			new Scene("decorating/lead", new BlockPos(72, -60, 20), new BlockPos(76, -59, 20), 70),
			new Scene("decorating/trowel", new BlockPos(80, -61, 17), new BlockPos(81, -61, 21), 70),
			new Scene("qol/void_anchor", new BlockPos(99, -61, 19), new BlockPos(101, -60, 21), 80),
			new Scene("qol/rift", new BlockPos(104, -60, 18), new BlockPos(109, -54, 23), 55),
			new Scene("qol/concrete", new BlockPos(111, -60, 19), new BlockPos(113, -58, 21), 80),
			new Scene("qol/muffler", new BlockPos(117, -60, 20), new BlockPos(120, -58, 21), 75),
			new Scene("brewing/cauldron", new BlockPos(20, -60, 28), new BlockPos(22, -57, 30), 80),
			new Scene("brewing/distilling", new BlockPos(25, -60, 28), new BlockPos(27, -59, 30), 90),
			new Scene("brewing/barrel", new BlockPos(30, -60, 28), new BlockPos(35, -57, 30), 70),
			new Scene("brewing/barrel_frame", new BlockPos(52, -60, 28), new BlockPos(52, -57, 34), 70),
			new Scene("brewing/drinks", new BlockPos(39, -60, 30), new BlockPos(43, -59, 30), 85),
			new Scene("brewing/chapter_drinks", new BlockPos(46, -60, 30), new BlockPos(49, -59, 30), 90));

	private static void build(ServerLevel level) {
		// ---- ovvar
		ItemStack front = ovve(Chapter.DATA, List.of(
				new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")),
				new Placement(Spot.FRONT_TOP_RIGHT, Patches.get("nyckeln0x0")),
				new Placement(Spot.FRONT_LOW_LEFT, Patches.get("it")),
				new Placement(Spot.FRONT_LOW_RIGHT, Patches.get("data")),
				new Placement(Spot.SLEEVE_OUT_TOP_L, Patches.get("spiken")),
				new Placement(Spot.SLEEVE_OUT_TOP_R, Patches.get("slaggan")),
				new Placement(Spot.SHOULDER_R, Patches.get("itk")),
				new Placement(Spot.LEG_OUT_TOP_R, Patches.get("tmeit")),
				new Placement(Spot.LEG_OUT_TOP_L, Patches.get("jgs"))));
		mannequin(level, 20, Chapter.DATA, front, 45);
		ItemStack back = ovve(Chapter.DATA, List.of(
				new Placement(Spot.BACK_BIG, Patches.get("it")),
				new Placement(Spot.BACK_TOP_LEFT, Patches.get("in")),
				new Placement(Spot.BACK_TOP_RIGHT, Patches.get("in_gold")),
				new Placement(Spot.SEAT, Patches.get("rivals"))));
		mannequin(level, 24, Chapter.DATA, back, 225);
		int x = 28;
		for (Chapter chapter : List.of(Chapter.DATA, Chapter.IT, Chapter.IT_KISEL, Chapter.MEDIA)) {
			mannequin(level, x, chapter, ovve(chapter, List.of(new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")))), 45);
			x += 2;
		}
		ArmorStand stand = new ArmorStand(EntityTypes.ARMOR_STAND, level);
		stand.setPos(40.5, -60, 20.5);
		stand.setYRot(0);   // facing the Tester, south
		stand.setShowArms(true);
		stand.setLeftArmPose(new Rotations(-20, 0, -10));
		stand.setRightArmPose(new Rotations(-15, 0, 10));
		stand.setItemSlot(EquipmentSlot.LEGS, ovve(Chapter.IT, List.of(
				new Placement(Spot.FRONT_LOW_RIGHT, Patches.get("kommn")),
				new Placement(Spot.SLEEVE_OUT_TOP_R, Patches.get("data")))));
		level.addFreshEntity(stand);

		// ---- canvas: a 2×2 wall of planks with a 32×32 picture across four canvases on its south face,
		// the face the camera sees; its lower left corner not painted yet, and a painter at it
		for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) {
			level.setBlockAndUpdate(new BlockPos(44 + dx, -60 + dy, 20), Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		BufferedImage picture = picture();
		for (int cx = 0; cx < 2; cx++) for (int cy = 0; cy < 2; cy++) {
			CanvasPixels pixels = new CanvasPixels();
			for (int px = 0; px < 16; px++) for (int py = 0; py < 16; py++) {
				int gx = cx * 16 + px, gy = (1 - cy) * 16 + py;
				int argb = picture.getRGB(gx, gy);
				boolean painted = (argb >>> 24) >= 128 && !(gy >= 20 && gx < 12);
				pixels.setRaw(px, py, painted ? CanvasUtils.findClosestRawColorARGB(argb | 0xFF000000) : 0);
			}
			ItemStack canvas = new ItemStack(DecorationsItems.CANVAS);
			canvas.set(DecorationsDataComponents.CANVAS_DATA, new CanvasData(Optional.of(pixels), Optional.empty(), false, false, false));
			CanvasEntity entity = CanvasEntity.create(level, Direction.SOUTH, new BlockPos(44 + cx, -60 + cy, 21), 0);
			entity.loadFromStack(canvas);
			level.addFreshEntity(entity);
		}
		Mannequin painter = new Mannequin(EntityTypes.MANNEQUIN, level);
		// Beside the wall, not in front of it: the camera looks from the south-west.
		painter.setPos(42.7, -60, 21.6);
		float toCanvas = -110;   // looking east, at the unpainted corner
		painter.setYRot(toCanvas); painter.setYBodyRot(toCanvas); painter.setYHeadRot(toCanvas);
		painter.setXRot(10);
		painter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_dye"))));
		level.addFreshEntity(painter);
		MID_SWING.add(painter.getUUID());

		// ---- decorating
		mailboxOnPost(level, new BlockPos(50, -60, 20), Direction.SOUTH);

		// rope: two posts, rope between their tops, a lantern and a hanging sign hung from it
		for (int y = -60; y <= -57; y++) {
			level.setBlockAndUpdate(new BlockPos(54, y, 20), Blocks.SPRUCE_LOG.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(58, y, 20), Blocks.SPRUCE_LOG.defaultBlockState());
		}
		List<BlockPos> rope = List.of(new BlockPos(55, -57, 20), new BlockPos(56, -57, 20), new BlockPos(57, -57, 20), new BlockPos(57, -58, 20));
		for (BlockPos pos : rope) level.setBlockAndUpdate(pos, DecorationsBlocks.ROPE.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(55, -58, 20), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		BlockPos hanging = new BlockPos(57, -59, 20);
		level.setBlockAndUpdate(hanging, Blocks.OAK_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, 0));
		if (level.getBlockEntity(hanging) instanceof SignBlockEntity sign) {
			sign.setText(signText("", "Post", "", ""), SignTextSlot.FRONT);
		}
		for (BlockPos pos : rope) reshape(level, pos);

		signPost(level, new BlockPos(62, -60, 20), "Spawn", "Nether");

		// lanterns: one on a wall of stone bricks, a soul lantern on a fence
		level.setBlockAndUpdate(new BlockPos(66, -60, 20), Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(66, -59, 20), Blocks.STONE_BRICKS.defaultBlockState());
		wallLantern(level, new BlockPos(66, -59, 21), Blocks.LANTERN);
		level.setBlockAndUpdate(new BlockPos(68, -60, 20), Blocks.OAK_FENCE.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(68, -59, 20), Blocks.OAK_FENCE.defaultBlockState());
		wallLantern(level, new BlockPos(68, -59, 21), Blocks.SOUL_LANTERN);

		// a lead from fence post to fence post
		for (int post : new int[] {72, 76}) {
			level.setBlockAndUpdate(new BlockPos(post, -60, 20), Blocks.OAK_FENCE.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(post, -59, 20), Blocks.OAK_FENCE.defaultBlockState());
		}
		FirstLeashFenceKnotEntity first = new FirstLeashFenceKnotEntity(DecorationsEntities.FIRST_LEASH_FENCE_KNOT, level);
		first.setPos(72, -59, 20);
		level.addFreshEntity(first);
		first.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(level, new BlockPos(76, -59, 20)), true);

		// a path laid with a trowel: blocks drawn at random from a hotbar, in place of the grass
		Random random = new Random(7);
		List<Block> hotbar = List.of(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL);
		for (int px = 80; px <= 81; px++) for (int pz = 17; pz <= 21; pz++) {
			level.setBlockAndUpdate(new BlockPos(px, -61, pz), hotbar.get(random.nextInt(hotbar.size())).defaultBlockState());
		}

		// the chapter's own picture, a street corner: a lantern on a wall, a mailbox, a sign post
		for (int wx = 86; wx <= 87; wx++) for (int wy = -60; wy <= -58; wy++) {
			level.setBlockAndUpdate(new BlockPos(wx, wy, 20), Blocks.STONE_BRICKS.defaultBlockState());
		}
		wallLantern(level, new BlockPos(86, -59, 21), Blocks.LANTERN);
		mailboxOnPost(level, new BlockPos(88, -60, 21), Direction.SOUTH);
		signPost(level, new BlockPos(89, -60, 21), "Spawn", "Mensa");

		// ---- quality of life (metacraft-qol)
		// a charged void anchor on a pad of end stone
		for (int px = 99; px <= 101; px++) for (int pz = 19; pz <= 21; pz++) {
			level.setBlockAndUpdate(new BlockPos(px, -61, pz), Blocks.END_STONE.defaultBlockState());
		}
		level.setBlockAndUpdate(new BlockPos(100, -60, 20),
				VoidAnchorBlocks.VOID_ANCHOR.value().defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 3));

		// a crack open in the air, its light bursting out from under it, someone sinking into it
		// (opened here, not by command: it stays open)
		Rift.open(level, new Vec3(106.5, -56.6, 20.5), 3.6f, RiftStyle.SHATTER);
		Mannequin sinking = new Mannequin(EntityTypes.MANNEQUIN, level);
		sinking.setPos(106.5, -55.9, 20.5);
		sinking.setNoGravity(true);
		sinking.setYRot(30); sinking.setYBodyRot(30); sinking.setYHeadRot(30);
		sinking.setXRot(40);
		level.addFreshEntity(sinking);

		// a cauldron of water, powder about to drop in, the concrete it made beside it
		level.setBlockAndUpdate(new BlockPos(112, -60, 20), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		level.setBlockAndUpdate(new BlockPos(113, -60, 20), Blocks.CONCRETE_POWDER.lightBlue().defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(113, -60, 21), Blocks.CONCRETE.lightBlue().defaultBlockState());
		ItemEntity powder = new ItemEntity(level, 112.5, -58.85, 20.5, new ItemStack(Items.CONCRETE_POWDER.lightBlue(), 64), 0, 0, 0);
		powder.setNoGravity(true);
		powder.setNeverPickUp();
		level.addFreshEntity(powder);
		ItemEntity made = new ItemEntity(level, 111.5, -60, 20.6, new ItemStack(Items.CONCRETE.lightBlue(), 64), 0, 0, 0);
		made.setNeverPickUp();
		level.addFreshEntity(made);

		// a muffler swung at a cow
		Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
		cow.snapTo(117.8, -60, 20.5, -90, 0);
		cow.setYHeadRot(-90); cow.setYBodyRot(-90);
		cow.setNoAi(true);
		level.addFreshEntity(cow);
		Mannequin muffling = new Mannequin(EntityTypes.MANNEQUIN, level);
		muffling.setPos(120.0, -60, 20.5);
		muffling.setYRot(90); muffling.setYBodyRot(90); muffling.setYHeadRot(90);
		muffling.setXRot(20);
		muffling.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(SilenceMobs.MUFFLER));
		level.addFreshEntity(muffling);
		MID_SWING.add(muffling.getUUID());
	}

	/**
	 * The Brewing chapter's scenes. Brewery's blocks and drinks are placed by command, so this source
	 * set needs no Brewery classes; they are there at runtime (metacraft-booklet's dev runtime).
	 */
	private static void buildBrewing(TestDedicatedServerContext server) {
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			// a cauldron of water over a lit campfire, the ingredients dropping in, a stick to stir with
			level.setBlockAndUpdate(new BlockPos(21, -60, 29), Blocks.CAMPFIRE.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(21, -59, 29), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			floating(level, 21.35, -57.7, 29.4, new ItemStack(Items.POTATO, 4));
			floating(level, 21.7, -57.4, 29.6, new ItemStack(Items.SWEET_BERRIES, 4));
			ItemEntity stick = new ItemEntity(level, 22.5, -60, 29.4, new ItemStack(Items.STICK), 0, 0, 0);
			stick.setNeverPickUp();
			level.addFreshEntity(stick);
			// a brewing stand with three mixtures in it and nothing on top, blaze powder beside it
			level.setBlockAndUpdate(new BlockPos(26, -60, 29), Blocks.BREWING_STAND.defaultBlockState()
					.setValue(BrewingStandBlock.HAS_BOTTLE[0], true).setValue(BrewingStandBlock.HAS_BOTTLE[1], true)
					.setValue(BrewingStandBlock.HAS_BOTTLE[2], true));
			ItemEntity powder = new ItemEntity(level, 27.4, -60, 29.5, new ItemStack(Items.BLAZE_POWDER, 3), 0, 0, 0);
			powder.setNeverPickUp();
			level.addFreshEntity(powder);
			// a barrel's frame, as Brewery checks it: four slices along x, each round (stairs on the
			// corners, planks on the sides), the end slices closed, standing on four fences (the spigot
			// below makes it Brewery's finished barrel)
			for (int x = 31; x <= 34; x++) slice(level, x, 28, x == 31 || x == 34);
			// and for the page on building one, an end slice beside a middle one, both faces in view
			slice(level, 52, 28, true);
			slice(level, 52, 32, false);
		});
		// the spigot on the middle plank of the east end, facing into the barrel
		server.runCommand("setblock 35 -58 29 brewery:barrel_spigot[facing=west]");
		// shelves of drinks: Brewery's own, and the chapter drinks, in frames on a wall
		shelf(server, 39, List.of("brewery:beer", "brewery:wine", "brewery:vodka", "brewery:mead", "brewery:cider"), List.of());
		shelf(server, 46, List.of("kultur:alcohol", "kultur:spiken", "kultur:slaggan", "kultur:nyckeln"), List.of("kultur:spiken", "kultur:slaggan"));
	}

	private static void floating(ServerLevel level, double x, double y, double z, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, x, y, z, stack, 0, 0, 0);
		item.setNoGravity(true);
		item.setNeverPickUp();
		level.addFreshEntity(item);
	}

	/** One slice across a barrel, at x from z0 to z0 + 2: closed with a plank and on fences at the ends, hollow between. */
	private static void slice(ServerLevel level, int x, int z0, boolean end) {
		for (int y = -59; y <= -57; y++) for (int z = z0; z <= z0 + 2; z++) {
			BlockPos at = new BlockPos(x, y, z);
			boolean corner = (y != -58) && (z != z0 + 1);
			if (corner) {
				// round off the outside: the full half and the raised back toward the barrel's middle
				level.setBlockAndUpdate(at, Blocks.SPRUCE_STAIRS.defaultBlockState()
						.setValue(StairBlock.HALF, y == -57 ? Half.BOTTOM : Half.TOP)
						.setValue(StairBlock.FACING, z == z0 ? Direction.SOUTH : Direction.NORTH));
			} else if (y == -58 && z == z0 + 1 && !end) {
				level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
			} else {
				level.setBlockAndUpdate(at, Blocks.SPRUCE_PLANKS.defaultBlockState());
			}
		}
		if (end) {
			level.setBlockAndUpdate(new BlockPos(x, -60, z0), Blocks.SPRUCE_FENCE.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(x, -60, z0 + 2), Blocks.SPRUCE_FENCE.defaultBlockState());
		}
	}

	/**
	 * A shelf of drinks from x0: a row of dark oak slabs with a bottle standing on each (resting item
	 * entities; the image renderer draws those, not item frames).
	 */
	private static void shelf(TestDedicatedServerContext server, int x0, List<String> drinks, List<String> distilled) {
		int x1 = x0 + drinks.size() - 1;
		server.runCommand("fill " + x0 + " -60 30 " + x1 + " -60 30 minecraft:dark_oak_slab");
		for (int i = 0; i < drinks.size(); i++) {
			String type = drinks.get(i);
			server.runCommand("summon minecraft:item " + (x0 + i + 0.5) + " -59.5 30.5 {NoGravity:1b,PickupDelay:32767,Age:-32768,"
					+ "Item:{id:\"brewery:drink_bottle\",count:1,components:{\"brewery:brew_data\":{type:\"" + type + "\",quality:10.0d,"
					+ "distillation_runs:" + (distilled.contains(type) ? 1 : 0) + "}}}}");
		}
	}

	private static void mailboxOnPost(ServerLevel level, BlockPos post, Direction facing) {
		level.setBlockAndUpdate(post, Blocks.OAK_FENCE.defaultBlockState());
		level.setBlockAndUpdate(post.above(), DecorationsBlocks.WOODEN_MAILBOX.get(WoodType.OAK).defaultBlockState().setValue(MailboxBlock.FACING, facing));
	}

	/** A two-high oak fence, its top a sign post with a sign each half. */
	private static void signPost(ServerLevel level, BlockPos foot, String upper, String lower) {
		level.setBlockAndUpdate(foot, Blocks.OAK_FENCE.defaultBlockState());
		BlockPos top = foot.above();
		level.setBlockAndUpdate(top, AttachedSignPostBlock.MAP.get(Blocks.OAK_FENCE).defaultBlockState());
		if (level.getBlockEntity(top) instanceof SignPostBlockEntity post) {
			var item = DecorationsItems.SIGN_POST.get(WoodType.OAK);
			// A sign faces whoever put it up: 45 faces the camera, south-west; 0 faces south.
			post.setText(true, SignPostBlockEntity.Sign.of(item, 45, false).withText(signText(upper, "", "", "")));
			post.setText(false, SignPostBlockEntity.Sign.of(item, 0, true).withText(signText(lower, "", "", "")));
		}
	}

	/** A lantern on the south face of the block north of {@code pos}, as a sneak-click there puts it. */
	private static void wallLantern(ServerLevel level, BlockPos pos, Block lantern) {
		BlockPos wall = pos.north();
		var attached = WallAttachedLanternBlock.getSupportType(level, Direction.SOUTH, wall, level.getBlockState(wall));
		level.setBlockAndUpdate(pos, WallAttachedLanternBlock.VANILLA2WALL.get(lantern).defaultBlockState()
				.setValue(WallAttachedLanternBlock.WATERLOGGED, false)
				.setValue(WallAttachedLanternBlock.FACING, Direction.NORTH)
				.setValue(WallAttachedLanternBlock.ATTACHED, attached));
	}

	/** The state a block would take from its neighbours, as if it had just been placed. */
	private static void reshape(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(state, level, pos));
	}

	private static SignText signText(String... lines) {
		List<Component> messages = Arrays.stream(lines).map(line -> (Component) Component.literal(line)).toList();
		return new SignText(messages, messages, DyeColor.BLACK, false);
	}

	private static ItemStack ovve(Chapter chapter, List<Placement> placements) {
		ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
		OvveItem.setTopUp(ovve, true);
		Looks.setSewn(ovve, SpotPlacements.fromList(placements).getOrThrow());
		return ovve;
	}

	private static void mannequin(ServerLevel level, int x, Chapter chapter, ItemStack ovve, float yaw) {
		Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
		m.setPos(x + 0.5, -60, 20.5);
		m.setYRot(yaw); m.setYBodyRot(yaw); m.setYHeadRot(yaw);
		if (chapter.ownPiece() == Piece.TOP) {
			// a frack is worn in the chest slot and is its own top
			m.setItemSlot(EquipmentSlot.CHEST, ovve);
		} else {
			m.setItemSlot(EquipmentSlot.LEGS, ovve);
			ItemStack top = new ItemStack(ModContent.top(chapter));
			top.set(ModComponents.PATCHES, ovve.get(ModComponents.PATCHES));
			m.setItemSlot(EquipmentSlot.CHEST, top);
		}
		level.addFreshEntity(m);
	}

	/** The picture on the canvas wall: METAcraft's icon at 32×32. */
	private static BufferedImage picture() {
		try (InputStream in = BookletRenders.class.getResourceAsStream("/assets/metacraft-booklet/icon.png")) {
			BufferedImage src = ImageIO.read(in);
			BufferedImage out = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			var g = out.createGraphics();
			g.drawImage(src, 0, 0, 32, 32, null);
			g.dispose();
			return out;
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}
}
