package nu.metacraft.rivals.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import eu.pb4.polymer.blocks.impl.DefaultModelData;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.TeamColor;
import nu.metacraft.rivals.Arena;
import nu.metacraft.rivals.Lobby;
import nu.metacraft.rivals.MainPack;
import nu.metacraft.rivals.Match;
import nu.metacraft.rivals.Stats;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.OvveTeams;
import nu.metacraft.rivals.PlayerTick;
import nu.metacraft.rivals.Readiness;
import nu.metacraft.rivals.TeamNames;
import nu.metacraft.rivals.Rivals;
import nu.metacraft.rivals.SquidDisplay;
import nu.metacraft.rivals.SquidState;
import nu.metacraft.rivals.RivalsCommands;
import nu.metacraft.rivals.ScoreBars;
import nu.metacraft.rivals.gun.PaintBall;
import nu.metacraft.rivals.gun.PaintWeapon;
import nu.metacraft.rivals.gun.Weapon;
import nu.metacraft.rivals.gun.WeaponChoice;
import nu.metacraft.rivals.gun.WeaponDialog;
import nu.metacraft.rivals.gun.WeaponLock;
import nu.metacraft.rivals.gun.Special;
import nu.metacraft.rivals.gun.SpecialChoice;
import nu.metacraft.rivals.gun.SpecialDialog;
import nu.metacraft.rivals.gun.SpecialTuning;
import nu.metacraft.rivals.gun.WeaponPicks;
import nu.metacraft.rivals.gun.WeaponSelector;
import nu.metacraft.rivals.gun.WeaponTuning;
import nu.metacraft.rivals.gun.WeaponTuning.Param;
import nu.metacraft.rivals.gun.Recoil;
import nu.metacraft.rivals.gun.Roll;
import nu.metacraft.rivals.paint.ConnectedPaintBlock;
import nu.metacraft.rivals.paint.Paint;
import nu.metacraft.rivals.paint.PaintBlock;
import nu.metacraft.rivals.paint.PaintBlocks;
import nu.metacraft.rivals.paint.PaintDisplays;
import nu.metacraft.rivals.paint.Painter;
import nu.metacraft.rivals.paint.PaintTally;
import nu.metacraft.rivals.paint.Unpaintable;
import nu.metacraft.rivals.pack.InkArt;
import nu.metacraft.rivals.pack.PaintArt;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import nu.metacraft.rivals.pack.RivalsPack;
import nu.metacraft.rivals.gun.Ink;
import nu.metacraft.rivals.gun.InkHud;
import nu.metacraft.rivals.gun.InkOnScreen;
import nu.metacraft.rivals.paint.PaintStates;

/**
 * Server-side game tests (Fabric GameTest API). Run headless with
 * {@code ./gradlew mods:rivals-paint:runGameTest}; each test gets an empty 8×8×8 structure and
 * positions passed to the helper are relative to it.
 */
public final class RivalsGameTests {
	/** The donors floors and ceilings are dealt from, in the order {@code PaintStates} spends them. */
	private static final List<Block> FLAT_DONORS = List.of(Blocks.CRIMSON_BUTTON, Blocks.WARPED_BUTTON,
			Blocks.BAMBOO_BUTTON, Blocks.PALE_OAK_BUTTON, Blocks.POPLAR_BUTTON);
	/** The donors the splat masks are dealt from: the carpet, then the buttons' leftovers. */
	private static final List<Block> SPLAT_DONORS = Stream.concat(
			Stream.of(Blocks.PALE_MOSS_CARPET), FLAT_DONORS.stream()).toList();

	@GameTest
	public void modLoads(GameTestHelper helper) {
		helper.succeed();
	}

	/** Both paint blocks are sent as the client state the table (spec §2) keeps for them, and never as water. */
	@GameTest
	public void paintMapsThroughTheStateTable(GameTestHelper helper) {
		for (PaintColor color : PaintColor.values()) {
			PaintBlock splat = PaintBlocks.splat(color);
			int mask = 1 << Direction.DOWN.ordinal() | 1 << Direction.NORTH.ordinal();
			BlockState state = splat.defaultBlockState()
					.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
					.setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true);
			helper.assertValueEqual(splat.faceMask(state), mask, color.id + " splat face mask");
			BlockState client = splat.getPolymerBlockState(state, PacketContext.get());
			helper.assertValueEqual(client, PaintStates.splat(color, mask), color.id + " splat maps to its own client state");
			ConnectedPaintBlock connected = PaintBlocks.connected(color);
			BlockState cell = ConnectedPaintBlock.withBits(
					connected.defaultBlockState().setValue(ConnectedPaintBlock.FACE, Direction.NORTH), 0b1010);
			helper.assertValueEqual(ConnectedPaintBlock.bits(cell), 0b1010, color.id + " connection bits round-trip");
			helper.assertValueEqual(connected.faceMask(cell), 1 << Direction.NORTH.ordinal(), color.id + " connected face mask");
			BlockState connectedClient = connected.getPolymerBlockState(cell, PacketContext.get());
			helper.assertValueEqual(connectedClient, PaintStates.connected(color, Direction.NORTH, 0b1010),
					color.id + " connected cell maps to its own client state");
			for (BlockState sent : List.of(client, connectedClient)) {
				helper.assertTrue(!sent.hasProperty(BlockStateProperties.WATERLOGGED) || !sent.getValue(BlockStateProperties.WATERLOGGED),
						Component.literal(color.id + " sent waterlogged: " + sent));
			}
		}
		helper.succeed();
	}

	/**
	 * Every generated paint texture is 16×16 and every one of its texels is the same ARGB value: the
	 * alpha marker the gloss shader reads, the connection bits in the low nibble of red, the colour in
	 * the rest. Uniform sprites are what makes reading bits back out of a mipmapped texel safe.
	 */
	@GameTest
	public void paintArtCarriesTheMarkerAlpha(GameTestHelper helper) throws IOException {
		Map<String, byte[]> files = PaintArt.packFiles();
		for (PaintColor color : PaintColor.values()) {
			for (int bits = 0; bits < 16; bits++) {
				String path = "assets/rivals-paint/textures/block/" + PaintArt.textureName(color, bits) + ".png";
				helper.assertTrue(files.containsKey(path), "texture in pack: " + path);
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(files.get(path)));
				helper.assertValueEqual(image.getWidth(), PaintArt.SIZE, path + " width");
				helper.assertValueEqual(image.getHeight(), PaintArt.SIZE, path + " height");
				int first = image.getRGB(0, 0);
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						helper.assertValueEqual((image.getRGB(x, y) >>> 24) & 0xFF, PaintArt.PAINT_ALPHA, path + " alpha at " + x + "," + y);
						helper.assertValueEqual(image.getRGB(x, y), first, path + " uniform at " + x + "," + y);
					}
				}
				helper.assertValueEqual((first >> 16) & 0xFF, PaintArt.encodeRed(color.rgb, bits), path + " red carries the bits");
				helper.assertValueEqual(first & 0xFFFF, color.rgb & 0xFFFF, path + " green and blue are the colour's own");
			}
		}
		helper.succeed();
	}

	/**
	 * Every donor override covers every state of its block, names a model that is in the pack, and each
	 * model resolves to a texture that is in the pack too — states paint does not use point at the empty
	 * model, so a stray vanilla sculk vein shows nothing. Every model carries a particle texture, and the
	 * shared face quad is the shape the shader expects.
	 */
	@GameTest
	public void blockstateOverridesReferenceGeneratedModels(GameTestHelper helper) {
		Map<String, byte[]> files = PaintArt.packFiles();
		Set<BlockState> used = new HashSet<>(PaintStates.all());
		for (Block donor : PaintStates.DONORS) {
			String path = "assets/minecraft/blockstates/" + BuiltInRegistries.BLOCK.getKey(donor).getPath() + ".json";
			helper.assertTrue(files.containsKey(path), "override present: " + path);
			JsonObject variants = JsonParser.parseString(new String(files.get(path), StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("variants");
			int states = 0;
			for (BlockState state : donor.getStateDefinition().getPossibleStates()) {
				states++;
				JsonElement variant = variants.get(PaintArt.variantKey(state));
				helper.assertTrue(variant != null, "variant for " + state);
				String model = variant.getAsJsonObject().get("model").getAsString(); // rivals-paint:block/paint_...
				String modelPath = "assets/rivals-paint/models/block/" + model.substring(model.indexOf('/') + 1) + ".json";
				helper.assertTrue(files.containsKey(modelPath), "model in pack: " + modelPath);
				JsonObject json = JsonParser.parseString(new String(files.get(modelPath), StandardCharsets.UTF_8)).getAsJsonObject();
				// Every model needs a particle texture, empty ones included, or the client logs a missing
				// texture reference for it on every join.
				helper.assertTrue(json.getAsJsonObject("textures").has("particle"), "particle texture in " + modelPath);
				if (!used.contains(state)) {
					helper.assertValueEqual(model, Rivals.MOD_ID + ":block/paint_none", "unused donor state draws nothing: " + state);
					helper.assertValueEqual(json.getAsJsonArray("elements").size(), 0, modelPath + " draws nothing");
					continue;
				}
				String texture = json.getAsJsonObject("textures").get("paint").getAsString();
				String texturePath = "assets/rivals-paint/textures/" + texture.substring(texture.indexOf(':') + 1) + ".png";
				helper.assertTrue(files.containsKey(texturePath), "texture in pack: " + texturePath);
				if (json.has("parent")) {
					// A connected cell: the wrapper hangs its texture on one of the six shared face quads.
					String parent = json.get("parent").getAsString();
					String parentPath = "assets/rivals-paint/models/block/" + parent.substring(parent.indexOf('/') + 1) + ".json";
					helper.assertTrue(files.containsKey(parentPath), "parent model in pack: " + parentPath);
					JsonObject parentJson = JsonParser.parseString(new String(files.get(parentPath), StandardCharsets.UTF_8)).getAsJsonObject();
					helper.assertValueEqual(parentJson.getAsJsonArray("elements").size(), 1, parentPath + " is one quad");
				} else {
					// A splat mask: one model listing a quad per painted face, all on the all-connected texture.
					helper.assertValueEqual(json.getAsJsonArray("elements").size(),
							Integer.bitCount(PaintStates.entry(state).faceMask()), modelPath + " has a quad per painted face");
				}
			}
			helper.assertValueEqual(variants.size(), states, donor + ": a variant for every state");
		}
		// The quad itself: a plane the full 16×16 of the cell, a tenth of a sixteenth off the attach face
		// (vanilla's own multiface offset), textured on both of its sides with the whole sprite and never
		// tinted — a slab, a smaller uv or a tintindex would each change what the shader is handed.
		String facePath = "assets/rivals-paint/models/block/" + PaintArt.modelName(Direction.DOWN) + ".json";
		JsonObject face = JsonParser.parseString(new String(files.get(facePath), StandardCharsets.UTF_8)).getAsJsonObject();
		JsonArray elements = face.getAsJsonArray("elements");
		helper.assertValueEqual(elements.size(), 1, facePath + " is one quad");
		JsonObject element = elements.get(0).getAsJsonObject();
		JsonArray from = element.getAsJsonArray("from");
		JsonArray to = element.getAsJsonArray("to");
		helper.assertValueEqual(from.get(1).getAsDouble(), 0.1, facePath + " sits 0.1 off the down face");
		helper.assertValueEqual(to.get(1).getAsDouble(), from.get(1).getAsDouble(), facePath + " is a plane, not a slab");
		for (int axis : new int[] {0, 2}) {
			helper.assertValueEqual(from.get(axis).getAsDouble(), 0.0, facePath + " starts at 0 on axis " + axis);
			helper.assertValueEqual(to.get(axis).getAsDouble(), 16.0, facePath + " spans the cell on axis " + axis);
		}
		JsonObject faces = element.getAsJsonObject("faces");
		helper.assertValueEqual(faces.keySet(), Set.of("up", "down"), facePath + ": both sides of the quad, and only those");
		for (String side : faces.keySet()) {
			JsonObject json = faces.getAsJsonObject(side);
			helper.assertValueEqual(json.get("texture").getAsString(), "#paint", facePath + " " + side + " texture");
			helper.assertTrue(!json.has("tintindex"), facePath + " " + side + " is untinted");
			JsonArray uv = json.getAsJsonArray("uv");
			// Which way round is paintQuadUvsOrientTheSprite's business; here it only has to be the whole
			// sprite, because the shader reads the cell's own coordinate out of it.
			helper.assertValueEqual(uv.toString(), java.util.Arrays.toString(PaintArt.uv(Direction.byName(side))).replace(" ", ""),
					facePath + " " + side + " uv covers the sprite");
		}
		helper.succeed();
	}

	/** Stone floor at relative y=1 over x,z in [0,size). */
	private static void stoneFloor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	private static int faces(BlockState state) {
		return state.getBlock() instanceof Paint paint ? Integer.bitCount(paint.faceMask(state)) : 0;
	}

	/** Paint of {@code color} in this cell, of either kind (a connected cell or the splat fallback). */
	private static boolean isPaint(BlockState state, PaintColor color) {
		return state.getBlock() instanceof Paint paint && paint.color() == color;
	}

	/** The same, carrying paint on the {@code face} side of the cell. */
	private static boolean hasFace(BlockState state, PaintColor color, Direction face) {
		return state.getBlock() instanceof Paint paint && paint.color() == color
				&& (paint.faceMask(state) & 1 << face.ordinal()) != 0;
	}

	/** A splat on the top of a floor block paints the cell above it, on its down face, in that colour. */
	@GameTest
	public void floorSplatPaintsCellAbove(GameTestHelper helper) {
		stoneFloor(helper, 5);
		BlockPos struck = new BlockPos(2, 1, 2);
		int painted = Painter.splat(helper.getLevel(), helper.absolutePos(struck), Direction.UP, PaintColor.DATA,
				helper.getLevel().getRandom());
		helper.assertTrue(painted >= 5 && painted <= 9, "painted " + painted + " faces, expected 5..9");
		BlockState cell = helper.getBlockState(struck.above());
		helper.assertTrue(isPaint(cell, PaintColor.DATA), Component.literal("cell above the hit is DATA paint, got " + cell));
		helper.assertTrue(hasFace(cell, PaintColor.DATA, Direction.DOWN), "paint sits on its down face");
		helper.succeed();
	}

	/** The blob stays within one block of the hit in the plane, and never where the surface is missing. */
	@GameTest
	public void blobStaysWithinRadiusAndOnSurfaces(GameTestHelper helper) {
		stoneFloor(helper, 5);
		helper.setBlock(new BlockPos(1, 1, 2), Blocks.AIR); // a hole beside the hit, not a corner
		Painter.splat(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, PaintColor.DATA,
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
		helper.assertTrue(isPaint(helper.getBlockState(new BlockPos(2, 2, 2)), PaintColor.DATA), "centre is painted");
		for (BlockPos edge : new BlockPos[] {new BlockPos(3, 2, 2), new BlockPos(2, 2, 1), new BlockPos(2, 2, 3)}) {
			BlockState cell = helper.getBlockState(edge);
			helper.assertTrue(isPaint(cell, PaintColor.DATA), Component.literal("edge " + edge + " should be DATA paint, got " + cell));
			helper.assertTrue(hasFace(cell, PaintColor.DATA, Direction.DOWN), "edge " + edge + " has its down face set");
		}
		helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 2)).isAir(), "edge over the hole stays air");
		helper.succeed();
	}

	/** Spec §4: a hit in another colour wipes the cell and leaves only the face that was just painted. */
	@GameTest
	public void otherColourOverpaintsCellToOneFace(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE); // floor under the cell
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE); // wall north of the cell
		BlockPos cell = new BlockPos(2, 2, 2);
		helper.setBlock(cell, PaintBlocks.splat(PaintColor.DATA).defaultBlockState()
				.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
				.setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true));
		boolean painted = Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, PaintColor.IT);
		helper.assertTrue(painted, "the cell counts as newly painted");
		BlockState after = helper.getBlockState(cell);
		helper.assertTrue(isPaint(after, PaintColor.IT), Component.literal("cell is IT now, got " + after));
		helper.assertTrue(hasFace(after, PaintColor.IT, Direction.DOWN), "the painted face is the down one");
		helper.assertValueEqual(faces(after), 1, "face count");
		helper.assertTrue(after.getBlock() instanceof ConnectedPaintBlock, "one face is a connected cell");
		helper.succeed();
	}

	/**
	 * The tally counts faces per colour from the cells it tracks, and reset removes them. Every count here
	 * is a delta against a snapshot taken first: {@code count} folds in the whole level's display quads,
	 * and game tests in the same level run side by side, so the absolute figures are not this test's to
	 * predict.
	 */
	@GameTest
	public void tallyCountsFacesAndResets(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
		BlockPos dataCell = new BlockPos(2, 2, 2);
		BlockPos itCell = new BlockPos(4, 2, 4);
		helper.setBlock(dataCell, PaintBlocks.splat(PaintColor.DATA).defaultBlockState()
				.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
				.setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true));
		helper.setBlock(itCell, PaintBlocks.splat(PaintColor.IT).defaultBlockState()
				.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true));
		// count() folds in the level's display quads, which belong to whatever else is running: take the
		// baseline first and read every figure below as this test's own contribution on top of it.
		Map<PaintColor, Integer> quads = PaintDisplays.of(helper.getLevel()).count(helper.getLevel());
		PaintTally tally = new PaintTally();
		tally.track(helper.absolutePos(dataCell));
		tally.track(helper.absolutePos(itCell));
		tally.track(helper.absolutePos(new BlockPos(0, 5, 0))); // air: must be dropped, not counted
		Map<PaintColor, Integer> counts = tally.count(helper.getLevel());
		Map<PaintColor, Integer> mine = new EnumMap<>(PaintColor.class);
		for (PaintColor color : PaintColor.values()) mine.put(color, counts.get(color) - quads.get(color));
		helper.assertValueEqual(mine.get(PaintColor.DATA), 2, "DATA faces");
		helper.assertValueEqual(mine.get(PaintColor.IT), 1, "IT faces");
		helper.assertValueEqual(tally.cells(), 2, "the air cell was dropped");
		// Two faces to one out of three painted faces in all: a third of them are IT's.
		helper.assertTrue(Math.abs(PaintTally.share(mine, PaintColor.IT) - 1f / 3f) < 1e-6, "IT share is a third");
		helper.assertTrue(Math.abs(PaintTally.share(mine, PaintColor.DATA) - 2f / 3f) < 1e-6, "DATA share is two thirds");
		int quadsBefore = PaintDisplays.of(helper.getLevel()).holders(); // reset clears the level's quads too
		int removed = tally.reset(helper.getLevel());
		helper.assertValueEqual(removed - quadsBefore, 2, "reset removed both cells");
		helper.assertTrue(helper.getBlockState(dataCell).isAir() && helper.getBlockState(itCell).isAir(), "cells are air after reset");
		helper.assertValueEqual(tally.count(helper.getLevel()).get(PaintColor.DATA), 0, "nothing left to count");
		helper.assertValueEqual(tally.cells(), 0, "and no cells left to count it from");
		helper.succeed();
	}

	/**
	 * Paint that nothing is tracking — the state a restart leaves, since the blocks are saved with the chunk
	 * and the tally is memory only — is still found and removed when there are bounds to sweep. Before this,
	 * {@code /rivals reset} after a restart answered "Nothing painted" over a painted arena and left it
	 * standing, and {@code /rivals score} counted none of it.
	 *
	 * <p>The box is this test's own structure and nothing else, so the sweep cannot reach into a test
	 * ticking beside it. Counts are read as deltas across the placement for the usual reason: {@code count}
	 * folds in the whole level's display quads.
	 */
	@GameTest
	public void resetSweepsTheArenaForUntrackedPaint(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos floor = new BlockPos(2, 1, 2);
		BlockPos wall = new BlockPos(4, 1, 4);
		helper.setBlock(floor, Blocks.STONE);
		helper.setBlock(wall, Blocks.STONE);
		BlockPos dataCell = helper.absolutePos(floor.above());
		BlockPos itCell = helper.absolutePos(wall.above());
		AABB area = helper.getBounds();
		BoundingBox box = BoundingBox.fromCorners(
				BlockPos.containing(area.minX, area.minY, area.minZ),
				BlockPos.containing(area.maxX, area.maxY, area.maxZ));
		PaintTally tally = new PaintTally();
		Map<PaintColor, Integer> before = tally.count(level, box);
		// Straight into the world, past the tally: the blocks are in the chunk and no cell is tracked, which
		// is exactly what the server comes back up to.
		level.setBlock(dataCell, PaintBlocks.connected(PaintColor.DATA).defaultBlockState()
				.setValue(ConnectedPaintBlock.FACE, Direction.DOWN), Block.UPDATE_ALL);
		level.setBlock(itCell, PaintBlocks.splat(PaintColor.IT).defaultBlockState()
				.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true), Block.UPDATE_ALL);
		helper.assertValueEqual(tally.cells(), 0, "nothing is tracked, as after a restart");

		// A score with bounds sweeps the arena, so it sees them.
		Map<PaintColor, Integer> after = tally.count(level, box);
		helper.assertValueEqual(after.get(PaintColor.DATA) - before.get(PaintColor.DATA), 1,
				"the untracked DATA face is counted");
		helper.assertValueEqual(after.get(PaintColor.IT) - before.get(PaintColor.IT), 1,
				"and the untracked IT face too");
		// And a reset with the same bounds takes them out and says how many.
		int removed = tally.reset(level, box);
		helper.assertValueEqual(removed, 2, "both untracked blocks were swept away");
		helper.assertTrue(helper.getBlockState(floor.above()).isAir(), "the DATA cell is air");
		helper.assertTrue(helper.getBlockState(wall.above()).isAir(), "and so is the IT cell");
		Map<PaintColor, Integer> empty = tally.count(level, box);
		helper.assertValueEqual(empty.get(PaintColor.DATA) - before.get(PaintColor.DATA), 0, "nothing left to count");
		helper.succeed();
	}

	/** A vanilla block by its bare registry path, so a test can name one the Blocks fields only collect. */
	private static Block vanilla(String path) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(path));
		if (block == Blocks.AIR) throw new AssertionError("no block called minecraft:" + path);
		return block;
	}

	/**
	 * The shapes ink falls through take none of it: a copper grate, a set of iron bars and a glass pane
	 * are all in {@code #rivals-paint:unpaintable}, and a splat on top of one leaves the cell above
	 * empty — no paint block, and no display quad either, which is the branch a face that is not full
	 * would otherwise have taken.
	 */
	@GameTest
	public void unpaintableShapesTakeNoPaint(GameTestHelper helper) {
		Map<BlockPos, Block> shapes = new LinkedHashMap<>();
		shapes.put(new BlockPos(1, 1, 1), vanilla("copper_grate"));
		shapes.put(new BlockPos(2, 1, 1), Blocks.IRON_BARS);
		shapes.put(new BlockPos(3, 1, 1), Blocks.GLASS_PANE);
		shapes.forEach((pos, block) -> helper.setBlock(pos, block));
		shapes.forEach((pos, block) -> {
			BlockState surface = helper.getBlockState(pos);
			helper.assertTrue(surface.is(Unpaintable.TAG), block + " is in the unpaintable tag");
			helper.assertTrue(!Painter.paintable(surface), block + " is not paintable");
			boolean painted = Painter.paintFace(helper.getLevel(), helper.absolutePos(pos), Direction.UP, PaintColor.DATA);
			helper.assertTrue(!painted, "nothing was painted on " + block);
			helper.assertTrue(helper.getBlockState(pos.above()).isAir(), "the cell above " + block + " stays air");
			helper.assertTrue(PaintDisplays.of(helper.getLevel()).faceAt(helper.absolutePos(pos.above())) == null,
					"and takes no display quads either: " + block);
		});
		// A whole splat over the three of them paints nothing at all — the shot simply skips them.
		helper.assertValueEqual(Painter.splat(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
				Direction.UP, PaintColor.DATA, helper.getLevel().getRandom(), 0), 0, "a splat on bars paints nothing");
		// The families the tag pulls in through vanilla's own tags, and the loose ids beside them: every
		// one of these has to be a block that exists in 26.3, or the tag entry is silently dropped.
		for (String id : new String[] {"rail", "powered_rail", "oak_trapdoor", "iron_trapdoor", "copper_trapdoor",
				"ladder", "scaffolding", "iron_chain", "waxed_oxidized_copper_chain", "waxed_oxidized_copper_grate",
				"exposed_copper_grate", "pink_stained_glass_pane", "copper_bars"}) {
			// vanilla() throws on an id no block answers to, so this is also the check that every entry in
			// the shipped tag file is a real 26.3 block: chain became iron_chain, and the copper families
			// are eight blocks each.
			helper.assertTrue(vanilla(id).defaultBlockState().is(Unpaintable.TAG), id + " is unpaintable");
		}
		// And a plain floor block still is paintable, so the tag has not swallowed the arena.
		helper.assertTrue(Painter.paintable(Blocks.STONE.defaultBlockState()), "stone still takes paint");
		helper.succeed();
	}

	/**
	 * The config list is the other half: an id in {@code config/rivals-paint/unpaintable.json} takes a
	 * block out of the game without a data pack, and {@code Unpaintable.load} is what {@code /rivals reload}
	 * runs. The list is one table for the whole server and the tests in a batch tick side by side, so this
	 * puts it back itself — and it uses a block nothing else here paints on.
	 */
	@GameTest
	public void configListAddsAnUnpaintableBlock(GameTestHelper helper) throws IOException {
		BlockPos floor = new BlockPos(5, 1, 5);
		helper.setBlock(floor, Blocks.GOLD_BLOCK);
		Identifier gold = BuiltInRegistries.BLOCK.getKey(Blocks.GOLD_BLOCK);
		helper.assertTrue(Painter.paintable(helper.getBlockState(floor)), "a gold block takes paint to start with");
		Path config = Files.createTempFile("rivals-unpaintable", ".json");
		try {
			Files.writeString(config, "{\"_help\": \"test\", \"blocks\": [\"" + gold + "\", \"minecraft:nonesuch\","
					+ " \"not a block id at all\"]}", StandardCharsets.UTF_8);
			// An id no block answers to, and one that is not an id at all, are warned about and skipped
			// rather than taking the read down with them.
			helper.assertValueEqual(Unpaintable.load(config), 1, "one usable id out of three");
			helper.assertTrue(Unpaintable.listed().contains(gold), "and it is the gold block");
			helper.assertTrue(!Painter.paintable(helper.getBlockState(floor)), "which now takes no paint");
			helper.assertTrue(!Painter.paintFace(helper.getLevel(), helper.absolutePos(floor), Direction.UP,
					PaintColor.DATA), "so the splat skips it");
			helper.assertTrue(helper.getBlockState(floor.above()).isAir(), "the cell above stays air");
		} finally {
			Unpaintable.clearListed();
			Files.deleteIfExists(config);
		}
		helper.assertTrue(Painter.paintable(helper.getBlockState(floor)), "and the list is back to the tag alone");
		// A missing file is written with its own _help, since json has no comments and this is a file an
		// arena builder is meant to edit.
		Path fresh = Files.createTempDirectory("rivals-unpaintable-fresh").resolve("unpaintable.json");
		try {
			helper.assertValueEqual(Unpaintable.load(fresh), 0, "a fresh file lists nothing");
			helper.assertTrue(Files.isRegularFile(fresh), "but it was written");
			String written = Files.readString(fresh, StandardCharsets.UTF_8);
			helper.assertTrue(written.contains("_help"), "with its help note: " + written);
			helper.assertTrue(written.contains("\"blocks\""), "and an empty list to fill in");
		} finally {
			Unpaintable.clearListed();
			Files.deleteIfExists(fresh);
		}
		helper.succeed();
	}

	/**
	 * The picker is a 26.3 dialog: one picture per weapon — the real weapon stack dyed in the viewer's
	 * team colour, with what it is for written under it rather than hidden in a tooltip — one button per
	 * weapon that runs the pick as the player, and a way out that keeps what they already have.
	 */
	@GameTest
	public void theWeaponDialogListsEveryWeapon(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.IT));
		WeaponChoice choices = WeaponChoice.of(helper.getLevel().getServer());
		try {
			MultiActionDialog dialog = WeaponDialog.build(player);
			helper.assertValueEqual(dialog.common().body().size(), 4, "one picture per weapon");
			helper.assertValueEqual(dialog.actions().size(), 5, "one button per weapon, and one out to the special");
			helper.assertValueEqual(dialog.columns(), 2, "two buttons to a row");
			int index = 0;
			for (Weapon weapon : Weapon.values()) {
				ItemBody picture = (ItemBody) dialog.common().body().get(index);
				ItemStack icon = picture.item().create();
				helper.assertTrue(icon.getItem() == PaintWeapon.of(weapon),
						"picture " + index + " is the real " + weapon + ", not a stand-in");
				DyedItemColor dye = icon.get(DataComponents.DYED_COLOR);
				helper.assertTrue(dye != null && dye.rgb() == PaintColor.IT.rgb,
						"and is dyed in the viewer's team colour, not " + dye);
				String said = picture.description().orElseThrow().contents().getString();
				helper.assertTrue(said.contains("ink"), weapon + "'s line says what it costs: " + said);
				String command = buttonCommand(dialog.actions().get(index));
				helper.assertValueEqual(command, "rivals weapons pick " + weapon.commandId(),
						"button " + index + " takes the " + weapon);
				helper.assertFalse(command.startsWith("/"), "with no leading slash: the client parses it itself");
				index++;
			}
			// With no pick of their own the shooter is the one marked, since that is what a match hands out.
			helper.assertValueEqual(WeaponChoice.DEFAULT, Weapon.SHOOTER, "the default is the shooter");
			helper.assertTrue(((ItemBody) dialog.common().body().getFirst()).description().orElseThrow()
					.contents().getString().contains("(current)"), "and it is the one marked current");
			// The last button is the other half of a loadout: it says what F throws now and opens the picker.
			ActionButton special = dialog.actions().getLast();
			helper.assertValueEqual(buttonCommand(special), WeaponDialog.SPECIAL_COMMAND, "the last button opens the special picker");
			String label = special.button().label().getString();
			helper.assertTrue(label.contains(SpecialChoice.DEFAULT.displayName),
					"and names the one they throw today: " + label);
			ActionButton exit = dialog.exitAction().orElseThrow();
			helper.assertTrue(exit.action().isEmpty(), "the way out runs no command");
			helper.assertTrue(exit.button().label().getString().contains(Weapon.SHOOTER.displayName),
					"and says what it keeps: " + exit.button().label().getString());
			helper.assertTrue(dialog.common().canCloseWithEscape(), "a weapon pick may be escaped out of");
		} finally {
			choices.forget(player.getUUID());
		}
		helper.succeed();
	}

	/** The command behind a dialog button, which is all a button is. */
	private static String buttonCommand(ActionButton button) {
		StaticAction action = (StaticAction) button.action().orElseThrow();
		return ((ClickEvent.RunCommand) action.value()).command();
	}

	/**
	 * And the button's command is a real command: {@code /rivals weapons pick <id>}, runnable by a player
	 * with no permission at all, because that is what a dialog button is — the client sending the string
	 * the server put on it. An id nothing answers to is a failure that lists the ones that work.
	 */
	@GameTest
	public void theWeaponPickCommandTakesTheWeapon(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		WeaponChoice choices = WeaponChoice.of(server);
		List<String> said = new ArrayList<>();
		CommandSourceStack source = server.createCommandSourceStack().withSource(sink(said)).withEntity(player);
		try {
			server.getCommands().performPrefixedCommand(source, WeaponDialog.command(Weapon.SLOSHER));
			helper.assertValueEqual(choices.get(player).orElse(null), Weapon.SLOSHER, "the pick is remembered");
			helper.assertTrue(player.getInventory().getItem(WeaponPicks.GIVEN_SLOT).getItem() == PaintWeapon.of(Weapon.SLOSHER),
					"and the slosher is in the first slot");
			said.clear();
			server.getCommands().performPrefixedCommand(source, "rivals weapons pick trombone");
			String refused = String.join(" | ", said);
			helper.assertTrue(refused.contains("trombone") && refused.contains("slosher"),
					"an unknown id names itself and lists the real ones: " + refused);
			helper.assertValueEqual(choices.get(player).orElse(null), Weapon.SLOSHER, "and changed nothing");
		} finally {
			choices.forget(player.getUUID());
		}
		helper.succeed();
	}

	/** A command source that keeps what it was told, for the tests that read a command's own words. */
	private static CommandSource sink(List<String> said) {
		return new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false; // nothing here is worth telling the whole server about
			}
		};
	}

	/**
	 * Picking is a swap, not a collection: every paint weapon goes out of the inventory and the chosen one
	 * comes back in the first slot. Anything that is not a paint weapon is left alone — a player's ovve and
	 * their blocks are not the picker's business.
	 */
	@GameTest
	public void pickingAWeaponReplacesTheInventory(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		WeaponChoice choices = WeaponChoice.of(helper.getLevel().getServer());
		try {
			PaintWeapon.giveKit(player);
			player.getInventory().setItem(20, new ItemStack(Items.STONE, 7));
			helper.assertValueEqual(paintWeapons(player), 4, "the kit is in there to start with");
			ItemStack given = WeaponPicks.pick(player, Weapon.ROLLER);
			helper.assertTrue(given.getItem() == PaintWeapon.of(Weapon.ROLLER), "a roller was handed over");
			helper.assertValueEqual(paintWeapons(player), 1, "and it is the only paint weapon left");
			helper.assertTrue(player.getInventory().getItem(WeaponPicks.GIVEN_SLOT).getItem() == PaintWeapon.of(Weapon.ROLLER),
					"in the first slot, so it is in hand a keypress later");
			DyedItemColor dye = given.get(DataComponents.DYED_COLOR);
			helper.assertTrue(dye != null && dye.rgb() == PaintColor.DATA.rgb, "dyed in the picker's team colour");
			helper.assertValueEqual(player.getInventory().getItem(20).getCount(), 7, "the stone was left alone");
			// And picking again is a swap rather than a second gun.
			WeaponPicks.pick(player, Weapon.CHARGER);
			helper.assertValueEqual(paintWeapons(player), 1, "still one weapon after a second pick");
			helper.assertTrue(player.getInventory().getItem(WeaponPicks.GIVEN_SLOT).getItem() == PaintWeapon.of(Weapon.CHARGER),
					"and it is the charger now");
		} finally {
			choices.forget(player.getUUID());
		}
		helper.succeed();
	}

	/**
	 * A picked weapon is locked to its slot, and that slot is the only place a locked hotbar selection may
	 * be: every other slot is refused and the client snapped back. One allowed slot rather than two, since
	 * the selector left the hotbar for {@link WeaponSelector#SLOT} — a locked hotbar with two places to be is
	 * a hotbar a player can end up holding a compass in during a firefight. A player carrying no picked
	 * weapon is nobody's business but vanilla's.
	 */
	@GameTest
	public void theHotbarIsLockedToTheWeaponsSlot(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		Inventory inventory = player.getInventory();
		WeaponSelector.give(player);
		inventory.setItem(3, new ItemStack(Items.BREAD));
		// Nothing picked yet: every slot is free to visit.
		helper.assertTrue(!WeaponLock.locked(player), "no weapon, no lock");
		inventory.setSelectedSlot(3);
		helper.assertTrue(!WeaponLock.refuseSlot(player, 3), "an unarmed player scrolls where they like");
		helper.assertValueEqual(inventory.getSelectedSlot(), 3, "and stays where they scrolled");
		// Armed: the weapon's slot, and nothing else.
		inventory.setItem(WeaponPicks.GIVEN_SLOT, new ItemStack(PaintWeapon.of(Weapon.ROLLER)));
		helper.assertTrue(WeaponLock.locked(player), "a weapon in its slot is the lock");
		helper.assertTrue(!WeaponLock.refuseSlot(player, WeaponPicks.GIVEN_SLOT), "the weapon's own slot is allowed");
		for (int slot = 1; slot < Inventory.getSelectionSize(); slot++) {
			helper.assertTrue(WeaponLock.refuseSlot(player, slot), "slot " + slot + " is refused");
			helper.assertValueEqual(inventory.getSelectedSlot(), WeaponPicks.GIVEN_SLOT,
					"and the hand is back on the weapon");
		}
		helper.assertTrue(!WeaponLock.maySelect(player, 4), "there is no second allowed slot any more");
		helper.succeed();
	}

	/**
	 * The weapon cannot be thrown away, and neither can the selector: one is the round and the other is the
	 * only way to a different one. Both drop actions come through the same question.
	 */
	@GameTest
	public void theLockedWeaponCannotBeDropped(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		Inventory inventory = player.getInventory();
		helper.assertTrue(!WeaponLock.refuseDrop(player), "an empty-handed player drops what they like");
		inventory.setItem(WeaponPicks.GIVEN_SLOT, new ItemStack(PaintWeapon.of(Weapon.SHOOTER)));
		// A selector in the hotbar is not a place a normal round can put one — it lives in the inventory grid
		// — but an operator handing themselves items can be holding anything, and it is still not droppable.
		inventory.setItem(4, WeaponSelector.stack());
		inventory.setItem(5, new ItemStack(Items.BREAD));
		inventory.setSelectedSlot(WeaponPicks.GIVEN_SLOT);
		helper.assertTrue(WeaponLock.refuseDrop(player), "the weapon is not droppable");
		helper.assertTrue(inventory.getItem(WeaponPicks.GIVEN_SLOT).getItem() instanceof PaintWeapon,
				"and is still in its slot");
		inventory.setSelectedSlot(4);
		helper.assertTrue(WeaponLock.refuseDrop(player), "nor is the selector");
		inventory.setSelectedSlot(5);
		helper.assertTrue(!WeaponLock.refuseDrop(player), "the bread is theirs to throw");
		helper.succeed();
	}

	/**
	 * Nor can it be dragged out of the inventory screen. Three shapes of click reach the weapon — the slot
	 * itself, the hotbar-swap key aimed at its slot from anywhere, and a paint weapon already on the cursor
	 * — and all three are refused and the menu resent; a click that touches nothing of ours is run.
	 */
	@GameTest
	public void theLockedWeaponCannotBeMovedInTheInventory(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		Inventory inventory = player.getInventory();
		inventory.setItem(WeaponPicks.GIVEN_SLOT, new ItemStack(PaintWeapon.of(Weapon.SLOSHER)));
		inventory.setItem(20, new ItemStack(Items.BREAD));
		int weaponSlot = -1;
		int breadSlot = -1;
		for (Slot slot : player.containerMenu.slots) {
			if (slot.container != inventory) continue;
			if (slot.getContainerSlot() == WeaponPicks.GIVEN_SLOT) weaponSlot = slot.index;
			if (slot.getContainerSlot() == 20) breadSlot = slot.index;
		}
		helper.assertTrue(weaponSlot >= 0 && breadSlot >= 0, "the inventory menu shows both slots");
		helper.assertTrue(WeaponLock.refuseContainerClick(player, weaponSlot, 0, ContainerInput.PICKUP),
				"a click on the weapon's slot is refused");
		helper.assertTrue(inventory.getItem(WeaponPicks.GIVEN_SLOT).getItem() instanceof PaintWeapon,
				"and the weapon has not moved");
		helper.assertTrue(WeaponLock.refuseContainerClick(player, breadSlot, WeaponPicks.GIVEN_SLOT, ContainerInput.SWAP),
				"and so is the hotbar-swap key aimed at it");
		helper.assertTrue(!WeaponLock.refuseContainerClick(player, breadSlot, 0, ContainerInput.PICKUP),
				"the bread is the player's own business");
		player.containerMenu.setCarried(new ItemStack(PaintWeapon.of(Weapon.SLOSHER)));
		helper.assertTrue(WeaponLock.refuseContainerClick(player, breadSlot, 0, ContainerInput.PICKUP),
				"a weapon on the cursor may not be put down anywhere");
		player.containerMenu.setCarried(ItemStack.EMPTY);
		helper.succeed();
	}

	/**
	 * The selector works from the inventory screen too, which with the hotbar locked is where it usually is.
	 * A click on it is not run — the compass must not be picked up or moved — and is the picker instead: the
	 * menu is resent, the screen closed and the dialog sent after it, in that order, because a dialog is
	 * drawn over whatever screen the client has open. Any click, any button, and whether or not the player
	 * has picked a weapon yet.
	 */
	@GameTest
	public void theSelectorOpensThePickerFromTheInventory(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		Inventory inventory = player.getInventory();
		inventory.setItem(WeaponPicks.GIVEN_SLOT, new ItemStack(PaintWeapon.of(Weapon.ROLLER)));
		WeaponSelector.give(player);
		int selectorSlot = -1;
		for (Slot slot : player.containerMenu.slots) {
			if (slot.container == inventory && slot.getContainerSlot() == WeaponSelector.SLOT) selectorSlot = slot.index;
		}
		helper.assertTrue(selectorSlot >= 0, "the inventory menu shows the selector's slot");
		int before = WeaponSelector.opens();
		helper.assertTrue(WeaponLock.refuseContainerClick(player, selectorSlot, 0, ContainerInput.PICKUP),
				"a click on the selector is not run");
		helper.assertTrue(WeaponSelector.is(inventory.getItem(WeaponSelector.SLOT)), "the selector is still in its slot");
		helper.assertValueEqual(WeaponSelector.opens(), before + 1, "and the picker was asked for");
		helper.assertValueEqual(inventory.getSelectedSlot(), WeaponPicks.GIVEN_SLOT, "with the hand back on the weapon");
		// A shift-click, a middle click, the swap key: every click on the selector means the same thing.
		helper.assertTrue(WeaponLock.refuseContainerClick(player, selectorSlot, 1, ContainerInput.QUICK_MOVE),
				"and so is a shift-click on it");
		helper.assertValueEqual(WeaponSelector.opens(), before + 2, "which is another pick request");
		// And a lobby player, who has no weapon and so no lock, reaches the picker the same way.
		inventory.setItem(WeaponPicks.GIVEN_SLOT, ItemStack.EMPTY);
		helper.assertTrue(!WeaponLock.locked(player), "nothing picked");
		helper.assertTrue(WeaponLock.refuseContainerClick(player, selectorSlot, 0, ContainerInput.PICKUP),
				"the selector still answers");
		helper.assertValueEqual(WeaponSelector.opens(), before + 3, "with the picker");
		helper.succeed();
	}

	/**
	 * Arming is what hands the selector out, and it is the only thing that does: a lobby player carries no
	 * kit at all, and one arm-up gives them the weapon in its slot and the selector in its own — which is
	 * the first free slot after a sweep, so arming used to write straight over it. Arming twice is still one
	 * selector, and {@link WeaponSelector#home} moves a stray one back without ever conjuring a second.
	 */
	@GameTest
	public void armingForAMatchGivesTheSelector(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		Lobby.receive(player); // adventure, no gun, no selector: a lobby player carries nothing of ours
		helper.assertTrue(!WeaponSelector.carried(player), "the lobby leaves a player no selector");
		ItemStack gun = Match.arm(player);
		helper.assertTrue(gun.getItem() instanceof PaintWeapon, "arming hands over a weapon");
		helper.assertTrue(player.getInventory().getItem(WeaponPicks.GIVEN_SLOT).getItem() instanceof PaintWeapon,
				"which is in its own slot");
		helper.assertTrue(WeaponSelector.is(player.getInventory().getItem(WeaponSelector.SLOT)),
				"and the selector with it, in its own slot at the grid's top right");
		helper.assertValueEqual(player.getInventory().getSelectedSlot(), WeaponPicks.GIVEN_SLOT, "with the gun in hand");
		// Armed again — a respawn, a mid-match join — and it is still one selector, not a second.
		Match.arm(player);
		helper.assertValueEqual(selectors(player), 1, "arming twice is still one selector");
		// A stray one is moved home; a player with none is left with none, which is what keeps the lobby
		// from resurrecting the kit the whistle took back.
		player.getInventory().setItem(WeaponSelector.SLOT, ItemStack.EMPTY);
		player.getInventory().setItem(30, WeaponSelector.stack());
		helper.assertTrue(WeaponSelector.home(player), "a stray selector is a thing to put right");
		helper.assertTrue(WeaponSelector.is(player.getInventory().getItem(WeaponSelector.SLOT)), "it is home again");
		helper.assertTrue(player.getInventory().getItem(30).isEmpty(), "and gone from where it was");
		helper.assertValueEqual(WeaponSelector.take(player), 1, "and taking it back takes exactly one");
		helper.assertTrue(!WeaponSelector.home(player), "home() hands nothing out to a player with none");
		helper.assertTrue(!WeaponSelector.carried(player), "so they still have none");
		helper.succeed();
	}

	/** How many weapon selectors a player is carrying, over the whole inventory. */
	private static int selectors(Player player) {
		int n = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (WeaponSelector.is(player.getInventory().getItem(slot))) n++;
		}
		return n;
	}

	/** How many paint weapons a player is carrying, over the whole inventory. */
	private static int paintWeapons(Player player) {
		int n = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).getItem() instanceof PaintWeapon) n++;
		}
		return n;
	}

	/**
	 * The pick survives a relog, which is the whole reason it is saved data rather than a field: it is
	 * written into the server's data storage and comes back out through the same codec. Round-tripped
	 * through the codec here rather than by restarting a server, which a game test cannot do.
	 */
	@GameTest
	public void theWeaponChoicePersists(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		WeaponChoice choices = WeaponChoice.of(helper.getLevel().getServer());
		try {
			helper.assertTrue(choices.get(player).isEmpty(), "nothing picked yet");
			helper.assertValueEqual(choices.orDefault(player), Weapon.SHOOTER, "so the shooter is what they would get");
			WeaponPicks.pick(player, Weapon.SLOSHER);
			helper.assertValueEqual(choices.get(player).orElse(null), Weapon.SLOSHER, "the pick is remembered");
			helper.assertTrue(choices.isDirty(), "and the saved data knows it has to be written");
			// The write and the read, as the level save and the next boot would do them.
			JsonElement written = WeaponChoice.CODEC.encodeStart(JsonOps.INSTANCE, choices)
					.getOrThrow(error -> new AssertionError("encode: " + error));
			WeaponChoice reloaded = WeaponChoice.CODEC.parse(JsonOps.INSTANCE, written)
					.getOrThrow(error -> new AssertionError("decode: " + error));
			helper.assertValueEqual(reloaded.get(player.getUUID()).orElse(null), Weapon.SLOSHER,
					"and it is still a slosher after a round trip");
			// Stored as the weapon's own id, so reordering the enum cannot hand anyone somebody else's gun.
			helper.assertTrue(written.toString().contains("slosher"), "written as an id: " + written);
		} finally {
			choices.forget(player.getUUID());
		}
		helper.succeed();
	}

	/**
	 * The special pick survives a relog the same way the weapon pick does, and for the same reason: it is
	 * a decision made in a lobby, and a restart may well happen between the lobby and the match. Written
	 * as the special's own id rather than an ordinal, so reordering the enum cannot hand anyone somebody
	 * else's bomb, and a player who never picked reads as empty rather than as the default — which is what
	 * decides whether they are asked.
	 */
	@GameTest
	public void theSpecialChoicePersists(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		SpecialChoice choices = SpecialChoice.of(helper.getLevel().getServer());
		try {
			helper.assertTrue(choices.get(player).isEmpty(), "nothing picked yet");
			helper.assertValueEqual(SpecialChoice.DEFAULT, Special.SPLAT_BOMB, "the default is the splat bomb");
			helper.assertValueEqual(choices.orDefault(player), Special.SPLAT_BOMB, "so that is what F throws");
			SpecialDialog.pick(player, Special.CURLING_BOMB);
			helper.assertValueEqual(choices.get(player).orElse(null), Special.CURLING_BOMB, "the pick is remembered");
			helper.assertTrue(choices.isDirty(), "and the saved data knows it has to be written");
			JsonElement written = SpecialChoice.CODEC.encodeStart(JsonOps.INSTANCE, choices)
					.getOrThrow(error -> new AssertionError("encode: " + error));
			SpecialChoice reloaded = SpecialChoice.CODEC.parse(JsonOps.INSTANCE, written)
					.getOrThrow(error -> new AssertionError("decode: " + error));
			helper.assertValueEqual(reloaded.get(player.getUUID()).orElse(null), Special.CURLING_BOMB,
					"and it is still a curling bomb after a round trip");
			helper.assertTrue(written.toString().contains("curling_bomb"), "written as an id: " + written);
		} finally {
			choices.forget(player.getUUID());
		}
		helper.succeed();
	}

	/**
	 * The special picker is the weapon picker's twin: one picture per special — the blob the bomb actually
	 * flies as, dyed in the viewer's colour and drawn at the size it is thrown at — what it does and what
	 * it costs written under it, one button each that runs the pick as the player, and a way out that keeps
	 * what they already throw.
	 */
	@GameTest
	public void theSpecialDialogListsEverySpecial(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.IT));
		SpecialChoice choices = SpecialChoice.of(helper.getLevel().getServer());
		try {
			MultiActionDialog dialog = SpecialDialog.build(player);
			helper.assertValueEqual(dialog.common().body().size(), 3, "one picture per special");
			helper.assertValueEqual(dialog.actions().size(), 3, "and one button per special");
			int index = 0;
			for (Special special : Special.values()) {
				ItemBody picture = (ItemBody) dialog.common().body().get(index);
				ItemStack icon = picture.item().create();
				DyedItemColor dye = icon.get(DataComponents.DYED_COLOR);
				helper.assertTrue(dye != null && dye.rgb() == PaintColor.IT.rgb,
						"picture " + index + " is dyed in the viewer's team colour, not " + dye);
				String said = picture.description().orElseThrow().contents().getString();
				helper.assertTrue(said.contains(special.displayName), special + " is named: " + said);
				helper.assertTrue(said.contains("ink"), special + "'s line says what it costs: " + said);
				helper.assertValueEqual(buttonCommand(dialog.actions().get(index)),
						"rivals special pick " + special.commandId(), "button " + index + " takes the " + special);
				index++;
			}
			// A burst bomb is a smaller bomb, so its picture is smaller: the row is to scale with itself.
			helper.assertTrue(SpecialDialog.iconSize(Special.BURST_BOMB) < SpecialDialog.iconSize(Special.SPLAT_BOMB),
					"the burst bomb is drawn smaller than the splat bomb");
			// With no pick of their own the splat bomb is the one marked, since that is what F throws.
			helper.assertTrue(((ItemBody) dialog.common().body().getFirst()).description().orElseThrow()
					.contents().getString().contains("(current)"), "the splat bomb is the one marked current");
			ActionButton exit = dialog.exitAction().orElseThrow();
			helper.assertTrue(exit.action().isEmpty(), "the way out runs no command");
			helper.assertTrue(exit.button().label().getString().contains(Special.SPLAT_BOMB.displayName),
					"and says what it keeps: " + exit.button().label().getString());
		} finally {
			choices.forget(player.getUUID());
		}
		helper.succeed();
	}

	/**
	 * And the buttons are real commands, runnable by a player with no permission at all — {@code /rivals
	 * special pick <id>} — because that is what a dialog button is. An id nothing answers to is a failure
	 * that lists the ones that work, and changes nothing.
	 */
	@GameTest
	public void theSpecialPickCommandTakesTheSpecial(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		SpecialChoice choices = SpecialChoice.of(server);
		List<String> said = new ArrayList<>();
		CommandSourceStack source = server.createCommandSourceStack().withSource(sink(said)).withEntity(player);
		try {
			server.getCommands().performPrefixedCommand(source, SpecialDialog.command(Special.BURST_BOMB));
			helper.assertValueEqual(choices.get(player).orElse(null), Special.BURST_BOMB, "the pick is remembered");
			said.clear();
			server.getCommands().performPrefixedCommand(source, "rivals special pick trombone");
			String refused = String.join(" | ", said);
			helper.assertTrue(refused.contains("trombone") && refused.contains("burst_bomb"),
					"an unknown id names itself and lists the real ones: " + refused);
			helper.assertValueEqual(choices.get(player).orElse(null), Special.BURST_BOMB, "and changed nothing");
		} finally {
			choices.forget(player.getUUID());
		}
		helper.succeed();
	}

	/**
	 * And the pick is what F throws: the same key, the same ink cost path, a different bomb. The refusal
	 * inside the wait names it too — a player who picked the burst bomb is told about a burst bomb.
	 */
	@GameTest
	public void theSpecialPickIsWhatFThrows(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		SpecialChoice choices = SpecialChoice.of(helper.getLevel().getServer());
		try {
			choices.set(player, Special.BURST_BOMB);
			ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
			long now = helper.getLevel().getServer().getTickCount();
			helper.assertTrue(PaintWeapon.swapHands(player), "F is still ours");
			List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0);
			helper.assertValueEqual(balls.size(), 1, "one bomb");
			PaintBall bomb = balls.getFirst();
			helper.assertValueEqual(bomb.special(), Special.BURST_BOMB, "and it is the one they picked");
			helper.assertValueEqual(bomb.blast(), Special.BURST_BLAST, "with the burst bomb's blast");
			helper.assertValueEqual(bomb.damage(), Special.BURST_DAMAGE, "and its damage");
			helper.assertValueEqual(Ink.get(gun), Ink.MAX - Special.BURST_INK, "for its own ink");
			// Its own wait, too: forty ticks rather than the splat bomb's eighty.
			helper.assertValueEqual(PaintWeapon.specialWait(player, now), (long) Special.BURST_COOLDOWN,
					"and its own wait");
			balls.forEach(Entity::discard);
		} finally {
			choices.forget(player.getUUID());
		}
		helper.succeed();
	}

	/**
	 * The selector the lobby hands out: one item, named, shown to the client as a compass that is not
	 * tracking anything, and its right click is the picker.
	 */
	@GameTest
	public void theWeaponSelectorIsANamedCompass(GameTestHelper helper) {
		ItemStack selector = WeaponSelector.stack();
		helper.assertTrue(WeaponSelector.is(selector), "it is a selector");
		helper.assertValueEqual(selector.get(DataComponents.ITEM_NAME), WeaponSelector.NAME,
				"named on the stack, because what the client gets is a compass");
		helper.assertTrue(WeaponSelector.get().getPolymerItem(selector, null) == Items.COMPASS, "shown as a compass");
		ItemStack client = WeaponSelector.get().getPolymerItemStack(selector, TooltipFlag.NORMAL, null,
				helper.getLevel().registryAccess());
		helper.assertTrue(client.get(DataComponents.LODESTONE_TRACKER) == null, "with no needle to spin");
		helper.assertValueEqual(client.get(DataComponents.ITEM_NAME), WeaponSelector.NAME, "and the name on the wire");
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.ADVENTURE));
		helper.assertTrue(!WeaponSelector.carried(player), "nobody carries one to start with");
		player.getInventory().setItem(3, WeaponSelector.stack());
		helper.assertTrue(WeaponSelector.carried(player), "and one in any slot counts, so the lobby hands out no second");
		helper.succeed();
	}

	/**
	 * A team's spawn is the stance of whoever set it, it is kept per team and per level, and it survives a
	 * restart — which is the point of it being saved data: setting an arena up is work an operator does
	 * once. Round-tripped through the codec, since a game test cannot reboot a server.
	 */
	@GameTest
	public void arenaSpawnsAreSavedPerTeam(GameTestHelper helper) {
		Arena arena = Arena.of(helper.getLevel());
		try {
			arena.forget();
			helper.assertTrue(arena.spawn(PaintColor.DATA).isEmpty(), "no DATA spawn to start with");
			helper.assertTrue(!arena.spawnsReady(), "so the arena is not ready");
			ServerPlayer player = connected(mockServerPlayer(helper, GameType.CREATIVE));
			Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 3.5));
			player.setPos(at.x, at.y, at.z);
			player.setYRot(135f);
			player.setXRot(-10f);
			arena.setSpawn(PaintColor.DATA, player);
			Arena.Spawn data = arena.spawn(PaintColor.DATA).orElseThrow();
			helper.assertTrue(data.pos().distanceTo(at) < 1.0e-6, "the spawn is where the setter stood: " + data);
			helper.assertValueEqual(data.yaw(), 135f, "and looks where they looked");
			helper.assertValueEqual(data.pitch(), -10f, "pitch too");
			helper.assertTrue(!arena.spawnsReady(), "one team is not both");
			arena.setSpawn(PaintColor.IT, new Arena.Spawn(at.add(8, 0, 0), -45f, 0f));
			helper.assertTrue(arena.spawnsReady(), "both teams set: the arena is ready");
			// Written and read back, as the level save and the next boot do it.
			JsonElement written = Arena.CODEC.encodeStart(JsonOps.INSTANCE, arena)
					.getOrThrow(error -> new AssertionError("encode: " + error));
			Arena reloaded = Arena.CODEC.parse(JsonOps.INSTANCE, written)
					.getOrThrow(error -> new AssertionError("decode: " + error));
			helper.assertTrue(reloaded.spawnsReady(), "both spawns came back");
			helper.assertValueEqual(reloaded.spawn(PaintColor.DATA).orElseThrow().yaw(), 135f, "with their looks");
			helper.assertTrue(reloaded.box().isEmpty(), "and no bounds, since none were set");
		} finally {
			arena.forget();
		}
		helper.succeed();
	}

	/**
	 * With bounds set, paint outside them is refused outright — on a full face (a paint block) and on a
	 * face that is not full (display quads) alike — and a reset clears only what is inside. The box is one
	 * table for the level and the tests in a batch tick side by side, so everything here happens inside
	 * one tick and the bounds are taken off again whatever happens.
	 */
	@GameTest
	public void arenaBoundsFenceThePaintIn(GameTestHelper helper) {
		Arena arena = Arena.of(helper.getLevel());
		BlockPos in = new BlockPos(2, 1, 2);
		BlockPos out = new BlockPos(6, 1, 6);
		BlockPos slabIn = new BlockPos(3, 1, 2);
		helper.setBlock(in, Blocks.STONE);
		helper.setBlock(out, Blocks.STONE);
		helper.setBlock(slabIn, Blocks.STONE_SLAB);
		PaintTally tally = PaintTally.of(helper.getLevel());
		try {
			arena.forget();
			helper.assertTrue(arena.inside(helper.absolutePos(out)), "with no bounds everything is inside");
			// A box around the near corner only, tall enough to hold the cells above the floor.
			arena.setBox(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(4, 6, 4)));
			helper.assertTrue(arena.inside(helper.absolutePos(in)), "the near floor is inside");
			helper.assertTrue(!arena.inside(helper.absolutePos(out)), "and the far one is not");
			helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(in), Direction.UP, PaintColor.DATA),
					"paint lands inside the bounds");
			helper.assertTrue(!Painter.paintFace(helper.getLevel(), helper.absolutePos(out), Direction.UP, PaintColor.DATA),
					"and is refused outside them");
			helper.assertTrue(helper.getBlockState(out.above()).isAir(), "nothing was written out there");
			// The other door into the same room: a slab takes display quads, and those are fenced in too.
			helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(slabIn), Direction.UP, PaintColor.DATA),
					"a slab inside takes quads");
			helper.assertTrue(PaintDisplays.of(helper.getLevel()).faceAt(helper.absolutePos(slabIn.above())) != null,
					"which are there");
			helper.setBlock(out, Blocks.STONE_SLAB);
			helper.assertTrue(!PaintDisplays.of(helper.getLevel()).paint(helper.getLevel(), helper.absolutePos(out),
					Direction.UP, PaintColor.DATA), "a slab outside takes none");
			// Take the bounds off and the same shot lands.
			helper.setBlock(out, Blocks.STONE);
			helper.assertTrue(arena.clearBox(), "there were bounds to clear");
			helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(out), Direction.UP, PaintColor.DATA),
					"and now the far floor takes paint");
			// A reset with bounds clears the inside and leaves the outside where it is.
			arena.setBox(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(4, 6, 4)));
			int removed = tally.reset(helper.getLevel(), arena.box().orElseThrow());
			helper.assertTrue(removed >= 1, "the inside was cleared: " + removed);
			helper.assertTrue(helper.getBlockState(in.above()).isAir(), "the near cell is air");
			helper.assertTrue(isPaint(helper.getBlockState(out.above()), PaintColor.DATA),
					"and the far one still holds its paint");
		} finally {
			arena.forget();
			// Leave nothing painted behind for the tests that count faces — inside this structure only. A
			// level-wide reset here took every tracked paint block in the level with it, including the
			// paint a ball in flight in another test had just put down, and that test then failed at random
			// depending on how the batch happened to be laid out.
			helper.setBlock(out.above(), Blocks.AIR);
			AABB bounds = helper.getBounds();
			tally.reset(helper.getLevel(), BoundingBox.fromCorners(
					BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
					BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)));
		}
		helper.succeed();
	}

	/**
	 * {@code /rivals arena show} traces the box's twelve edges. The step grows with the box, so a
	 * hundred-block arena does not ask a client for ten thousand particles in one packet.
	 */
	@GameTest
	public void arenaOutlineTracesEveryEdge(GameTestHelper helper) {
		BoundingBox small = BoundingBox.fromCorners(new BlockPos(0, 0, 0), new BlockPos(3, 3, 3));
		List<Vec3> points = Arena.outlinePoints(small);
		for (double x : new double[] {0.0, 4.0}) {
			for (double y : new double[] {0.0, 4.0}) {
				for (double z : new double[] {0.0, 4.0}) {
					Vec3 corner = new Vec3(x, y, z);
					helper.assertTrue(points.stream().anyMatch(at -> at.distanceTo(corner) < 1.0e-6),
							"the outline reaches the corner " + corner);
				}
			}
		}
		BoundingBox huge = BoundingBox.fromCorners(new BlockPos(0, 0, 0), new BlockPos(400, 200, 400));
		List<Vec3> many = Arena.outlinePoints(huge);
		helper.assertTrue(many.size() <= 700, "a big box is still a sane number of particles: " + many.size());
		helper.assertTrue(many.size() >= 12, "but it is still twelve edges: " + many.size());
		// Nobody is being shown anything when there is no box to show.
		Arena.clearShows();
		Arena arena = Arena.of(helper.getLevel());
		try {
			arena.forget();
			ServerPlayer viewer = connected(mockServerPlayer(helper, GameType.CREATIVE));
			helper.assertTrue(!Arena.show(helper.getLevel(), viewer), "no box, nothing to show");
			helper.assertValueEqual(Arena.shows(), 0, "and nobody is watching");
			arena.setBox(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(4, 4, 4)));
			helper.assertTrue(Arena.show(helper.getLevel(), viewer), "with a box there is");
			helper.assertValueEqual(Arena.shows(), 1, "one watcher");
		} finally {
			arena.forget();
			Arena.clearShows();
		}
		helper.succeed();
	}

	/**
	 * {@code /rivals ready} lines everybody up: name, side and the weapon they picked, grouped by side with
	 * whoever is on neither last — and it fails, naming them, if anybody is on neither, which is what
	 * {@code match start} leans on. Spectators are left out rather than counted as teamless.
	 *
	 * <p>A side is a plain vanilla scoreboard team under the name {@link TeamNames} gives it, so a mock
	 * player joins one the way a real one does: {@code /team join}, which is what {@code addPlayerToTeam}
	 * is underneath.
	 */
	@GameTest
	public void readinessNamesWhoeverIsOnNoTeam(GameTestHelper helper) {
		ServerScoreboard board = helper.getLevel().getScoreboard();
		ServerPlayer onData = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		ServerPlayer onIt = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		ServerPlayer teamless = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		ServerPlayer watching = connected(mockServerPlayer(helper, GameType.SPECTATOR));
		WeaponChoice choices = WeaponChoice.of(helper.getLevel().getServer());
		try {
			board.addPlayerToTeam(onData.getScoreboardName(), team(helper, PaintColor.DATA));
			board.addPlayerToTeam(onIt.getScoreboardName(), team(helper, PaintColor.IT));
			choices.set(onData, Weapon.ROLLER);
			// Listed teamless-first on the way in, to prove the report groups them rather than echoing the order.
			List<ServerPlayer> everyone = List.of(teamless, onIt, watching, onData);
			Readiness.Report report = Readiness.of(everyone);
			helper.assertValueEqual(report.lines().size(), 3, "the spectator is not playing");
			helper.assertFalse(report.ready(), "and one of the three is on no team");
			helper.assertValueEqual(report.teamless().size(), 1, "exactly one");
			helper.assertValueEqual(report.teamlessNames(), teamless.getScoreboardName(),
					"named: " + report.teamlessNames());
			helper.assertValueEqual(report.on(PaintColor.DATA), 1, "one on DATA");
			helper.assertValueEqual(report.on(PaintColor.IT), 1, "and one on IT");
			// Grouped: DATA, then IT, then whoever is on neither.
			Readiness.Line first = report.lines().get(0);
			helper.assertValueEqual(first.team().orElse(null), PaintColor.DATA, "DATA comes first");
			helper.assertValueEqual(first.player(), onData, "and it is the player on DATA");
			helper.assertValueEqual(first.weapon().orElse(null), Weapon.ROLLER, "their pick is in the line");
			helper.assertTrue(first.text().contains("DATA") && first.text().contains("Paint Roller"),
					"the printed line says both: " + first.text());
			Readiness.Line second = report.lines().get(1);
			helper.assertValueEqual(second.team().orElse(null), PaintColor.IT, "then IT");
			helper.assertTrue(second.weapon().isEmpty() && second.text().contains("none yet"),
					"and a player who never picked says so: " + second.text());
			Readiness.Line last = report.lines().get(2);
			helper.assertTrue(last.team().isEmpty() && last.text().contains("no team"),
					"with the teamless one last: " + last.text());
			// Put the last one on a team and the report goes green.
			board.addPlayerToTeam(teamless.getScoreboardName(), team(helper, PaintColor.IT));
			Readiness.Report after = Readiness.of(everyone);
			helper.assertTrue(after.ready(), "everybody on a side: ready");
			helper.assertTrue(after.teamless().isEmpty(), "nobody left to name");
			helper.assertValueEqual(after.on(PaintColor.IT), 2, "two on IT now");
			// And nobody at all is not ready either: there is no match without players.
			helper.assertFalse(Readiness.of(List.of(watching)).ready(), "a lone spectator is not a match");
		} finally {
			choices.forget(onData.getUUID());
			board.removePlayerFromTeam(onData.getScoreboardName());
			board.removePlayerFromTeam(onIt.getScoreboardName());
			board.removePlayerFromTeam(teamless.getScoreboardName());
		}
		helper.succeed();
	}

	/**
	 * Two players on the two sides, a one-minute match, and the whole machine walked through on a clock of
	 * the test's own: LOBBY → COUNTDOWN → PLAYING → ENDED → LOBBY.
	 *
	 * <p>Everything here happens inside one server tick. {@link Match} is one machine for the server and
	 * its own {@code END_SERVER_TICK} hook drives it off the real tick count, so a test that spanned ticks
	 * would have the real clock and this fake one fighting over the same state; and the fake ticks start a
	 * long way past any real tick count so the two can never be confused. The state is put back whatever
	 * happens, since every other test in the batch shares it.
	 */
	@GameTest
	public void matchWalksFromCountdownToLobby(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerScoreboard board = level.getScoreboard();
		Arena arena = Arena.of(level);
		WeaponChoice choices = WeaponChoice.of(level.getServer());
		ServerPlayer one = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		ServerPlayer two = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		ServerPlayer late = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		List<ServerPlayer> players = List.of(one, two);
		long t = 1_000_000L;
		try {
			arena.forget();
			fenceIn(helper, arena);
			board.addPlayerToTeam(one.getScoreboardName(), team(helper, PaintColor.DATA));
			// No spawns, no match: there is nowhere to put anybody.
			helper.assertFalse(Match.start(level.getServer(), level, () -> players, 1, false, t).started(),
					"a match with no spawns is refused");
			Vec3 dataAt = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
			Vec3 itAt = helper.absoluteVec(new Vec3(6.5, 2.0, 6.5));
			arena.setSpawn(PaintColor.DATA, new Arena.Spawn(dataAt, 0f, 0f));
			arena.setSpawn(PaintColor.IT, new Arena.Spawn(itAt, 180f, 0f));
			choices.set(two, Weapon.CHARGER);
			// A player on neither team is refused without force, and let in with it.
			Match.Result refused = Match.start(level.getServer(), level, () -> players, 1, false, t);
			helper.assertFalse(refused.started(), "a player on no team stops the start");
			helper.assertTrue(refused.message().getString().contains(two.getScoreboardName()),
					"and is named: " + refused.message().getString());
			board.addPlayerToTeam(two.getScoreboardName(), team(helper, PaintColor.IT));

			Match.Result started = Match.start(level.getServer(), level, () -> players, 1, false, t);
			helper.assertTrue(started.started(), "both on a side: " + started.message().getString());
			helper.assertValueEqual(Match.state(), Match.State.COUNTDOWN, "counting down");
			helper.assertValueEqual(Match.ticksLeft(t), (long) Match.COUNTDOWN_TICKS, "five seconds of it");
			helper.assertTrue(Match.isFrozen(one) && Match.isFrozen(two), "and nobody can move");
			// The sides are the scoreboard teams they joined, under the configured names.
			helper.assertValueEqual(PaintColor.byTeam(one.getTeam()).orElse(null), PaintColor.DATA, "one is DATA");
			helper.assertValueEqual(PaintColor.byTeam(two.getTeam()).orElse(null), PaintColor.IT, "two is IT");
			// Each is holding the weapon they picked — the shooter for the one who never picked.
			helper.assertTrue(one.getInventory().getItem(0).getItem() == PaintWeapon.of(Weapon.SHOOTER),
					"no pick means a shooter");
			helper.assertTrue(two.getInventory().getItem(0).getItem() == PaintWeapon.of(Weapon.CHARGER),
					"and a pick means the pick");

			Match.tick(level.getServer(), t + 1);
			helper.assertValueEqual(Match.state(), Match.State.COUNTDOWN, "a tick in, still counting");
			Match.tick(level.getServer(), t + Match.COUNTDOWN_TICKS);
			helper.assertValueEqual(Match.state(), Match.State.PLAYING, "GO");
			helper.assertTrue(!Match.isFrozen(one) && !Match.isFrozen(two), "and the freeze is off");
			long playing = t + Match.COUNTDOWN_TICKS;
			helper.assertValueEqual(Match.ticksLeft(playing), 60L * 20L, "a minute on the clock");
			helper.assertTrue(Match.clock(Match.ticksLeft(playing)).equals("⏱ 1:00"),
					"which reads " + Match.clock(Match.ticksLeft(playing)));

			// A player who turns up mid-match on a side is armed and given the respawn grace rather than
			// dropped straight into a firefight; one on no side is not let in at all.
			helper.assertFalse(Match.addMidMatch(late, playing), "a latecomer on no team is not let in");
			board.addPlayerToTeam(late.getScoreboardName(), team(helper, PaintColor.DATA));
			helper.assertTrue(Match.addMidMatch(late, playing), "but one on a team is");
			helper.assertTrue(late.getInventory().getItem(0).getItem() instanceof PaintWeapon, "with a weapon");
			helper.assertTrue(Match.isFrozen(late) && Match.isRespawning(late, playing), "and a moment to look around");

			Match.tick(level.getServer(), playing + 60L * 20L);
			helper.assertValueEqual(Match.state(), Match.State.ENDED, "time is up");
			helper.assertTrue(Match.isFrozen(one), "everybody is frozen for the result");
			helper.assertTrue(!Match.finalCounts().isEmpty(), "and the paint was counted");
			// A match that runs out of clock takes the kit back exactly as a stopped one does.
			helper.assertValueEqual(paintWeapons(one) + selectors(one), 0, "one is carrying nothing of ours");
			helper.assertValueEqual(paintWeapons(two) + selectors(two), 0, "and neither is two");
			helper.assertTrue(!WeaponLock.locked(one) && !WeaponLock.locked(two), "with no lock on either");
			Match.tick(level.getServer(), playing + 60L * 20L + Match.ENDED_TICKS);
			helper.assertValueEqual(Match.state(), Match.State.LOBBY, "and ten seconds later it is the lobby again");
			helper.assertTrue(!Match.isFrozen(one) && !Match.isFrozen(two), "with nobody frozen");
			helper.assertValueEqual(selectors(one) + selectors(two), 0, "and nobody handed a selector on the way");
		} finally {
			Match.clearAll();
			arena.forget();
			choices.forget(one.getUUID());
			choices.forget(two.getUUID());
			board.removePlayerFromTeam(one.getScoreboardName());
			board.removePlayerFromTeam(two.getScoreboardName());
			board.removePlayerFromTeam(late.getScoreboardName());
		}
		helper.succeed();
	}

	/**
	 * The countdown puts the weapon picker in front of anybody who has never picked one, and only them:
	 * five frozen seconds is the one moment in a round with nothing else to do, and taking the screen away
	 * from somebody who is watching the numbers count down would be worse than useless. A player who does
	 * not answer keeps the shooter they were already handed.
	 *
	 * <p>Counted rather than seen: a mock player's connection swallows what is sent to it, so what is
	 * assertable is who {@code Match.start} asks — which is what {@link Match#askUnarmed} answers.
	 */
	@GameTest
	public void theCountdownAsksWhoeverNeverPickedAWeapon(GameTestHelper helper) {
		ServerScoreboard board = helper.getLevel().getScoreboard();
		ServerPlayer picked = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		ServerPlayer never = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		ServerPlayer teamless = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		WeaponChoice choices = WeaponChoice.of(helper.getLevel().getServer());
		SpecialChoice specials = SpecialChoice.of(helper.getLevel().getServer());
		try {
			board.addPlayerToTeam(picked.getScoreboardName(), team(helper, PaintColor.DATA));
			board.addPlayerToTeam(never.getScoreboardName(), team(helper, PaintColor.IT));
			choices.set(picked, Weapon.SLOSHER);
			specials.set(picked, Special.BURST_BOMB);
			List<ServerPlayer> everyone = List.of(picked, never, teamless);
			helper.assertValueEqual(Match.askUnarmed(Readiness.of(everyone)), 1,
					"only the player who never picked is asked");
			// A weapon but no special is still a player with half a loadout, and the other half is asked
			// for on its own rather than stacked on top of a weapon picker they never saw.
			choices.set(never, Weapon.ROLLER);
			helper.assertValueEqual(Match.askUnarmed(Readiness.of(everyone)), 1,
					"now it is the special they have never picked");
			specials.set(never, Special.CURLING_BOMB);
			helper.assertValueEqual(Match.askUnarmed(Readiness.of(everyone)), 0,
					"and once everybody has both, the countdown is left alone");
		} finally {
			choices.forget(picked.getUUID());
			choices.forget(never.getUUID());
			specials.forget(picked.getUUID());
			specials.forget(never.getUUID());
			board.removePlayerFromTeam(picked.getScoreboardName());
			board.removePlayerFromTeam(never.getScoreboardName());
		}
		helper.succeed();
	}

	/**
	 * {@code /rivals match stop} blows the whistle early: straight from PLAYING to ENDED — and it takes the
	 * round with it. Johan stopped a match and was left holding the weapon selector, so the whistle now
	 * takes the whole kit back (the weapon in its slot, the selector in its own, the lock with them) and
	 * takes every Rivals boss bar off every screen; the ticks that follow, in ENDED and then in the lobby,
	 * must not put any of it back.
	 *
	 * <p>This is the <b>only</b> test that starts and stops a round, and it has to stay that way.
	 * {@code Match} is one machine for the whole server and an {@code Arena} is one per level, so a second
	 * test that drove a round would be fighting this one for both while the rest of the batch ticks beside
	 * it — which is exactly what it did when MAIN's running flag was first tested by starting a real round:
	 * paint balls in flight in other tests found their shooter teleported to this arena's spawn, and three
	 * paint tests failed at random. {@link MainPack#edge} exists so that the flag's decision can be
	 * asserted without any of that.
	 */
	@GameTest
	public void matchStopEndsItEarly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerScoreboard board = level.getScoreboard();
		Arena arena = Arena.of(level);
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		List<ServerPlayer> players = List.of(player);
		long t = 2_000_000L;
		try {
			arena.forget();
			fenceIn(helper, arena);
			board.addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
			arena.setSpawn(PaintColor.DATA, new Arena.Spawn(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)), 0f, 0f));
			arena.setSpawn(PaintColor.IT, new Arena.Spawn(helper.absoluteVec(new Vec3(6.5, 2.0, 6.5)), 0f, 0f));

			// This arena is one MAIN could start a round in, which is the half of the flag's job that
			// needs a level with spawns. The edges themselves are theRunningFlagReadsOnTheEdges.
			helper.assertTrue(MainPack.arenaLevel(level.getServer()).isPresent(),
					"a level with a spawn for both sides is an arena the running flag can play in");

			helper.assertFalse(Match.stop(t, helper.getLevel().getServer()), "nothing to stop in the lobby");
			helper.assertTrue(Match.start(level.getServer(), level, () -> players, 5, false, t).started(), "started");
			Match.tick(level.getServer(), t + Match.COUNTDOWN_TICKS);
			helper.assertValueEqual(Match.state(), Match.State.PLAYING, "playing");
			// A second start while one is running is refused rather than restarting it.
			helper.assertFalse(Match.start(level.getServer(), level, () -> players, 5, false, t).started(),
					"one match at a time");
			// Mid-round: the kit is in their hands, and the bars — the timer and a score bar over the paint
			// they have put down — are on their screen.
			helper.assertTrue(WeaponLock.locked(player), "the weapon is in its slot and locked to it");
			helper.assertTrue(WeaponSelector.is(player.getInventory().getItem(WeaponSelector.SLOT)),
					"with the selector in its own");
			// A score bar exists only over paint, so one cell of it, by hand: not through the Painter, whose
			// blob spends the level's shared random and would have every other test in the batch rolling
			// different numbers.
			BlockPos cell = new BlockPos(2, 2, 2);
			helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
			helper.setBlock(cell, PaintBlocks.splat(PaintColor.DATA).defaultBlockState()
					.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true));
			PaintTally.of(level).track(helper.absolutePos(cell));
			ScoreBars.refresh(level.getServer(), players);
			helper.assertTrue(ScoreBars.shows(player), "the score bar over the paint they put down is on them");
			helper.assertTrue(Match.showsAnyBar(player), "and so is the timer");

			helper.assertTrue(Match.stop(t + 400, helper.getLevel().getServer()), "stopped early");
			helper.assertValueEqual(Match.state(), Match.State.ENDED, "which is the same ending");
			helper.assertTrue(Match.isFrozen(player), "and the same freeze");
			// The whistle: no kit, no lock, no bars.
			helper.assertValueEqual(paintWeapons(player), 0, "the weapon went back with the whistle");
			helper.assertValueEqual(selectors(player), 0, "and so did the selector");
			helper.assertTrue(!WeaponLock.locked(player), "so nothing is locked to a slot");
			helper.assertTrue(!ScoreBars.shows(player), "the score bar came off with it");
			helper.assertTrue(!Match.showsAnyBar(player), "and no Rivals boss bar is left on the screen");
			// The ten seconds of ENDED, then the lobby, then a while in it: nothing hands any of it back.
			Match.tick(level.getServer(), t + 500);
			ScoreBars.refresh(level.getServer(), players);
			helper.assertValueEqual(selectors(player), 0, "ENDED gives no selector back");
			helper.assertTrue(!Match.showsAnyBar(player), "nor a boss bar");
			Match.tick(level.getServer(), t + 400 + Match.ENDED_TICKS);
			helper.assertValueEqual(Match.state(), Match.State.LOBBY, "and then it is the lobby");
			for (int tick = 1; tick <= 3; tick++) {
				Match.tick(level.getServer(), t + 400 + Match.ENDED_TICKS + tick);
				ScoreBars.refresh(level.getServer(), players);
			}
			helper.assertValueEqual(selectors(player), 0, "which does not give the selector back either");
			helper.assertValueEqual(paintWeapons(player), 0, "nor a weapon");
			helper.assertTrue(!Match.showsAnyBar(player), "nor a boss bar, however long it ticks");
			helper.assertTrue(!Match.isFrozen(player), "and nobody is frozen in a lobby");
		} finally {
			Match.clearAll();
			// The paint this test put down to make a score bar exist, off the level's shared tally again.
			Arena.of(level).box().ifPresent(box -> PaintTally.of(level).reset(level, box));
			arena.forget();
			board.removePlayerFromTeam(player.getScoreboardName());
		}
		helper.succeed();
	}

	/**
	 * The result is read off the tally: most faces wins, equal shares are a draw, and a match nobody
	 * painted in is a draw too rather than a win for whichever team the enum lists first.
	 */
	@GameTest
	public void matchWinnerComesFromTheTally(GameTestHelper helper) {
		Map<PaintColor, Integer> dataAhead = new EnumMap<>(PaintColor.class);
		dataAhead.put(PaintColor.DATA, 61);
		dataAhead.put(PaintColor.IT, 39);
		helper.assertValueEqual(Match.decide(dataAhead), PaintColor.DATA, "more faces wins");
		helper.assertTrue(Match.percentages(dataAhead).equals("DATA 61 % · IT 39 %"),
				"both percentages are printed: " + Match.percentages(dataAhead));
		Map<PaintColor, Integer> tied = new EnumMap<>(PaintColor.class);
		tied.put(PaintColor.DATA, 7);
		tied.put(PaintColor.IT, 7);
		helper.assertTrue(Match.decide(tied) == null, "equal is a draw");
		helper.assertTrue(Match.decide(Map.of()) == null, "and so is a match nobody painted in");
		Map<PaintColor, Integer> itAhead = new EnumMap<>(PaintColor.class);
		itAhead.put(PaintColor.DATA, 0);
		itAhead.put(PaintColor.IT, 1);
		helper.assertValueEqual(Match.decide(itAhead), PaintColor.IT, "one face is enough");
		helper.assertTrue(Match.clock(0).equals("⏱ 0:00") && Match.clock(20 * 125).equals("⏱ 2:05"),
				"the clock reads m:ss: " + Match.clock(20 * 125));
		helper.succeed();
	}

	/**
	 * The lobby takes the whole Rivals kit back: every paint weapon and the weapon selector with them, so a
	 * player between matches is carrying nothing this mod ever handed them — which is the bug Johan found,
	 * a compass left in his inventory by a match he had stopped. Everything else in the inventory is left
	 * where it is, and nothing in the lobby hands any of it out again.
	 */
	@GameTest
	public void theLobbyTakesTheKitBack(GameTestHelper helper) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		PaintWeapon.giveKit(player);
		player.getInventory().setItem(25, new ItemStack(Items.BREAD, 3));
		player.getInventory().setItem(WeaponSelector.SLOT, WeaponSelector.stack());
		helper.assertValueEqual(paintWeapons(player), 4, "four guns to start with");
		helper.assertTrue(WeaponSelector.carried(player), "and a selector");
		int taken = Lobby.receive(player);
		helper.assertValueEqual(taken, 5, "the lobby took all four guns and the selector");
		helper.assertValueEqual(paintWeapons(player), 0, "and left no gun behind");
		helper.assertTrue(!WeaponSelector.carried(player), "nor a selector anywhere in the inventory");
		helper.assertTrue(!WeaponLock.locked(player), "so nothing is locked to a slot any more");
		helper.assertValueEqual(player.getInventory().getItem(25).getCount(), 3, "the bread is untouched");
		// Twice through the lobby is still nothing: the lobby has nothing to hand out.
		Lobby.receive(player);
		helper.assertValueEqual(Lobby.receive(player), 0, "a second pass finds nothing left to take");
		helper.assertValueEqual(selectors(player), 0, "still no selector");
		// A selector that has wandered out of its slot is taken back too, wherever it is.
		player.getInventory().setItem(30, WeaponSelector.stack());
		helper.assertValueEqual(Lobby.receive(player), 1, "a stray selector goes with the rest");
		helper.assertTrue(player.getInventory().getItem(30).isEmpty(), "out of the slot it had wandered to");
		// A player frozen by the end of a match is thawed by the lobby: nobody stands still between rounds.
		Match.freeze(player);
		helper.assertTrue(Match.isFrozen(player), "frozen for the result");
		Lobby.receive(player);
		helper.assertTrue(!Match.isFrozen(player), "and free again in the lobby");
		helper.succeed();
	}

	/**
	 * Coming back to life outside a match: a dressed player lands on their own team's spawn, and one with
	 * no team lands at the world spawn instead of wherever they happened to die.
	 */
	@GameTest
	public void theLobbyRespawnsOnTheTeamSpawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Arena arena = Arena.of(level);
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.IT));
		try {
			arena.forget();
			Vec3 itAt = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
			arena.setSpawn(PaintColor.IT, new Arena.Spawn(itAt, 90f, 5f));
			player.setPos(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
			Lobby.sendToSpawn(player);
			helper.assertTrue(player.position().distanceTo(itAt) < 1.0e-3,
					"a team player lands on their team's spawn, not " + player.position());
			helper.assertValueEqual(player.getYRot(), 90f, "facing the way the spawn faces");
			// With no spawn for their colour there is nothing to send them to but the world spawn.
			arena.forget();
			BlockPos world = level.getRespawnData().pos();
			Lobby.sendToSpawn(player);
			helper.assertTrue(player.position().distanceTo(new Vec3(world.getX() + 0.5, world.getY(), world.getZ() + 0.5)) < 1.0e-3,
					"and with no team spawn, the world's own, not " + player.position());
		} finally {
			arena.forget();
			helper.getLevel().getScoreboard().removePlayerFromTeam(player.getScoreboardName(), team(helper, PaintColor.IT));
		}
		helper.succeed();
	}

	/**
	 * Arena bounds around this test's own structure and nothing else.
	 *
	 * <p>{@code Match.start} clears the arena's paint, and an arena with no box <em>is</em> the whole level
	 * — so a match test that started without bounds wiped the paint of every other test in the batch,
	 * including one mid-flight ten ticks from its assertion. Fencing the match in is the same thing an
	 * operator does with {@code /rivals arena set}.
	 */
	private static void fenceIn(GameTestHelper helper, Arena arena) {
		AABB bounds = helper.getBounds();
		arena.setBox(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
				BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ));
	}

	private static PlayerTeam team(GameTestHelper helper, PaintColor color) {
		ServerScoreboard board = helper.getLevel().getScoreboard();
		// Under the configured name, so the tests follow teams.json the way a player's /team join does.
		PlayerTeam team = board.getPlayerTeam(TeamNames.nameOf(color));
		return team != null ? team : board.addPlayerTeam(TeamNames.nameOf(color));
	}

	/**
	 * Mock players, one scoreboard identity each.
	 *
	 * <p>Vanilla's {@code makeMockPlayer} and {@code makeMockServerPlayer} both name their player
	 * {@code test-mock-player}, and a scoreboard team is keyed by that name — so every mock in the run
	 * is the same team member, and the tests in a batch tick side by side. One test putting its mock on
	 * DATA put every other test's mock on DATA too, and the test that then cleared the name took them
	 * all off again; that is how a friendly-fire assertion came to pass for the wrong reason. These
	 * build the same two anonymous subclasses vanilla does, with a profile name nothing else in the run
	 * shares, so a team joined here is joined by this test's player alone and no test has to clear up
	 * after another one.
	 *
	 * <p>The counter is per JVM and the tag per class-load, because the game-test world — scoreboard and
	 * all — is saved and reused between runs: a bare counter would hand out {@code mock-1} again next
	 * run and find it still on a team.
	 */
	private static final AtomicInteger MOCKS = new AtomicInteger();
	private static final String MOCK_TAG = Integer.toHexString((int) (System.nanoTime() & 0xFFFFFF));

	private static GameProfile mockProfile() {
		return new GameProfile(UUID.randomUUID(), "mock-" + MOCK_TAG + "-" + MOCKS.incrementAndGet());
	}

	/** Vanilla's {@code makeMockPlayer}, with a name of its own. Not added to the level; callers do that. */
	private static Player mockPlayer(GameTestHelper helper, GameType mode) {
		return new Player(helper.getLevel(), mockProfile()) {
			@Override
			public GameType gameMode() {
				return mode;
			}

			@Override
			public boolean isClientAuthoritative() {
				return false;
			}
		};
	}

	/** Vanilla's {@code makeMockServerPlayer}, with a name of its own: a ServerPlayer, but no connection. */
	private static ServerPlayer mockServerPlayer(GameTestHelper helper, GameType mode) {
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), mockProfile(),
				ClientInformation.createDefault()) {
			@Override
			public GameType gameMode() {
				return mode;
			}

			@Override
			public boolean isClientAuthoritative() {
				return false;
			}
		};
		mode.updatePlayerAbilities(player.getAbilities());
		return player;
	}

	/**
	 * Gives a mock server player a connection that swallows whatever is sent to it. Vanilla's helper builds one
	 * without a connection at all, and a particle packet aimed at a single viewer — which is how every paint
	 * burst goes out now, so that none of them lands on a camera — is written straight to
	 * {@code player.connection} and throws on a null one. The listener's constructor is what sets that field;
	 * the override keeps the packet off the wire, since there is no channel under it.
	 */
	private static ServerPlayer connected(ServerPlayer player) {
		new ServerGamePacketListenerImpl(player.level().getServer(), new Connection(PacketFlow.SERVERBOUND), player,
				CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
			@Override
			public void send(Packet<?> packet) {}
		};
		return player;
	}

	/**
	 * A mock survival <em>server</em> player on {@code color}'s team, for the wall climb: only a
	 * {@link ServerPlayer} carries the client input {@code PlayerTick} reads to decide which wall is
	 * being pushed into, so a plain mock player can never climb.
	 *
	 * <p>This one has no connection (vanilla's helper builds it without one), so the invisibility
	 * {@code PlayerTick} keeps on a squid would NPE on its way out to the client. Seeding the effect
	 * straight into the active map, well above the running-low threshold, means {@code keep} finds it
	 * healthy and never re-adds it — the climb is what these tests are about.
	 */
	private static ServerPlayer wallSquid(GameTestHelper helper, PaintColor color) {
		ServerPlayer player = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, color));
		player.getActiveEffectsMap().put(MobEffects.INVISIBILITY,
				new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0, true, false, false));
		return player;
	}

	/** Pressing forward, sneaking: the input a climbing squid sends. */
	private static final Input PUSHING = new Input(true, false, false, false, false, true, false);

	/** A mock survival player holding a gun, standing at relative (4, 3, 4), on no team. */
	private static Player gunner(GameTestHelper helper) {
		// A plain mock player, not a mock ServerPlayer: that one has no connection, so vanilla's
		// ServerItemCooldowns throws when the gun starts its cooldown.
		Player player = mockPlayer(helper, GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(4, 3, 4));
		player.setPos(at.x, at.y, at.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.SHOOTER)));
		return player;
	}

	/** Without a team the gun refuses: no projectile, no cooldown. */
	@GameTest
	public void gunWithoutTeamDoesNotShoot(GameTestHelper helper) {
		Player player = gunner(helper);
		InteractionResult result = PaintWeapon.of(Weapon.SHOOTER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.FAIL, "use fails without a team");
		helper.assertTrue(helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0).isEmpty(), "no paint ball spawned");
		helper.assertTrue(!player.getCooldowns().isOnCooldown(player.getItemInHand(InteractionHand.MAIN_HAND)), "no cooldown");
		helper.succeed();
	}

	/** On a team the gun throws one paint ball carrying a firework star in the team colour, and starts the cooldown. */
	@GameTest
	public void gunOnTeamThrowsColouredBall(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		InteractionResult result = PaintWeapon.of(Weapon.SHOOTER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result.consumesAction(), "use succeeds on a team");
		List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0);
		helper.assertValueEqual(balls.size(), 1, "one paint ball");
		PaintBall ball = balls.getFirst();
		helper.assertTrue(ball.color() == PaintColor.DATA, "ball is DATA");
		ItemStack shown = ball.getItem();
		helper.assertTrue(shown.is(Items.FIREWORK_STAR), Component.literal("ball shows a firework star, got " + shown));
		FireworkExplosion explosion = shown.get(DataComponents.FIREWORK_EXPLOSION);
		helper.assertTrue(explosion != null && explosion.colors().contains(PaintColor.DATA.rgb), "star is tinted DATA");
		helper.assertTrue(player.getCooldowns().isOnCooldown(player.getItemInHand(InteractionHand.MAIN_HAND)), "cooldown started");
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		PaintWeapon.of(Weapon.SHOOTER).inventoryTick(held, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		DyedItemColor dye = held.get(DataComponents.DYED_COLOR);
		helper.assertTrue(dye != null && dye.rgb() == PaintColor.DATA.rgb, "the held gun's tank is dyed DATA");
		balls.forEach(Entity::discard);
		helper.succeed();
	}

	/** The gun stack carries the team colour as a dye, nothing without a team, and loses a stale dye. */
	@GameTest
	public void gunTankTakesTeamColour(GameTestHelper helper) {
		ItemStack onTeam = PaintWeapon.withTankColor(new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK), team(helper, PaintColor.DATA));
		DyedItemColor dye = onTeam.get(DataComponents.DYED_COLOR);
		helper.assertTrue(dye != null && dye.rgb() == PaintColor.DATA.rgb, "tank dyed DATA");
		ItemStack noTeam = PaintWeapon.withTankColor(new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK), null);
		helper.assertTrue(noTeam.get(DataComponents.DYED_COLOR) == null, "no dye without a team");
		ItemStack left = PaintWeapon.withTankColor(onTeam, null);
		helper.assertTrue(left.get(DataComponents.DYED_COLOR) == null, "leaving a team strips the dye");
		helper.succeed();
	}

	/**
	 * Every weapon's item definition and model ship in the jar, on the one shared pair of Julle textures,
	 * with Julle's own display transforms kept and their ink faces still on tint index 0 so the team dye
	 * reaches them. And the sprayer is gone: it did what the shooter does, so its files must not linger in
	 * a pack that no longer registers the item.
	 */
	@GameTest
	public void gunModelAssetsArePresent(GameTestHelper helper) throws IOException {
		String base = "/assets/" + Rivals.MOD_ID + "/";
		// One body/ink pair for all four, 128x128 as Julle exported them.
		for (String shared : new String[] {"julle_body", "julle_ink"}) {
			try (InputStream in = Rivals.class.getResourceAsStream(base + "textures/item/" + shared + ".png")) {
				helper.assertTrue(in != null, "shared texture present: " + shared);
				BufferedImage image = ImageIO.read(in);
				helper.assertValueEqual(image.getWidth(), 128, shared + " is 128 px wide");
				helper.assertValueEqual(image.getHeight(), 128, shared + " is 128 px tall");
			}
		}
		for (String gone : new String[] {"items/sprayer.json", "models/item/sprayer.json",
				"textures/item/sprayer_palette.png", "textures/item/paint_gun_palette.png",
				"textures/item/charger_palette.png", "textures/item/slosher_palette.png"}) {
			try (InputStream in = Rivals.class.getResourceAsStream(base + gone)) {
				helper.assertTrue(in == null, "the sprayer and the Kenney palettes are gone: " + gone);
			}
		}
		for (String id : new String[] {"paint_gun", "charger", "slosher", "roller"}) {
			for (String path : new String[] {"items/" + id + ".json", "models/item/" + id + ".json"}) {
				try (InputStream in = Rivals.class.getResourceAsStream(base + path)) {
					helper.assertTrue(in != null, "asset present: " + path);
				}
			}
			try (InputStream in = Rivals.class.getResourceAsStream(base + "models/item/" + id + ".json")) {
				JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				JsonObject textures = model.getAsJsonObject("textures");
				helper.assertValueEqual(textures.get("0").getAsString(), Rivals.MOD_ID + ":item/julle_body", id + ": body texture");
				helper.assertValueEqual(textures.get("1").getAsString(), Rivals.MOD_ID + ":item/julle_ink", id + ": ink texture");
				helper.assertValueEqual(model.getAsJsonArray("texture_size").toString(), "[128,128]", id + ": Julle's atlas size");
				helper.assertValueEqual(model.get("gui_light").getAsString(), "side", id + ": gui_light kept");
				// Julle's display transforms, verbatim: the first person is what the LED's placement is
				// computed from and the third person is what everyone else sees the weapon in.
				JsonObject display = model.getAsJsonObject("display");
				for (String context : new String[] {"gui", "ground", "fixed", "head",
						"thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand"}) {
					helper.assertTrue(display.has(context), id + ": keeps Julle's " + context + " transform");
					for (String part : new String[] {"rotation", "translation", "scale"}) {
						helper.assertValueEqual(display.getAsJsonObject(context).getAsJsonArray(part).size(), 3,
								id + ": " + context + "." + part + " is three numbers");
					}
				}
				JsonArray elements = model.getAsJsonArray("elements");
				helper.assertTrue(elements.size() >= 5, id + ": model has elements");
				helper.assertTrue(elements.size() <= 400, id + ": model stays under 400 elements, got " + elements.size());
				int inkFaces = 0;
				for (JsonElement e : elements) {
					JsonObject box = e.getAsJsonObject();
					for (String key : new String[] {"from", "to"}) {
						for (JsonElement v : box.getAsJsonArray(key)) {
							double d = v.getAsDouble();
							helper.assertTrue(d >= -16 && d <= 32, id + ": element coordinate in range: " + d);
						}
					}
					for (var face : box.getAsJsonObject("faces").entrySet()) {
						JsonObject json = face.getValue().getAsJsonObject();
						if (!json.has("tintindex")) continue;
						// Tint 0 is the team dye on an ink face; tint 1 is the LED's own, checked by the ink test.
						if (json.get("tintindex").getAsInt() != 0) continue;
						helper.assertValueEqual(json.get("texture").getAsString(), "#1",
								id + ": the dyed faces are the ink texture's");
						inkFaces++;
					}
				}
				helper.assertTrue(inkFaces > 0, id + ": some faces take the team dye, got " + inkFaces);
			}
			try (InputStream in = Rivals.class.getResourceAsStream(base + "items/" + id + ".json")) {
				JsonObject definition = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				// One model, or a condition with one on each side: the roller swaps to its rolling pose
				// while the button is held. Every branch has to be this weapon's own model and take the
				// team dye, or holding the button would change the weapon rather than the pose.
				boolean own = false;
				for (JsonObject branch : modelBranches(definition.getAsJsonObject("model"))) {
					String points = branch.get("model").getAsString();
					helper.assertTrue(points.startsWith(Rivals.MOD_ID + ":item/" + id),
							id + ": definition points at the model, got " + points);
					own |= points.equals(Rivals.MOD_ID + ":item/" + id);
					helper.assertValueEqual(branch.getAsJsonArray("tints").get(0).getAsJsonObject().get("type").getAsString(),
							"minecraft:dye", id + ": dye tint");
				}
				helper.assertTrue(own, id + ": and one of them is the plain model");
			}
		}
		helper.succeed();
	}

	/**
	 * Pirkko, the figure a squid wears for everyone else: Julle's mascot, imported by
	 * {@code tools/pirkko_model.py} from {@code tools/julle/pirkko_models/}. The import is a copy and a
	 * rename, so what there is to check is that all three files shipped and that not one {@code splat:}
	 * id survived the rename — a texture id left pointing at the delivery's namespace is a figure that
	 * renders as the missing-texture checker on every client, and nothing server-side would ever say so.
	 *
	 * <p>The blob stays too: the thrown ball still wears it, and importing Pirkko must not take it away.
	 */
	@GameTest
	public void pirkkoModelAssetsArePresent(GameTestHelper helper) throws IOException {
		String base = "/assets/" + Rivals.MOD_ID + "/";
		for (String path : new String[] {"items/pirkko.json", "models/item/pirkko.json",
				"textures/item/pirkko_ink.png", "items/blob.json", "models/item/blob.json"}) {
			try (InputStream in = Rivals.class.getResourceAsStream(base + path)) {
				helper.assertTrue(in != null, "asset present: " + path);
			}
		}
		try (InputStream in = Rivals.class.getResourceAsStream(base + "textures/item/pirkko_ink.png")) {
			BufferedImage image = ImageIO.read(in);
			helper.assertValueEqual(image.getWidth(), 128, "Pirkko's sheet is 128 px wide");
			helper.assertValueEqual(image.getHeight(), 128, "and 128 px tall");
		}
		// The item definition: our model, and the dye tint that carries the team colour.
		try (InputStream in = Rivals.class.getResourceAsStream(base + "items/pirkko.json")) {
			JsonObject definition = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject model = definition.getAsJsonObject("model");
			helper.assertValueEqual(model.get("model").getAsString(), Rivals.MOD_ID + ":item/pirkko",
					"the definition points at our model");
			helper.assertValueEqual(model.getAsJsonArray("tints").get(0).getAsJsonObject().get("type").getAsString(),
					"minecraft:dye", "and takes the dye tint the team colour is sent as");
		}
		try (InputStream in = Rivals.class.getResourceAsStream(base + "models/item/pirkko.json")) {
			JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject textures = model.getAsJsonObject("textures");
			helper.assertTrue(!textures.isEmpty(), "the model names its textures");
			for (var texture : textures.entrySet()) {
				String value = texture.getValue().getAsString();
				helper.assertTrue(value.startsWith(Rivals.MOD_ID + ":"),
						"every texture id is ours, but " + texture.getKey() + " is " + value);
				try (InputStream sheet = Rivals.class.getResourceAsStream(
						base + "textures/" + value.substring(value.indexOf(':') + 1) + ".png")) {
					helper.assertTrue(sheet != null, "and the file it names ships: " + value);
				}
			}
			helper.assertValueEqual(model.getAsJsonArray("texture_size").toString(), "[128,128]", "Julle's atlas size");
			// Julle's geometry, verbatim: twelve cubes lying flat, face up, head towards -Z, the underside
			// exactly at y=0 and four model units tall, 24 long and 22 wide. Those are the numbers the
			// floor figure's lift and scale are worked out from, so a redelivery that stood Pirkko up or
			// lifted her off the floor of her own model space has to fail here rather than in a screenshot.
			JsonArray elements = model.getAsJsonArray("elements");
			helper.assertValueEqual(elements.size(), 12, "twelve cubes");
			double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
			double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
			for (JsonElement e : elements) {
				JsonObject box = e.getAsJsonObject();
				for (int axis = 0; axis < 3; axis++) {
					low[axis] = Math.min(low[axis], box.getAsJsonArray("from").get(axis).getAsDouble());
					high[axis] = Math.max(high[axis], box.getAsJsonArray("to").get(axis).getAsDouble());
				}
				for (var face : box.getAsJsonObject("faces").entrySet()) {
					helper.assertValueEqual(face.getValue().getAsJsonObject().get("tintindex").getAsInt(), 0,
							"every face takes the team dye: " + box.get("name").getAsString() + "." + face.getKey());
				}
			}
			helper.assertValueEqual(low[1], 0.0, "the underside is exactly y=0 in model units");
			helper.assertValueEqual(high[1] - low[1], 4.0, "four model units tall");
			helper.assertValueEqual(high[2] - low[2], 24.0, "24 long along the z axis her head points down");
			helper.assertValueEqual(high[0] - low[0], 22.0, "22 wide across, hands included");
			// And centred across both of those, which is what lets the display turn her about her own middle.
			helper.assertValueEqual(low[0] + high[0], 16.0, "centred across x in item space");
			helper.assertValueEqual(low[2] + high[2], 16.0, "and centred along z");
		}
		helper.succeed();
	}

	/** A thrown ball paints the cell it lands in, on the struck face, and spends its bounce doing it. */
	@GameTest
	public void paintBallPaintsWhereItLands(GameTestHelper helper) {
		stoneFloor(helper, 5);
		PaintBall ball = new PaintBall(helper.getLevel(), gunner(helper), PaintColor.DATA);
		Vec3 from = helper.absoluteVec(new Vec3(2.5, 4, 2.5));
		ball.setPos(from.x, from.y, from.z);
		ball.setDeltaMovement(0, -0.6, 0); // straight down onto the floor block at relative (2, 1, 2)
		helper.getLevel().addFreshEntity(ball);
		helper.runAfterDelay(10, () -> {
			BlockPos cell = new BlockPos(2, 2, 2);
			BlockState state = helper.getBlockState(cell);
			helper.assertTrue(isPaint(state, PaintColor.DATA),
					Component.literal("the cell where the ball landed should be DATA paint, got " + state));
			helper.assertTrue(hasFace(state, PaintColor.DATA, Direction.DOWN), "paint sits on its down face");
			// A default ball carries the shooter's bounces, so ten ticks on it is still in the air on its
			// way back up from the floor it just painted (along with the droplets that bounce threw off);
			// what the hit must have spent is one of those bounces.
			List<PaintBall> left = helper.getEntities(PaintBall.TYPE, cell, 4.0);
			helper.assertTrue(left.stream().allMatch(other -> other.isDroplet() || other.bouncesLeft() < Weapon.SHOOTER_BOUNCES),
					"the ball is gone after the hit, or has spent a bounce");
			// A bouncing ball outlives the test it was thrown in; left alone it would sail on and paint
			// into whatever test structure sits next door.
			left.forEach(Entity::discard);
			helper.succeed();
		});
	}

	/**
	 * A ball that hits a teammate paints the floor under them and leaves their health alone.
	 *
	 * <p>Two halves, and they are deliberately not in the same tick. Friendly fire is checked
	 * <em>synchronously</em>, against a direct {@code onHitEntity}, because a health assertion ten ticks
	 * out is an assertion about whatever else has happened to this player since. The flight is then
	 * checked for what only a flight can show: that a ball thrown at a player lands on them and paints
	 * the cell under their feet. The synchronous half paints that cell too, so it is wiped first and
	 * asserted empty; the flight has to put the paint there itself.
	 */
	@GameTest
	public void paintBallOnEntityPaintsUnderneathWithoutDamage(GameTestHelper helper) {
		stoneFloor(helper, 5);
		Player shooter = gunner(helper); // a different mock player: a projectile never hits its own owner
		Player target = mockPlayer(helper, GameType.SURVIVAL);
		Vec3 stand = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
		target.setPos(stand.x, stand.y, stand.z);
		// The ball's own colour is what decides friendly fire, so this team is the whole setup.
		helper.getLevel().getScoreboard().addPlayerToTeam(target.getScoreboardName(), team(helper, PaintColor.DATA));
		// The projectile's entity sweep only sees entities the level knows about.
		helper.assertTrue(helper.getLevel().addFreshEntity(target), "the target player joined the level");
		target.setHealth(target.getMaxHealth());
		target.damageCooldownTime = 0;
		float health = target.getHealth();
		helper.assertFalse(PaintBall.hostile(PaintColor.DATA, target), "a teammate is not a target");
		PaintBall direct = new PaintBall(helper.getLevel(), shooter, PaintColor.DATA);
		direct.setPos(stand.x, stand.y + 1.0, stand.z);
		helper.assertTrue(direct.damage() > 0, "the ball would hurt someone, damage " + direct.damage());
		direct.onHitEntity(new EntityHitResult(target));
		helper.assertValueEqual(target.getHealth(), health, "a teammate takes no damage from a direct hit");
		direct.discard();
		// Wipe what the direct hit painted, so the flight below has to paint the cell from scratch.
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
		helper.assertFalse(isPaint(helper.getBlockState(new BlockPos(2, 2, 2)), PaintColor.DATA), "the cell starts empty");
		PaintBall ball = new PaintBall(helper.getLevel(), shooter, PaintColor.DATA);
		Vec3 from = helper.absoluteVec(new Vec3(2.5, 2.6, 0.5));
		ball.setPos(from.x, from.y, from.z);
		// Flat and fast at the player's chest, not dropped on their head: a ball that missed would
		// sail off the far edge of the floor instead of splatting on the cell asserted below, so this
		// can only pass through onHitEntity.
		Direction along = helper.getAbsoluteDirection(Direction.SOUTH);
		ball.setDeltaMovement(along.getStepX() * 1.2, 0, along.getStepZ() * 1.2);
		helper.getLevel().addFreshEntity(ball);
		helper.runAfterDelay(10, () -> {
			BlockState state = helper.getBlockState(new BlockPos(2, 2, 2));
			helper.assertTrue(isPaint(state, PaintColor.DATA),
					Component.literal("the floor under the player should be DATA paint, got " + state));
			helper.assertTrue(hasFace(state, PaintColor.DATA, Direction.DOWN), "paint sits on its down face");
			target.discard();
			helper.getLevel().getScoreboard().removePlayerFromTeam(target.getScoreboardName());
			helper.succeed();
		});
	}

	/** A splash on the floor beside a wall paints the wall's face too (the rays), not only the floor. */
	/**
	 * A ball that lands on someone from the other team takes hearts off them, and still paints the floor
	 * under their feet. {@code onHitEntity} is called directly: putting a ball in flight and waiting for
	 * it to arrive is a different test's job.
	 */
	@GameTest
	public void directHitHurtsTheOtherTeam(GameTestHelper helper) {
		stoneFloor(helper, 5);
		Player target = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(target.getScoreboardName(), team(helper, PaintColor.IT));
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		target.setPos(at.x, at.y, at.z);
		target.setHealth(target.getMaxHealth());
		target.damageCooldownTime = 0;
		float before = target.getHealth();
		PaintBall ball = new PaintBall(helper.getLevel(), null, PaintColor.DATA, 0, 0);
		ball.setPos(at.x, at.y + 1.0, at.z);
		helper.assertValueEqual(ball.damage(), Weapon.SHOOTER.damage, "a ball carries the shooter's damage by default");
		ball.onHitEntity(new EntityHitResult(target));
		helper.assertValueEqual(target.getHealth(), before - Weapon.SHOOTER.damage, "the hit took the shooter's damage off");
		helper.assertTrue(isPaint(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(2, 2, 2))), PaintColor.DATA),
				"and the floor under them is still painted");
		// No team at all is fair game: an arena full of untagged mobs must not be cover.
		helper.getLevel().getScoreboard().removePlayerFromTeam(target.getScoreboardName());
		helper.assertTrue(PaintBall.hostile(PaintColor.DATA, target), "someone on no team can still be shot");
		helper.succeed();
	}

	/** The same ball against one of your own: paint under their feet, not a scratch on them. */
	@GameTest
	public void directHitSparesTheOwnTeam(GameTestHelper helper) {
		stoneFloor(helper, 5);
		Player target = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(target.getScoreboardName(), team(helper, PaintColor.DATA));
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		target.setPos(at.x, at.y, at.z);
		target.setHealth(target.getMaxHealth());
		target.damageCooldownTime = 0;
		float before = target.getHealth();
		PaintBall ball = new PaintBall(helper.getLevel(), null, PaintColor.DATA, 0, 0);
		ball.setPos(at.x, at.y + 1.0, at.z);
		ball.onHitEntity(new EntityHitResult(target));
		helper.assertValueEqual(target.getHealth(), before, "a teammate takes no damage");
		helper.assertFalse(PaintBall.hostile(PaintColor.DATA, target), "and is not a target at all");
		helper.assertTrue(PaintBall.hostile(PaintColor.IT, target), "though the other colour may shoot them");
		helper.assertTrue(isPaint(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(2, 2, 2))), PaintColor.DATA),
				"the paint lands on a teammate all the same");
		helper.getLevel().getScoreboard().removePlayerFromTeam(target.getScoreboardName());
		helper.succeed();
	}

	@GameTest
	public void splashPaintsAdjacentWall(GameTestHelper helper) {
		stoneFloor(helper, 5);
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(4, y, 2), Blocks.STONE); // wall east of the hit
		BlockPos struck = new BlockPos(3, 1, 2);
		Vec3 impact = helper.absoluteVec(new Vec3(3.6, 2.0, 2.5));
		int changed = Painter.splash(helper.getLevel(), impact, helper.absolutePos(struck), Direction.UP, PaintColor.DATA,
				helper.getLevel().getRandom(), null);
		helper.assertTrue(changed >= 5, "blob plus rays painted at least five cells, got " + changed);
		BlockState floorCell = helper.getBlockState(new BlockPos(3, 2, 2));
		helper.assertTrue(hasFace(floorCell, PaintColor.DATA, Direction.DOWN), "floor cell painted");
		BlockState wallCell = helper.getBlockState(new BlockPos(3, 2, 2)); // same cell holds the wall's west face
		helper.assertTrue(hasFace(wallCell, PaintColor.DATA, Direction.EAST),
				Component.literal("the wall face east of the hit is painted, got " + wallCell));
		helper.succeed();
	}

	/** Setup creates one vanilla team per colour with the matching colour, no friendly fire, no collisions. */
	/** Every ovvar ovve id belongs to a chapter; nothing else in or out of the namespace does. */
	@GameTest
	public void ovveIdsMapToTeams(GameTestHelper helper) {
		for (String id : new String[] {"data_ovve", "data_ovve_top", "data_polymiter_ovve"}) {
			helper.assertValueEqual(OvveTeams.colourOf(Identifier.fromNamespaceAndPath("ovvar", id)),
					Optional.of(PaintColor.DATA), id + " is a DATA ovve");
		}
		for (String id : new String[] {"it_ovve", "it_kisel_ovve", "it_polymiter_ovve"}) {
			helper.assertValueEqual(OvveTeams.colourOf(Identifier.fromNamespaceAndPath("ovvar", id)),
					Optional.of(PaintColor.IT), id + " is an IT ovve");
		}
		helper.assertValueEqual(OvveTeams.colourOf(Identifier.fromNamespaceAndPath("ovvar", "media_frack")),
				Optional.empty(), "the media frack belongs to no chapter");
		helper.assertValueEqual(OvveTeams.colourOf(Identifier.fromNamespaceAndPath("minecraft", "leather_leggings")),
				Optional.empty(), "and nothing outside the namespace is an ovve");
		// The stack path over a real registered item, since no ovvar item is on the test classpath.
		helper.assertValueEqual(OvveTeams.colourOf(new ItemStack(Items.LEATHER_LEGGINGS)), Optional.empty(), "vanilla trousers are not an ovve");
		helper.assertValueEqual(OvveTeams.colourOf(ItemStack.EMPTY), Optional.empty(), "bare legs are not an ovve");
		helper.succeed();
	}

	/**
	 * A player wearing no ovve keeps the team they were put on by hand — the check must never strip
	 * someone for changing trousers. (The joining half needs an ovvar item, which is not on the test
	 * classpath; {@link OvveTeams#colourOf} is what decides it and is covered above.)
	 */
	@GameTest
	public void wornOvveJoinsTheTeam(GameTestHelper helper) {
		Player player = gunner(helper);
		ServerScoreboard board = helper.getLevel().getScoreboard();
		board.addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.IT));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
		helper.assertValueEqual(OvveTeams.worn(player), Optional.empty(), "leather trousers dress you as nobody");
		PlayerTick.tick(player, 20); // an ovve tick
		helper.assertValueEqual(PaintColor.byTeam(player.getTeam()), Optional.of(PaintColor.IT), "the team they were put on by hand stands");
		board.removePlayerFromTeam(player.getScoreboardName());
		helper.succeed();
	}

	/**
	 * {@code /rivals setup} makes one team per side, under the name the config gives that side, and does
	 * not touch one that already exists beyond the two rules a match needs: an existing team may well be
	 * the server's own, pointed at a side by the config, and renaming or recolouring it would be rude.
	 *
	 * <p>The sides are pointed at names of this test's own rather than at the default {@code data} and
	 * {@code it}. Those two teams are where every other test's mock player stands, and deleting a scoreboard
	 * team deletes its membership — which no {@code finally} can put back. Everything here is synchronous
	 * and the names go back afterwards, the same discipline {@link #withTuning} keeps.
	 */
	@GameTest
	public void setupCreatesTeamsUnderTheConfiguredNames(GameTestHelper helper) {
		ServerScoreboard board = helper.getLevel().getScoreboard();
		String dataName = "rivals-setup-test-data";
		String itName = "rivals-setup-test-it";
		String theirName = "rivals-setup-test-house";
		try {
			helper.assertTrue(TeamNames.set(PaintColor.DATA, dataName) && TeamNames.set(PaintColor.IT, itName),
					"both sides point at a team of this test's own");
			List<String> made = RivalsCommands.setupTeams(helper.getLevel().getServer());
			helper.assertValueEqual(made, List.of(dataName, itName), "both teams were made, under those names");
			for (PaintColor color : PaintColor.values()) {
				PlayerTeam team = board.getPlayerTeam(TeamNames.nameOf(color));
				helper.assertTrue(team != null, "team exists: " + TeamNames.nameOf(color));
				helper.assertTrue(team.getColor().equals(Optional.of(color.teamColor)), "team colour: " + color.id);
				helper.assertTrue(!team.isAllowFriendlyFire(), "friendly fire off: " + color.id);
				helper.assertTrue(team.getCollisionRule() == Team.CollisionRule.NEVER, "no collisions: " + color.id);
				helper.assertValueEqual(PaintColor.byTeam(team).orElse(null), color, "and it is " + color + "'s team");
			}
			// Run again: nothing to make, so nothing is renamed or recoloured either.
			helper.assertTrue(RivalsCommands.setupTeams(helper.getLevel().getServer()).isEmpty(),
					"a second setup makes nothing");
			// A team that was already there keeps its own display name and colour, but gets the match's rules.
			PlayerTeam theirs = board.addPlayerTeam(theirName);
			theirs.setDisplayName(Component.literal("House Blue"));
			theirs.setColor(Optional.of(TeamColor.AQUA));
			theirs.setAllowFriendlyFire(true);
			helper.assertTrue(TeamNames.set(PaintColor.IT, theirName), "IT points at their team instead");
			helper.assertValueEqual(RivalsCommands.setupTeams(helper.getLevel().getServer()), List.of(),
					"nothing to make: both teams exist");
			helper.assertTrue(theirs.getDisplayName().getString().equals("House Blue"), "their name stands");
			helper.assertTrue(theirs.getColor().equals(Optional.of(TeamColor.AQUA)), "and their colour");
			helper.assertTrue(!theirs.isAllowFriendlyFire(), "but friendly fire is off, which the match needs");
		} finally {
			TeamNames.resetAll();
			for (String name : List.of(dataName, itName, theirName)) {
				PlayerTeam made = board.getPlayerTeam(name);
				if (made != null) board.removePlayerTeam(made);
			}
		}
		helper.succeed();
	}

	/**
	 * Which scoreboard team each side is comes out of {@code config/rivals-paint/teams.json}, so a
	 * server that already runs teams of its own can point a side at one instead of keeping a second pair.
	 * {@link PaintColor#byTeam} — the one question anything in this mod asks about a player's side —
	 * follows the configured names, and a team called by a side's old id is then nobody's.
	 *
	 * <p>Set through the config object rather than through the file: the file is read once per server, and
	 * a test that wrote one would be racing every other test in the batch. Synchronous, with the names put
	 * back afterwards, for the same reason {@link #withTuning} is.
	 */
	@GameTest
	public void teamNamesFollowTheConfig(GameTestHelper helper) {
		ServerScoreboard board = helper.getLevel().getScoreboard();
		String renamedName = "rivals-names-test-red";
		String strangerName = "rivals-names-test-stranger";
		PlayerTeam renamed = board.addPlayerTeam(renamedName);
		PlayerTeam stranger = board.addPlayerTeam(strangerName);
		try {
			helper.assertValueEqual(TeamNames.nameOf(PaintColor.DATA), "main.data", "the default name is MAIN's team for the side");
			helper.assertValueEqual(TeamNames.slotOf("main.it").orElse(null), PaintColor.IT, "and it reads back");
			helper.assertTrue(TeamNames.slotOf(strangerName).isEmpty(), "a team of nobody's is nobody's");
			helper.assertTrue(TeamNames.renamed().isEmpty(), "nothing is off its default");

			helper.assertTrue(TeamNames.set(PaintColor.DATA, renamedName), "DATA is pointed elsewhere");
			helper.assertValueEqual(TeamNames.nameOf(PaintColor.DATA), renamedName, "which is the name it now uses");
			helper.assertValueEqual(TeamNames.slotOf(renamedName).orElse(null), PaintColor.DATA, "and it resolves");
			helper.assertTrue(TeamNames.slotOf("main.data").isEmpty(), "while the old name is nobody's");
			helper.assertValueEqual(TeamNames.renamed(), List.of(PaintColor.DATA), "one side is off its default");
			helper.assertValueEqual(TeamNames.nameList(), renamedName + ", main.it", "listed as " + TeamNames.nameList());
			// Two sides may not share a name: a team cannot be both, and the lookup would have to guess.
			helper.assertFalse(TeamNames.set(PaintColor.IT, renamedName), "IT cannot take DATA's team");
			helper.assertValueEqual(TeamNames.nameOf(PaintColor.IT), "main.it", "so IT keeps its own");
			// And a real team under the configured name is DATA, while any other team is nobody's.
			helper.assertValueEqual(PaintColor.byTeam(renamed).orElse(null), PaintColor.DATA,
					"a team under the configured name is DATA");
			helper.assertTrue(PaintColor.byTeam(stranger).isEmpty(), "and one under any other name is nobody's");
			helper.assertTrue(PaintColor.byTeam(null).isEmpty(), "as is no team at all");
		} finally {
			TeamNames.resetAll();
			board.removePlayerTeam(renamed);
			board.removePlayerTeam(stranger);
		}
		helper.succeed();
	}

	/** Recoil on a mock player (no connection) sends nothing and leaves nothing queued; a shot still succeeds. */
	@GameTest
	public void recoilIsSafeWithoutConnection(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		int before = Recoil.pending();
		InteractionResult result = PaintWeapon.of(Weapon.SHOOTER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result.consumesAction(), "shot succeeds");
		helper.assertValueEqual(Recoil.pending(), before, "no settle queued for a connectionless player");
		helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0).forEach(Entity::discard);
		helper.succeed();
	}

	/**
	 * A stair top takes paint as display quads: tracked, counted in the colour, dropped when the surface
	 * goes (waterlogged stairs included), removed by reset. Holder counts are deltas against a snapshot
	 * taken first, since {@link PaintDisplays} is per level and other tests in the same level hold quads
	 * of their own; the two figures that are absolute are the post-conditions of a level-wide clear,
	 * which is what {@code reset} is.
	 */
	@GameTest
	public void stairTakesDisplayPaint(GameTestHelper helper) {
		BlockPos stair = new BlockPos(2, 1, 2);
		helper.setBlock(stair, Blocks.STONE_STAIRS.defaultBlockState());
		PaintDisplays displays = PaintDisplays.of(helper.getLevel());
		int before = displays.holders();
		boolean painted = Painter.paintFace(helper.getLevel(), helper.absolutePos(stair), Direction.UP, PaintColor.DATA);
		helper.assertTrue(painted, "stair top accepted paint");
		helper.assertTrue(helper.getBlockState(stair.above()).isAir(), "no paint block above a stair (quads instead)");
		helper.assertValueEqual(displays.holders(), before + 1, "one holder for the cell");
		helper.assertTrue(displays.colorAt(helper.absolutePos(stair.above())) == PaintColor.DATA, "cell is DATA");
		// The quads are block displays of the paint's own client state, one per outline box on the face.
		List<BlockState> quads = displays.statesAt(helper.absolutePos(stair.above()));
		helper.assertTrue(!quads.isEmpty() && quads.size() <= 3, "one to three quads, got " + quads.size());
		for (BlockState quad : quads) {
			helper.assertValueEqual(quad, PaintStates.connected(PaintColor.DATA, Direction.DOWN, 0), "the DATA floor state");
		}
		helper.assertTrue(displays.count(helper.getLevel()).get(PaintColor.DATA) >= 1, "counted as DATA faces");
		boolean recoloured = Painter.paintFace(helper.getLevel(), helper.absolutePos(stair), Direction.UP, PaintColor.IT);
		helper.assertTrue(recoloured && displays.colorAt(helper.absolutePos(stair.above())) == PaintColor.IT, "recoloured to IT");
		helper.assertValueEqual(displays.holders(), before + 1, "recolour reuses the cell");
		BlockPos wet = new BlockPos(5, 1, 5);
		helper.setBlock(wet, Blocks.STONE_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(wet), Direction.UP, PaintColor.DATA),
				"a waterlogged stair still takes paint");
		// The quads die with their surface. A chunk unload does the same thing by another route — Polymer
		// destroys the holder's attachment — but a game test cannot unload its own chunks, so this half of
		// the rule stands in for both.
		helper.setBlock(stair, Blocks.AIR.defaultBlockState());
		displays.count(helper.getLevel()); // the sweep that prunes cells whose paint is gone
		helper.assertTrue(displays.colorAt(helper.absolutePos(stair.above())) == null, "the broken stair took its cell with it");
		helper.assertValueEqual(displays.holders(), before + 1, "only the waterlogged stair's holder is left");
		helper.setBlock(stair, Blocks.STONE_STAIRS.defaultBlockState());
		helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(stair), Direction.UP, PaintColor.IT),
				"a rebuilt stair takes the same colour again");
		PaintTally tally = new PaintTally();
		helper.assertTrue(tally.count(helper.getLevel()).get(PaintColor.IT) >= 1, "the tally counts the quads as IT faces");
		int removed = tally.reset(helper.getLevel()); // a reset clears the level's display quads too
		helper.assertTrue(removed >= 1 && displays.holders() == 0, "clear removed the quads");
		helper.assertValueEqual(tally.count(helper.getLevel()).get(PaintColor.IT), 0, "nothing left to count");
		// A surface that only changes shape keeps its position, so every check above still passes, but the
		// quads were cut to the old shape and now hang over nothing: the cell must be dropped.
		BlockPos turned = new BlockPos(6, 1, 6);
		helper.setBlock(turned, Blocks.STONE_STAIRS.defaultBlockState()); // default facing is north
		helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(turned), Direction.UP, PaintColor.DATA),
				"the stair took paint");
		helper.assertTrue(displays.colorAt(helper.absolutePos(turned.above())) == PaintColor.DATA, "turned stair's cell is DATA");
		helper.setBlock(turned, Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		displays.count(helper.getLevel()); // the sweep
		helper.assertTrue(displays.colorAt(helper.absolutePos(turned.above())) == null,
				"turning the stair under the quads dropped the cell");
		helper.assertValueEqual(displays.holders(), 0, "no holders left");
		helper.succeed();
	}

	/**
	 * A display quad is the paint state itself: a block display showing
	 * {@link PaintStates#connected} for the cell's colour, its attach direction (the opposite of the face
	 * it was painted on — floor paint on a slab top attaches DOWN, exactly as a chunk cell does) and its
	 * connection bits. Every quad in a cell shows the same state, and the state is one of the table's own,
	 * so the pack already has a model and a texture for it and the item shader's gloss finds the marker
	 * alpha in it.
	 */
	@GameTest
	public void displayQuadsWearRealPaintStates(GameTestHelper helper) {
		BlockPos slab = new BlockPos(2, 1, 2);
		helper.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState());
		PaintDisplays displays = PaintDisplays.of(helper.getLevel());
		BlockPos cell = helper.absolutePos(slab.above());
		helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(slab), Direction.UP, PaintColor.DATA),
				"slab top accepted paint");
		List<BlockState> states = displays.statesAt(cell);
		helper.assertTrue(!states.isEmpty(), "the cell holds quads");
		helper.assertValueEqual(displays.bitsAt(cell), 0, "an isolated quad has no connections");
		for (BlockState state : states) {
			helper.assertTrue(PaintStates.all().contains(state), "a real client paint state: " + state);
			PaintStates.Entry entry = PaintStates.entry(state);
			helper.assertTrue(entry.color() == PaintColor.DATA, "DATA, got " + entry.color());
			helper.assertTrue(entry.face() == Direction.DOWN, "attaches DOWN under the paint's own face, got " + entry.face());
			helper.assertValueEqual(entry.bits(), 0, "no bits");
			helper.assertValueEqual(state, PaintStates.connected(PaintColor.DATA, Direction.DOWN, 0), "the table's state for it");
		}
		helper.succeed();
	}

	/**
	 * Quads border like blocks. Two slab tops side by side each gain the bit pointing at the other, and a
	 * paint block painted into the cell beside a quad opens that quad's border towards it, and the block's
	 * own border opens back — the quads are not blocks, so nothing tells either side about the other but
	 * {@link Painter} and {@link PaintDisplays#refreshAround}.
	 */
	@GameTest
	public void displayQuadsConnectToNeighbours(GameTestHelper helper) {
		PaintDisplays displays = PaintDisplays.of(helper.getLevel());
		BlockPos west = new BlockPos(2, 1, 4), east = new BlockPos(3, 1, 4);
		helper.setBlock(west, Blocks.STONE_SLAB.defaultBlockState());
		helper.setBlock(east, Blocks.STONE_SLAB.defaultBlockState());
		helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(west), Direction.UP, PaintColor.DATA), "west slab painted");
		helper.assertValueEqual(displays.bitsAt(helper.absolutePos(west.above())), 0, "alone so far");
		helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(east), Direction.UP, PaintColor.DATA), "east slab painted");
		// inPlane for a DOWN attach is {WEST, EAST, NORTH, SOUTH}: bit 1 is +u (east), bit 0 is -u (west).
		helper.assertValueEqual(displays.bitsAt(helper.absolutePos(west.above())), 2, "the west quad reaches east");
		helper.assertValueEqual(displays.bitsAt(helper.absolutePos(east.above())), 1, "the east quad reaches west");
		for (BlockState state : displays.statesAt(helper.absolutePos(east.above()))) {
			helper.assertValueEqual(state, PaintStates.connected(PaintColor.DATA, Direction.DOWN, 1), "and shows the bordered state");
		}
		// A full block north of the east slab, painted on top: its paint block lands in the cell in-plane
		// with the quad, which is bit 2 (-v, north) for a DOWN attach.
		BlockPos north = new BlockPos(3, 1, 3);
		helper.setBlock(north, Blocks.STONE);
		helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(north), Direction.UP, PaintColor.DATA), "floor block painted");
		helper.assertTrue(Painter.isPaint(helper.getBlockState(north.above())), "a paint block, not quads");
		helper.assertValueEqual(displays.bitsAt(helper.absolutePos(east.above())), 1 | 4, "the quad borders the paint block too");
		// And the block borders the quad back: bit 3 is +v (south) for a DOWN attach, which is where the
		// quad cell is from the paint block's point of view. No seam either way round.
		helper.assertValueEqual(ConnectedPaintBlock.bits(helper.getBlockState(north.above())) & 8, 8,
				"and the paint block opens its own edge towards the quad");
		helper.succeed();
	}

	/**
	 * A whole field of quads, which is what a dirt-path arena is: a path top is fifteen sixteenths high, so
	 * no cell of it is ever a paint block and every seam in the field is quad to quad. It has to border
	 * exactly as chunk paint does — the middle of a 3×3 opens all four of its sides, an edge cell three of
	 * them — and the state carrying those bits has to <em>reach</em> the client. A display element only
	 * sends its tracker changes when its holder ticks, and a holder attached with {@code ChunkAttachment.of}
	 * never ticks, so before the refresh flushed the holder itself every cell of a painted field kept on
	 * screen the closed border it was born with, however many neighbours arrived afterwards.
	 * {@link PaintDisplays#dirtyAt} is that half of the rule: nothing left unsent.
	 */
	@GameTest
	public void pathQuadsConnectAcrossAField(GameTestHelper helper) {
		PaintDisplays displays = PaintDisplays.of(helper.getLevel());
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT_PATH);
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				BlockPos path = new BlockPos(x, 1, z);
				helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(path), Direction.UP, PaintColor.DATA),
						"path top " + x + "," + z + " took paint");
				helper.assertTrue(helper.getBlockState(path.above()).isAir(),
						"a path top is not a full face: its paint is quads, not a block, at " + x + "," + z);
				helper.assertTrue(displays.colorAt(helper.absolutePos(path.above())) == PaintColor.DATA,
						"the cell above holds DATA quads at " + x + "," + z);
			}
		}
		// inPlane for a DOWN attach is {WEST, EAST, NORTH, SOUTH}: bit 0 -x, bit 1 +x, bit 2 -z, bit 3 +z.
		BlockPos middle = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.assertValueEqual(displays.bitsAt(middle), 15, "the middle of the field opens all four borders");
		BlockPos northEdge = helper.absolutePos(new BlockPos(2, 2, 1));
		helper.assertValueEqual(displays.bitsAt(northEdge), 1 | 2 | 8, "the north edge opens west, east and south");
		BlockPos corner = helper.absolutePos(new BlockPos(1, 2, 1));
		helper.assertValueEqual(displays.bitsAt(corner), 2 | 8, "the north-west corner opens east and south only");
		// Every quad in a cell wears the cell's nibble, and none of it is still sitting on the server.
		for (BlockState state : displays.statesAt(middle)) {
			helper.assertValueEqual(state, PaintStates.connected(PaintColor.DATA, Direction.DOWN, 15),
					"the middle's quads show the all-connected state");
		}
		// Every cell but the last one painted gained a neighbour after its quads were built, so every one of
		// them was re-bordered and every one of those changes has to have gone out. The last cell, 3,3, is
		// left out on purpose: its bits were known before its holder was attached, so its spawn packet
		// carried them and its synched data was never packed — dirty there means "never sent", not "stale".
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 3 && z == 3) continue;
				helper.assertTrue(!displays.dirtyAt(helper.absolutePos(new BlockPos(x, 2, z))),
						"the re-bordered state went out to the watchers at " + x + "," + z);
			}
		}
		// Mixed: a full block beside the field takes a paint block, and the seam opens from both sides.
		BlockPos grass = new BlockPos(4, 1, 2);
		helper.setBlock(grass, Blocks.GRASS_BLOCK);
		helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(grass), Direction.UP, PaintColor.DATA),
				"the grass top took paint");
		helper.assertTrue(Painter.isPaint(helper.getBlockState(grass.above())), "a full face takes a paint block");
		BlockPos eastEdge = helper.absolutePos(new BlockPos(3, 2, 2));
		helper.assertValueEqual(displays.bitsAt(eastEdge), 15, "the east edge quad now opens east towards the block");
		helper.assertValueEqual(ConnectedPaintBlock.bits(helper.getBlockState(grass.above())), 1,
				"and the paint block opens west towards the quad");
		helper.assertTrue(!displays.dirtyAt(eastEdge), "that change went out too");
		helper.succeed();
	}

	/** The gloss lives in the terrain shader pair (what actually draws chunks in 26.3), keyed on the paint alpha marker. */
	@GameTest
	public void glossShaderCarriesTheMarkerGuard(GameTestHelper helper) {
		String fsh = new String(RivalsPack.shader("terrain.fsh"), StandardCharsets.UTF_8);
		String vsh = new String(RivalsPack.shader("terrain.vsh"), StandardCharsets.UTF_8);
		helper.assertTrue(fsh.contains("RIVALS_GLOSS") && fsh.contains("0.9216") && fsh.contains("0.008"), "fragment shader guards on the marker alpha");
		helper.assertTrue(fsh.contains("sampleRGSS") && fsh.contains("#ifdef ALPHA_CUTOUT"), "vanilla terrain sampling and cutout kept");
		helper.assertTrue(vsh.contains("out vec3 viewPos") && vsh.contains("ChunkPosition"), "vertex shader exports the view position from the chunk-relative position");
		// The in-plane cell coordinate the border is cut from: the pair has to agree or the paint is untextured.
		helper.assertTrue(vsh.contains("out vec3 chunkPos"), "vertex shader exports the chunk-relative position");
		helper.assertTrue(fsh.contains("in vec3 chunkPos"), "fragment shader reads the chunk-relative position");
		// Pixel art: the border, the wobble and the highlights are all read off texel centres.
		helper.assertTrue(fsh.contains("TEXELS") && fsh.contains("floor(p * TEXELS) + 0.5") && fsh.contains("floor(chunkPos * TEXELS) + 0.5"),
				"the paint snaps to the 16-px grid before it decides anything");
		helper.assertTrue(RivalsPack.class.getResource("/rivals_shaders/block.fsh") == null, "the block shader override is gone");
		helper.succeed();
	}

	/**
	 * The same gloss in the item pair, which is what draws the block displays on stairs, slabs and panes
	 * (cutoutBlockItemSheet → ITEM_CUTOUT → core/item). The delta over vanilla is two varyings and where
	 * the in-face coordinate comes from: the sprite, not a position. A display's vertices are baked by the
	 * render PoseStack with the camera rotation already in them, so there is no world position in the item
	 * pair to take fract() of — the first attempt reconstructed one from the Globals camera and drew
	 * borders across the middle of cells.
	 */
	@GameTest
	public void itemShaderCarriesTheSameGloss(GameTestHelper helper) {
		helper.assertTrue(RivalsPack.SHADERS.containsAll(List.of("item.vsh", "item.fsh")), "the pack ships the item pair");
		String fsh = new String(RivalsPack.shader("item.fsh"), StandardCharsets.UTF_8);
		String vsh = new String(RivalsPack.shader("item.vsh"), StandardCharsets.UTF_8);
		helper.assertTrue(fsh.contains("RIVALS_GLOSS") && fsh.contains("0.9216") && fsh.contains("0.008"),
				"fragment shader guards on the marker alpha");
		helper.assertTrue(fsh.contains("fract(texCoord0 * vec2(textureSize(Sampler0, 0)) / SPRITE)"),
				"the in-face coordinate is the sprite's own");
		helper.assertTrue(!fsh.contains("paintPos") && !vsh.contains("paintPos") && !vsh.contains("CameraBlockPos"),
				"and no reconstructed world position is left anywhere in the pair");
		helper.assertTrue(fsh.contains("in vec3 viewPos") && vsh.contains("out vec3 viewPos"), "the pair agrees on the view position");
		helper.assertTrue(fsh.contains("floor(p * TEXELS) + 0.5"), "and snaps to the same 16-px grid the terrain gloss does");
		// Lighting parity: the chunks never get a directional term, so neither may the quads.
		helper.assertTrue(fsh.contains("color = vec4(tex.rgb * rawColor.rgb, 1.0)"),
				"paint takes the tint without the item pair's directional light");
		// Vanilla's own item work has to survive: the cutout, the lightmap and overlay, the glint.
		helper.assertTrue(fsh.contains("#ifdef ALPHA_CUTOUT") && fsh.contains("lightMapColor") && fsh.contains("GlintSampler"),
				"vanilla item shading kept");
		helper.assertTrue(vsh.contains("minecraft_mix_light(Light0_Direction"), "vanilla item lighting kept");
		// And terrain keeps its own path: chunk geometry does have a chunk-relative position.
		String terrain = new String(RivalsPack.shader("terrain.fsh"), StandardCharsets.UTF_8);
		helper.assertTrue(terrain.contains("in vec3 chunkPos"), "the terrain gloss still reads the chunk position");
		helper.succeed();
	}

	/**
	 * The paint quads' UVs carry the orientation. The gloss shader reads its in-face coordinate off the
	 * sprite, so which way round the sprite lies decides which side of a cell a border opens on; vanilla
	 * maps a face's u and v to world axes differently per face, so every face needs its own flip. The
	 * expected arrays below are written out rather than computed, so that a change to the table has to be
	 * argued for here: with vertex 0 at (uv[0], uv[1]), 1 at (uv[0], uv[3]), 2 at (uv[2], uv[3]) and 3 at
	 * (uv[2], uv[1]) — CuboidFace.UVs in 26.3, which does not sort them — and FaceInfo's corner table,
	 * vanilla's u and v run along up (+x, +z), down (+x, −z), north (−x, −y), south (+x, −y), west (+z, −y)
	 * and east (−z, −y), while the paint's own axes are (+x, +z) on a Y attach, (+z, +y) on X and (+x, +y)
	 * on Z.
	 */
	@GameTest
	public void paintQuadUvsOrientTheSprite(GameTestHelper helper) {
		Map<String, int[]> expected = new LinkedHashMap<>();
		expected.put("up", new int[] {0, 0, 16, 16});      // already the paint's axes
		expected.put("down", new int[] {0, 16, 16, 0});    // v runs −z, so flip v
		expected.put("north", new int[] {16, 16, 0, 0});   // u runs −x and v runs −y, so flip both
		expected.put("south", new int[] {0, 16, 16, 0});   // v runs −y
		expected.put("west", new int[] {0, 16, 16, 0});    // u already runs +z, v runs −y
		expected.put("east", new int[] {16, 16, 0, 0});    // u runs −z and v runs −y
		for (var entry : expected.entrySet()) {
			Direction side = Direction.byName(entry.getKey());
			helper.assertValueEqual(java.util.Arrays.toString(PaintArt.uv(side)),
					java.util.Arrays.toString(entry.getValue()), side + " uv");
		}
		// And every generated face model actually carries them: two faces per attach direction, the two
		// sides of the paper-thin quad, each with the flip its own facing needs.
		Map<String, byte[]> files = PaintArt.packFiles();
		for (Direction attach : Direction.values()) {
			String path = "assets/rivals-paint/models/block/" + PaintArt.modelName(attach) + ".json";
			helper.assertTrue(files.containsKey(path), "model in pack: " + path);
			JsonObject model = JsonParser.parseString(new String(files.get(path), StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject faces = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
			helper.assertValueEqual(faces.size(), 2, attach + ": both sides of the quad are drawn");
			for (var face : faces.entrySet()) {
				int[] want = expected.get(face.getKey());
				helper.assertTrue(want != null, attach + ": unexpected face " + face.getKey());
				JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
				for (int i = 0; i < 4; i++) {
					helper.assertValueEqual(uv.get(i).getAsInt(), want[i], attach + " " + face.getKey() + " uv[" + i + "]");
				}
			}
		}
		helper.succeed();
	}

	/**
	 * Ink on the screen is the health the player has lost, in quarters: {@code ink_1} up to a quarter
	 * gone, {@code ink_4} nearly dead. There is no meter of its own any more — nothing to top up and
	 * nothing to drain — so healing wipes the ink, a respawn starts clean, and the overlay is a health
	 * bar the player cannot help reading. What a hit still decides is <em>whose</em> ink it is: the
	 * colour is the team of the last enemy paint that touched them, and a player no enemy has touched
	 * has no colour and so no ink, however far a fall took them.
	 */
	@GameTest
	public void inkOnScreenIsTheHealthYouHaveLost(GameTestHelper helper) {
		ServerPlayer player = mockServerPlayer(helper, GameType.SURVIVAL);
		PlayerTeam data = team(helper, PaintColor.DATA);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), data);
		InkOnScreen.forget(player);
		player.setHealth(player.getMaxHealth());
		helper.assertValueEqual(InkOnScreen.amount(player), 0, "full health is a clean screen");
		InkOnScreen.hit(player, PaintColor.IT, 4.0f);
		helper.assertTrue(InkOnScreen.color(player) == PaintColor.IT, "the ink is the shooter's colour");
		helper.assertValueEqual(InkOnScreen.amount(player), 0, "but a hit that cost no health is no ink");
		InkOnScreen.tick(player);
		helper.assertValueEqual(InkOnScreen.ledFor(player), InkOnScreen.IDLE, "and the LED stays dark");
		// Twenty hit points and four overlays, so five lost is the top of state 1 and nineteen is deep
		// in state 4. Floored, which is why a quarter gone is 63 and not 64.
		InkOnScreen.hit(player, PaintColor.IT, 4.0f);
		player.setHealth(15.0f);
		helper.assertValueEqual(InkOnScreen.amount(player), 63, "five of twenty lost");
		helper.assertValueEqual(inkState(InkOnScreen.amount(player)), 1, "which the shader draws as ink_1");
		player.setHealth(10.0f);
		helper.assertValueEqual(InkOnScreen.amount(player), 127, "ten of twenty lost");
		helper.assertValueEqual(inkState(InkOnScreen.amount(player)), 2, "which the shader draws as ink_2");
		player.setHealth(5.0f);
		helper.assertValueEqual(InkOnScreen.amount(player), 191, "fifteen of twenty lost");
		helper.assertValueEqual(inkState(InkOnScreen.amount(player)), 3, "which the shader draws as ink_3");
		player.setHealth(1.0f);
		helper.assertValueEqual(InkOnScreen.amount(player), 242, "nineteen of twenty lost");
		helper.assertValueEqual(inkState(InkOnScreen.amount(player)), 4, "which the shader draws as ink_4");
		// Ink from the other team repaints the visor rather than mixing: one colour is all the shader
		// draws, and it is the newest enemy paint to touch the player.
		InkOnScreen.standing(player, PaintColor.IT);
		helper.assertTrue(InkOnScreen.color(player) == PaintColor.IT, "the ink it is standing in");
		InkOnScreen.tick(player);
		helper.assertValueEqual(InkOnScreen.ledFor(player) & 0xFF, 242, "the published amount is the health lost");
		helper.assertValueEqual(InkOnScreen.ledFor(player) >> 8 & 0xF, PaintColor.IT.ordinal(),
				"in the last enemy's colour");
		// A heal lowers the amount at once, and the LED catches up inside the send window.
		player.setHealth(11.0f);
		helper.assertValueEqual(InkOnScreen.amount(player), 114, "nine of twenty lost after the heal");
		for (int i = 0; i < InkOnScreen.SEND_EVERY + 1; i++) InkOnScreen.tick(player);
		helper.assertValueEqual(InkOnScreen.ledFor(player) & 0xFF, 114, "and the LED follows the health back up");
		// Healed up: the ink goes by itself, colour and all. Nothing has to wipe it.
		player.setHealth(player.getMaxHealth());
		InkOnScreen.tick(player);
		helper.assertValueEqual(InkOnScreen.amount(player), 0, "back to full health is a clean screen");
		helper.assertTrue(InkOnScreen.color(player) == null, "with no colour left behind");
		helper.assertValueEqual(InkOnScreen.ledFor(player), InkOnScreen.IDLE, "and a dark LED");
		// Not in a match: no ink, however much health is missing.
		InkOnScreen.hit(player, PaintColor.IT, 2.0f);
		player.setHealth(10.0f);
		helper.getLevel().getScoreboard().removePlayerFromTeam(player.getScoreboardName(), data);
		InkOnScreen.tick(player);
		helper.assertTrue(InkOnScreen.color(player) == null, "no team, no ink on the screen");
		helper.assertValueEqual(InkOnScreen.ledFor(player), InkOnScreen.IDLE, "and nothing on the LED either");
		// A hit on something that is not a player, and a hit that did nothing, are both no ink at all.
		InkOnScreen.hit(player, PaintColor.IT, 0.0f);
		helper.assertTrue(InkOnScreen.color(player) == null, "a hit for no damage is not paint in the face");
		helper.succeed();
	}

	/** Which overlay the shader picks for an amount: 1-63, 64-127, 128-191, 192-255, as ink.fsh does it. */
	private static int inkState(int amount) {
		return Math.min(4, amount / 64 + 1);
	}

	/**
	 * The data LED: the value written onto the held weapon is (255, team, amount). Red at full and green
	 * under 16 is the signature the probe shader hunts for; green's low nibble is the enemy team, so the
	 * shader knows which ink to draw; blue is the amount. With no ink the value is a dark grey that fails
	 * the signature, so the LED reads as an indicator that is off. Nothing here may drift from the
	 * shader's own decode without the ink coming out the wrong colour or the wrong size.
	 */
	@GameTest
	public void inkLedEncodesAmountAndColour(GameTestHelper helper) {
		int value = InkOnScreen.led(PaintColor.IT, 200);
		helper.assertValueEqual(value >> 16 & 0xFF, InkOnScreen.SIGNATURE_RED, "red at full");
		helper.assertValueEqual(value >> 8 & 0xFF, PaintColor.IT.ordinal(), "green is the enemy team's index");
		helper.assertTrue((value >> 8 & 0xFF) < 16, "and stays inside the nibble the shader tests");
		helper.assertValueEqual(value & 0xFF, 200, "blue is the amount");
		helper.assertValueEqual(InkOnScreen.led(PaintColor.DATA, 1), 0xFF0001, "DATA at amount 1 is 0xFF0001");
		// Out-of-range amounts are clamped rather than spilling into the team nibble.
		helper.assertValueEqual(InkOnScreen.led(PaintColor.DATA, 9000), 0xFF00FF, "clamped to the byte, teams untouched");
		// The idle value must fail the shader's test on both counts, or a clean screen would draw ink.
		helper.assertTrue((InkOnScreen.IDLE >> 16 & 0xFF) != 0xFF, "idle is not red at full");
		helper.assertTrue((InkOnScreen.IDLE >> 8 & 0xFF) >= 16, "and its green is outside the nibble");

		// And the value has to reach the weapon. PaintWeapon.inventoryTick is the one writer, so that is
		// what a held gun gets its LED from — the same tick that keeps its tank dyed.
		ServerPlayer player = mockServerPlayer(helper, GameType.SURVIVAL);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		InkOnScreen.forget(player);
		ItemStack gun = new ItemStack(PaintWeapon.of(Weapon.SHOOTER));
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		PaintWeapon weapon = (PaintWeapon) gun.getItem();
		weapon.inventoryTick(gun, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		helper.assertValueEqual(InkOnScreen.ledOf(gun), InkOnScreen.IDLE, "a clean screen leaves the LED dark");
		InkOnScreen.hit(player, PaintColor.IT, 4.0f);
		player.setHealth(10.0f);
		InkOnScreen.tick(player);
		weapon.inventoryTick(gun, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		int lit = InkOnScreen.led(PaintColor.IT, InkOnScreen.amount(player));
		helper.assertValueEqual(InkOnScreen.ledOf(gun), lit, "the first tick with ink lights the LED");
		// Every change is an item-slot sync, so the value is held still for a tick or two.
		player.setHealth(4.0f);
		InkOnScreen.tick(player);
		weapon.inventoryTick(gun, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		helper.assertValueEqual(InkOnScreen.ledOf(gun), lit, "and not again within the send window");
		InkOnScreen.tick(player);
		weapon.inventoryTick(gun, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		helper.assertTrue(InkOnScreen.ledOf(gun) != lit, "but the tick after the window it catches up");
		// A weapon stowed with a full screen must not come back out still carrying a live number.
		InkOnScreen.clear(player);
		weapon.inventoryTick(gun, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		helper.assertValueEqual(InkOnScreen.ledOf(gun), InkOnScreen.IDLE, "a cleared meter darkens the LED again");
		helper.succeed();
	}

	/**
	 * The LED value only ever reaches the player whose meter it is. Everyone else is handed the idle
	 * colour, because a lit LED on someone else's gun is both a tell and a false reading: the ink pass
	 * hunts the lower half of the frame for that signature, so another player's third-person weapon
	 * walking past would splatter the finder's own screen.
	 */
	@GameTest
	public void ledIsHiddenFromOtherViewers(GameTestHelper helper) {
		ItemStack gun = new ItemStack(PaintWeapon.of(Weapon.SHOOTER));
		UUID holder = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		int lit = InkOnScreen.led(PaintColor.IT, 200);
		InkOnScreen.put(gun, lit);
		InkOnScreen.owner(gun, holder);
		helper.assertValueEqual(InkOnScreen.ledOf(gun), lit, "the server's own stack carries the value");
		helper.assertTrue(holder.equals(InkOnScreen.ownerOf(gun)), "and knows whose it is");
		helper.assertValueEqual(PaintWeapon.ledForViewer(gun, holder), lit, "the holder's client is told it");
		helper.assertValueEqual(PaintWeapon.ledForViewer(gun, other), InkOnScreen.IDLE, "nobody else is");
		helper.assertValueEqual(PaintWeapon.ledForViewer(gun, null), InkOnScreen.IDLE, "and neither is a viewer with no profile");
		// A weapon that changes hands must not go on lighting up for whoever held it before.
		InkOnScreen.owner(gun, other);
		helper.assertValueEqual(PaintWeapon.ledForViewer(gun, holder), InkOnScreen.IDLE, "the old holder loses it");
		helper.assertValueEqual(PaintWeapon.ledForViewer(gun, other), lit, "and the new one gains it");
		// A stack nobody owns (a dropped weapon, a /give) shows nothing to anyone.
		ItemStack loose = new ItemStack(PaintWeapon.of(Weapon.SLOSHER));
		InkOnScreen.put(loose, lit);
		helper.assertValueEqual(PaintWeapon.ledForViewer(loose, holder), InkOnScreen.IDLE, "an unowned weapon is dark");
		helper.succeed();
	}

	/**
	 * The pack side of the ink: the {@code end_of_frame} chain vanilla asks for every frame, its two
	 * shaders, and the LED the meter is written onto — its texture, the tint source that colours it, and
	 * the one element on every weapon model that wears it. The chain has to read {@code minecraft:main},
	 * find the LED in a one-by-one target (so the search runs once a frame rather than once a pixel) and
	 * put the frame back where it found it.
	 */
	@GameTest
	public void packCarriesTheInkPostEffect(GameTestHelper helper) throws IOException {
		Map<String, byte[]> files = InkArt.packFiles();
		String chainPath = "assets/minecraft/post_effect/end_of_frame.json";
		String ledPath = "assets/rivals-paint/textures/item/data_led.png";
		for (String path : new String[] {chainPath, "assets/rivals-paint/shaders/post/ink.fsh",
				"assets/rivals-paint/shaders/post/ink_probe.fsh", ledPath}) {
			helper.assertTrue(files.containsKey(path), "in pack: " + path);
		}
		JsonObject chain = JsonParser.parseString(new String(files.get(chainPath), StandardCharsets.UTF_8)).getAsJsonObject();
		JsonObject probeTarget = chain.getAsJsonObject("targets").getAsJsonObject("rivals_ink_data");
		helper.assertValueEqual(probeTarget.get("width").getAsInt(), 1, "the data target is one pixel wide");
		helper.assertValueEqual(probeTarget.get("height").getAsInt(), 1, "and one pixel tall");
		JsonArray passes = chain.getAsJsonArray("passes");
		helper.assertValueEqual(passes.size(), 3, "probe, ink, blit back");
		helper.assertValueEqual(passes.get(0).getAsJsonObject().get("fragment_shader").getAsString(),
				"rivals-paint:post/ink_probe", "the probe runs first");
		helper.assertValueEqual(passes.get(0).getAsJsonObject().get("output").getAsString(), "rivals_ink_data", "into the data target");
		JsonObject inkPass = passes.get(1).getAsJsonObject();
		helper.assertValueEqual(inkPass.get("fragment_shader").getAsString(), "rivals-paint:post/ink", "then the ink");
		List<String> samplers = new ArrayList<>();
		for (JsonElement input : inkPass.getAsJsonArray("inputs")) {
			JsonObject json = input.getAsJsonObject();
			// The overlays are texture inputs and name a location rather than a target; they are checked
			// on their own below.
			if (!json.has("target")) continue;
			samplers.add(json.get("sampler_name").getAsString() + "=" + json.get("target").getAsString());
		}
		helper.assertTrue(samplers.contains("In=minecraft:main") && samplers.contains("Probe=rivals_ink_data"),
				"reading the frame and the data pixel: " + samplers);
		helper.assertValueEqual(passes.get(2).getAsJsonObject().get("output").getAsString(), "minecraft:main",
				"and the frame goes back where it came from");
		// The shaders' own halves of the contract.
		String probe = new String(files.get("assets/rivals-paint/shaders/post/ink_probe.fsh"), StandardCharsets.UTF_8);
		helper.assertTrue(probe.contains("frame.r > 0.99") && probe.contains("frame.g < 0.0627"),
				"the probe tests the marker signature: red at full, green under 16");
		helper.assertTrue(probe.contains("InSize"), "and searches in the input's own pixels");
		// It sweeps nothing: item.vsh pins the LED to a fixed 8x8 quad at the bottom centre, so the probe
		// reads the middle of that quad and confirms two pixels to either side, both of which are inside
		// it by construction.
		helper.assertTrue(probe.contains("LED_Y = 5.0") && probe.contains("InSize.x * 0.5"),
				"the probe reads the middle of the quad item.vsh pins the LED to");
		helper.assertTrue(!probe.contains("BAND = 0.08") && !probe.contains("STEP_SHARE"),
				"and no longer sweeps the bottom of the frame for it");
		helper.assertTrue(probe.contains("confirms(at, CONFIRM, here) && confirms(at, -CONFIRM, here)"),
				"confirming to both sides, since both are inside the quad");
		String ink = new String(files.get("assets/rivals-paint/shaders/post/ink.fsh"), StandardCharsets.UTF_8);
		// The LED's position used to ride in the probe's blue and alpha so this pass could paint over it.
		// There is nothing to paint over now — the hotbar is drawn on top of it — so those two bytes are
		// gone from both ends, and the ink pass reads only the amount and the team.
		helper.assertTrue(!ink.contains("probe.b") && !ink.contains("probe.a"),
				"the ink pass no longer reads the LED's position: the hotbar covers it");
		helper.assertTrue(probe.contains("vec4(here.b, here.g, 0.0, 1.0)"), "and the probe no longer sends one");
		// The ink is drawn from four overlays an artist can replace, bound as texture inputs on the ink
		// pass with no filtering — the texture is the pixel grid, so a bilinear sampler would blur it.
		List<String> overlays = new ArrayList<>();
		for (JsonElement input : inkPass.getAsJsonArray("inputs")) {
			JsonObject json = input.getAsJsonObject();
			if (!json.has("location")) continue;
			overlays.add(json.get("sampler_name").getAsString());
			helper.assertValueEqual(json.get("width").getAsInt(), InkArt.OVERLAY_WIDTH, "overlay width");
			helper.assertValueEqual(json.get("height").getAsInt(), InkArt.OVERLAY_HEIGHT, "overlay height");
			helper.assertTrue(json.has("bilinear") && !json.get("bilinear").getAsBoolean(),
					json.get("sampler_name").getAsString() + " is sampled with no filtering");
		}
		helper.assertValueEqual(overlays.size(), InkArt.INK_STATES,
				"one overlay per quarter of the health you can lose: " + overlays);
		String chainText = new String(files.get(chainPath), StandardCharsets.UTF_8);
		for (int state = 1; state <= InkArt.INK_STATES; state++) {
			// A texture input's location is bare: 26.3's PostChain resolves it as
			// "textures/effect/" + path + ".png", so the chain names rivals-paint:ink_1 and the file
			// sits at textures/effect/ink_1.png. A location with the directory or the extension in it asks
			// for textures/effect/textures/post/ink_1.png.png, and a missing texture input is the
			// magenta-and-black checker over the whole screen.
			helper.assertTrue(chainText.contains(InkArt.overlayLocation(state)),
					"the chain binds " + InkArt.overlayLocation(state));
			String file = "/assets/" + Rivals.MOD_ID + "/" + InkArt.overlay(state);
			try (InputStream in = Rivals.class.getResourceAsStream(file)) {
				helper.assertTrue(in != null, "and the file the client will look for is there: " + file);
			}
			helper.assertTrue(ink.contains("Ink" + state + "Sampler"), "and the shader reads Ink" + state + "Sampler");
		}
		for (JsonElement input : inkPass.getAsJsonArray("inputs")) {
			JsonObject json = input.getAsJsonObject();
			if (!json.has("location")) continue;
			String location = json.get("location").getAsString();
			helper.assertTrue(!location.contains("textures/") && !location.endsWith(".png"),
					"a texture input's location carries neither the directory nor the extension — PostChain "
							+ "adds textures/effect/ and .png itself — but " + json.get("sampler_name").getAsString()
							+ " asks for " + location);
		}
		// The four tones the overlay's greyscale is mapped onto, and the four states it picks between.
		helper.assertTrue(ink.contains("TONE_SHADOW = 0.3") && ink.contains("TONE_BASE = 0.6")
						&& ink.contains("TONE_LIGHT = 0.85"),
				"the shader steps the overlay's luminance into four tones of the team colour");
		// The states are not four hard steps any more: the layer the next state adds fades in over the
		// quarter, the way powder snow's frost does, so being hurt grows blobs in instead of popping
		// them onto the glass whole. Only the opacity is continuous — the tones stay quantised.
		helper.assertTrue(!ink.contains("floor(amount * 255.0 / 64.0)"),
				"the state is no longer picked as one of four hard steps");
		helper.assertTrue(ink.contains("smoothstep") && ink.contains("mix(frame, tone, opacity)"),
				"the arriving layer fades in over the frame instead");
		helper.assertTrue(!ink.contains("GRID"), "with no grid snapping of its own: the texture is the grid");
		// The overlays are drawn top row first, the way every paint program writes a PNG, and the post
		// chain's texCoord has y = 0 at the BOTTOM of the frame — so an unflipped sample showed the
		// drawings upside down and the drips ran up. An artist has to be able to paint them upright.
		helper.assertTrue(ink.contains("vec2(uv.x, 1.0 - uv.y)"),
				"the overlays are sampled flipped, so a PNG drawn the right way up is shown the right way up");
		helper.assertTrue(ink.contains("ProbeSampler"), "the ink reads the data pixel");
		for (PaintColor team : PaintColor.values()) {
			// Both team inks are hard-coded in the shader, so they have to be the colours the teams wear.
			String red = String.format(Locale.ROOT, "%.4f", (team.rgb >> 16 & 0xFF) / 255.0);
			helper.assertTrue(ink.contains(red), team + "'s ink (" + red + ") is in the shader");
		}
		// The item shaders' half: the unlit vertex tint, and the branch that puts it on the frame exactly.
		String itemFsh = new String(RivalsPack.shader("item.fsh"), StandardCharsets.UTF_8);
		String itemVsh = new String(RivalsPack.shader("item.vsh"), StandardCharsets.UTF_8);
		helper.assertTrue(itemVsh.contains("out vec4 rawColor") && itemVsh.contains("rawColor = Color"),
				"the vertex shader carries the tint before lighting");
		// RIVALS_LED_PIN: the LED is placed by the vertex shader, not by the model's display transform.
		// The hand is bobbed and the sprint FOV moves it, so a solved model position left the LED off the
		// bottom edge every other step and the ink blinked in walking rhythm.
		helper.assertTrue(itemVsh.contains("RIVALS_LED_PIN") && itemVsh.contains("ScreenSize"),
				"the vertex shader pins the LED to a quad in screen pixels");
		// It recognises an LED vertex by the tint the server wrote — the same signature the probe reads
		// back out of the frame — and not by reading the atlas: a vertex texture fetch for the sprite's
		// marker alpha never produced a quad on a real client.
		helper.assertTrue(itemVsh.contains("0.0627"),
				"recognising it by the tint signature's green window, the same 16 the probe tests");
		helper.assertTrue(itemVsh.contains("gl_VertexIndex"),
				"and taking the quad's corner from the vertex index");
		// 26.3's renderpearl backend parses Vulkan-flavoured GLSL. gl_VertexID does not compile there,
		// and a shader that does not compile takes the whole pack with it — which it did once.
		helper.assertTrue(!itemVsh.contains("gl_VertexID"),
				"spelled gl_VertexIndex: the GL spelling does not compile on renderpearl, and a pack with a "
						+ "shader that does not compile is a pack the client refuses");
		// Under an orthographic matrix — the GUI's hotbar icons and the inventory, drawn through this same
		// pipeline — the pinned quad would sit over the hotbar in plain view, so there the LED is clipped.
		helper.assertTrue(itemVsh.contains("ProjMat[2][3] == 0.0") && itemVsh.contains("vec4(0.0, 0.0, 2.0, 1.0)"),
				"and sends it outside the clip volume under an orthographic projection");
		// 26.3 clips to [0, 1] with the depth reversed — GameRenderer clears the depth texture to 0.0 and
		// tests GREATER, and Projection.setupPerspective asks JOML for zZeroToOne — so the near plane is
		// 1, not -1. A quad at -0.999 is outside the volume and every vertex of it was clipped, which is
		// exactly as invisible as having no LED at all.
		helper.assertTrue(!itemVsh.contains("-0.999"),
				"the pinned quad is not at OpenGL's classic near plane: 26.3's clip volume is [0, 1], reversed");
		helper.assertTrue(itemVsh.contains("0.9999, 1.0)"), "it sits just inside the near plane, which is 1");
		helper.assertTrue(itemFsh.contains("in vec4 rawColor") && itemFsh.contains("RIVALS_LED"),
				"and the fragment shader draws the LED from it");
		helper.assertTrue(itemFsh.contains(String.format(Locale.ROOT, "%.4f", InkArt.LED_ALPHA / 255.0)),
				"guarded on the LED's marker alpha " + InkArt.LED_ALPHA);
		// An LED with nothing to say draws nothing at all, for anybody: the idle grey is discarded, which
		// is what makes it invisible rather than merely small — on someone else's gun, and on your own in
		// third person or in the inventory.
		helper.assertTrue(itemFsh.contains("RIVALS_LED_IDLE") && itemFsh.contains("discard"),
				"the idle LED is discarded outright");
		helper.assertTrue(itemFsh.contains(String.format(Locale.ROOT, "%.5f", (InkOnScreen.IDLE >> 16 & 0xFF) / 255.0)),
				"and the value it tests for is InkOnScreen.IDLE's own channel, "
						+ String.format(Locale.ROOT, "%.5f", (InkOnScreen.IDLE >> 16 & 0xFF) / 255.0));
		// The LED's own texture: one flat value, so every mip level carries the marker unchanged.
		BufferedImage led = ImageIO.read(new ByteArrayInputStream(files.get(ledPath)));
		helper.assertValueEqual(led.getWidth(), InkArt.LED_SIZE, "the LED sprite is 16 px wide");
		helper.assertValueEqual(led.getHeight(), InkArt.LED_SIZE, "and 16 px tall");
		for (int y = 0; y < led.getHeight(); y++) {
			for (int x = 0; x < led.getWidth(); x++) {
				helper.assertValueEqual(led.getRGB(x, y), InkArt.LED_ALPHA << 24 | 0xFFFFFF, "marker alpha on white at " + x + "," + y);
			}
		}
		// And every weapon has to wear it: a second tint source for custom_model_data colour 0, and one
		// element whose faces take that tint.
		for (String id : new String[] {"paint_gun", "charger", "slosher", "roller"}) {
			try (InputStream in = Rivals.class.getResourceAsStream("/assets/" + Rivals.MOD_ID + "/items/" + id + ".json")) {
				JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
						.getAsJsonObject().getAsJsonObject("model");
				// Every branch of the definition, because the roller's is a condition on using_item with a
				// second model for the rolling pose: an LED that is only on one of the two would go dark
				// the moment the button went down.
				for (JsonObject branch : modelBranches(model)) {
					JsonObject tint = branch.getAsJsonArray("tints").get(1).getAsJsonObject();
					helper.assertValueEqual(tint.get("type").getAsString(), "minecraft:custom_model_data", id + ": LED tint source");
					helper.assertValueEqual(tint.get("index").getAsInt(), 0, id + ": colour 0");
					helper.assertValueEqual(tint.get("default").getAsInt(), InkOnScreen.IDLE, id + ": dark until the server says otherwise");
				}
			}
			try (InputStream in = Rivals.class.getResourceAsStream("/assets/" + Rivals.MOD_ID + "/items/" + id + ".json")) {
				JsonObject definition = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				// The LED's value changes every couple of ticks; without this the client replays the equip
				// animation each time and the gun dips in and out of view (the tank's dye did the same).
				helper.assertTrue(definition.has("hand_animation_on_swap") && !definition.get("hand_animation_on_swap").getAsBoolean(),
						id + ": a component change must not replay the equip animation");
			}
			try (InputStream in = Rivals.class.getResourceAsStream("/assets/" + Rivals.MOD_ID + "/models/item/" + id + ".json")) {
				JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				helper.assertValueEqual(model.getAsJsonObject("textures").get("led").getAsString(),
						Rivals.MOD_ID + ":item/" + InkArt.LED_TEXTURE, id + ": the model names the LED texture");
				int leds = 0;
				for (JsonElement element : model.getAsJsonArray("elements")) {
					JsonObject box = element.getAsJsonObject();
					boolean isLed = false;
					for (var face : box.getAsJsonObject("faces").entrySet()) {
						JsonObject json = face.getValue().getAsJsonObject();
						if ("#led".equals(json.get("texture").getAsString())) {
							isLed = true;
							helper.assertValueEqual(json.get("tintindex").getAsInt(), 1, id + ": the LED takes the second tint");
						}
					}
					if (!isLed) continue;
					leds++;
					helper.assertValueEqual(box.getAsJsonObject("faces").size(), 6, id + ": visible from every side");
					double size = box.getAsJsonArray("to").get(1).getAsDouble() - box.getAsJsonArray("from").get(1).getAsDouble();
					helper.assertTrue(size == 1.0, id + ": one model pixel tall, got " + size);
				}
				helper.assertValueEqual(leds, 1, id + ": exactly one LED");
			}
		}
		helper.succeed();
	}

	/**
	 * The overlays themselves, which are ordinary resources an artist is meant to paint over. The format
	 * is the contract in {@code textures/effect/README.md}, and it is the shader's contract too: 320×180,
	 * alpha as hard coverage rather than a soft edge, more ink as the state climbs, ink against every
	 * edge because that is where a faceful lands, and the middle of the screen left clear in every one of
	 * them, because that is where the player is aiming.
	 *
	 * <p>And the states are cumulative: every texel of state N−1 is ink in state N as well. The states
	 * are quarters of the health the player has lost, so they are walked up and down as the fight goes,
	 * and ink that vanished from a corner on the way up would read as the screen being wiped clean at the
	 * exact moment the player is being hurt.
	 */
	@GameTest
	public void theInkOverlaysAreDrawnToTheArtistsFormat(GameTestHelper helper) throws IOException {
		int[] covered = new int[InkArt.INK_STATES + 1];
		boolean[] before = null;
		for (int state = 1; state <= InkArt.INK_STATES; state++) {
			String path = "/assets/" + Rivals.MOD_ID + "/" + InkArt.overlay(state);
			BufferedImage image;
			try (InputStream in = Rivals.class.getResourceAsStream(path)) {
				helper.assertTrue(in != null, "overlay present: " + path);
				image = ImageIO.read(in);
			}
			helper.assertValueEqual(image.getWidth(), InkArt.OVERLAY_WIDTH, "overlay " + state + " width");
			helper.assertValueEqual(image.getHeight(), InkArt.OVERLAY_HEIGHT, "overlay " + state + " height");
			helper.assertTrue(image.getColorModel().hasAlpha(), "overlay " + state + " carries alpha");
			int wet = 0;
			boolean[] ink = new boolean[image.getWidth() * image.getHeight()];
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int alpha = image.getRGB(x, y) >>> 24;
					// Hard coverage: the shader draws a blocky edge, and a soft alpha makes it ragged
					// rather than soft.
					helper.assertTrue(alpha == 0 || alpha == 255,
							"overlay " + state + " alpha at " + x + "," + y + " is " + alpha + ", not 0 or 255");
					if (alpha != 255) continue;
					wet++;
					ink[y * image.getWidth() + x] = true;
				}
			}
			if (before != null) {
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						int at = y * image.getWidth() + x;
						helper.assertTrue(ink[at] || !before[at], "overlay " + state + " lost the texel at " + x + ","
								+ y + " that overlay " + (state - 1) + " had: the states build up, they are not redrawn");
					}
				}
			}
			before = ink;
			covered[state] = wet;
			helper.assertValueEqual(image.getRGB(image.getWidth() / 2, image.getHeight() / 2) >>> 24, 0,
					"overlay " + state + " leaves the middle of the screen clear");
			helper.assertTrue(edgeInk(image), "overlay " + state + " has ink against all four edges");
		}
		for (int state = 2; state <= InkArt.INK_STATES; state++) {
			helper.assertTrue(covered[state] > covered[state - 1],
					"state " + state + " covers more than " + (state - 1) + ": " + covered[state] + " vs " + covered[state - 1]);
		}
		helper.assertTrue(covered[InkArt.INK_STATES] > 2 * covered[1],
				"and a faceful is far more than a graze: " + covered[InkArt.INK_STATES] + " vs " + covered[1]);
		helper.succeed();
	}

	/** Is there ink touching all four edges of this overlay? Ink that creeps in has to start somewhere. */
	private static boolean edgeInk(BufferedImage image) {
		boolean left = false, right = false, top = false, bottom = false;
		for (int y = 0; y < image.getHeight(); y++) {
			if ((image.getRGB(0, y) >>> 24) == 255) left = true;
			if ((image.getRGB(image.getWidth() - 1, y) >>> 24) == 255) right = true;
		}
		for (int x = 0; x < image.getWidth(); x++) {
			if ((image.getRGB(x, 0) >>> 24) == 255) top = true;
			if ((image.getRGB(x, image.getHeight() - 1) >>> 24) == 255) bottom = true;
		}
		return left && right && top && bottom;
	}

	/** Every {@code minecraft:model} leaf of an item definition: one, or both sides of a condition. */
	private static List<JsonObject> modelBranches(JsonObject model) {
		if ("minecraft:model".equals(model.get("type").getAsString())) return List.of(model);
		return List.of(modelBranches(model.getAsJsonObject("on_true")).get(0),
				modelBranches(model.getAsJsonObject("on_false")).get(0));
	}

	/**
	 * The roller's second pose. With the client actually using the item now (the consumable on the
	 * client stack), the item definition can switch on {@code minecraft:using_item}: holding the button
	 * swaps in a model that is the same geometry with the head pitched down, pushed ahead and scaled up,
	 * so the roller reads as pressed against the floor rather than carried in front of the face.
	 *
	 * <p>The rolling model is a <em>child</em> of the plain one — display transforms and nothing else —
	 * so the geometry, the tints and the data LED element all stay in one place.
	 */
	@GameTest
	public void theRollerSwapsToARollingPoseWhileTheButtonIsHeld(GameTestHelper helper) throws IOException {
		JsonObject definition;
		try (InputStream in = Rivals.class.getResourceAsStream("/assets/" + Rivals.MOD_ID + "/items/roller.json")) {
			definition = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		JsonObject model = definition.getAsJsonObject("model");
		helper.assertValueEqual(model.get("type").getAsString(), "minecraft:condition", "the roller picks a model");
		helper.assertValueEqual(model.get("property").getAsString(), "minecraft:using_item",
				"on whether the player is using it, which is what holding the button now means");
		helper.assertValueEqual(model.getAsJsonObject("on_true").get("model").getAsString(),
				Rivals.MOD_ID + ":item/roller_rolling", "held: the rolling pose");
		helper.assertValueEqual(model.getAsJsonObject("on_false").get("model").getAsString(),
				Rivals.MOD_ID + ":item/roller", "let go: the plain one");
		JsonObject pose;
		try (InputStream in = Rivals.class.getResourceAsStream("/assets/" + Rivals.MOD_ID + "/models/item/roller_rolling.json")) {
			helper.assertTrue(in != null, "the rolling model is a real file");
			pose = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		helper.assertValueEqual(pose.get("parent").getAsString(), Rivals.MOD_ID + ":item/roller",
				"and is the same geometry: a child model, display only");
		helper.assertTrue(!pose.has("elements"), "with no geometry of its own to drift from the parent's");
		JsonObject plain;
		try (InputStream in = Rivals.class.getResourceAsStream("/assets/" + Rivals.MOD_ID + "/models/item/roller.json")) {
			plain = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		for (String view : new String[] {"firstperson_righthand", "firstperson_lefthand",
				"thirdperson_righthand", "thirdperson_lefthand"}) {
			JsonObject rolling = pose.getAsJsonObject("display").getAsJsonObject(view);
			helper.assertTrue(rolling != null, "the rolling pose overrides " + view);
			helper.assertTrue(!rolling.toString().equals(plain.getAsJsonObject("display").getAsJsonObject(view).toString()),
					view + ": and it is a different pose from the plain one");
		}
		// The head goes down and forward, and the whole thing gets bigger: that is the pose, and it is
		// the one thing here worth asserting in numbers rather than trusting to a screenshot nobody took.
		JsonArray held = pose.getAsJsonObject("display").getAsJsonObject("firstperson_righthand")
				.getAsJsonArray("rotation");
		JsonObject base = plain.getAsJsonObject("display").getAsJsonObject("firstperson_righthand");
		helper.assertTrue(held.get(0).getAsDouble() < base.getAsJsonArray("rotation").get(0).getAsDouble(),
				"pitched nose-down: the drum is the model's -z end, so a lower x rotation puts it on the floor");
		JsonObject rolling = pose.getAsJsonObject("display").getAsJsonObject("firstperson_righthand");
		helper.assertTrue(rolling.getAsJsonArray("translation").get(1).getAsDouble()
						< base.getAsJsonArray("translation").get(1).getAsDouble(), "and lower");
		helper.assertTrue(rolling.getAsJsonArray("translation").get(2).getAsDouble()
						< base.getAsJsonArray("translation").get(2).getAsDouble(), "and further ahead");
		helper.assertTrue(rolling.getAsJsonArray("scale").get(0).getAsDouble()
						> base.getAsJsonArray("scale").get(0).getAsDouble(), "and bigger");
		// Third person, where the first try moved the roller half a pixel: a display translation is in
		// sixteenths of a block, so a pose that differs by less than a whole unit differs by nothing
		// anyone can see. And the drum has to be turned to the front at all — the base [90, 0, 0] sends
		// the model's -z end over the shoulder, so no amount of lowering it would ever have shown a head
		// on the floor.
		for (String view : new String[] {"thirdperson_righthand", "thirdperson_lefthand"}) {
			JsonObject third = pose.getAsJsonObject("display").getAsJsonObject(view);
			JsonObject were = plain.getAsJsonObject("display").getAsJsonObject(view);
			helper.assertTrue(third.getAsJsonArray("rotation").get(0).getAsDouble() == 45.0,
					view + ": the drum is turned down and ahead (+y is ahead in the third-person hand frame; -45 put it in the ground behind)");
			double moved = 0.0;
			for (int axis = 0; axis < 3; axis++) {
				double delta = third.getAsJsonArray("translation").get(axis).getAsDouble()
						- were.getAsJsonArray("translation").get(axis).getAsDouble();
				moved += delta * delta;
			}
			helper.assertTrue(Math.sqrt(moved) >= 8.0,
					view + ": and moved by " + String.format(Locale.ROOT, "%.1f", Math.sqrt(moved))
							+ " of the sixteenths a display translation is in, which has to be a visible distance");
		}
		helper.succeed();
	}

	/**
	 * The LED element on every weapon model. It used to be solved so that the weapon's own
	 * {@code firstperson_righthand} transform landed it under the hotbar, and a test here re-walked
	 * 26.3's whole first-person chain to assert it did. That placement is gone: the hand is not fixed on
	 * screen — {@code GameRenderer.bobView} moves the hand pose by up to a tenth of the screen height per
	 * walk cycle, and the sprint FOV change moves it too — so the LED dropped off the bottom edge every
	 * other step and the ink blinked in walking rhythm. {@code item.vsh} pins it instead
	 * (RIVALS_LED_PIN), ignoring the model's position for LED vertices and emitting a fixed quad in
	 * screen pixels; that half is asserted with the rest of the shader contract in
	 * {@link #packCarriesTheInkPostEffect}.
	 *
	 * <p>So all a model owes the LED now is somewhere to hang the sprite: one element, every face on
	 * {@code #led}, taking the second tint. Where in the box it sits no longer matters — the vertex
	 * shader throws those coordinates away.
	 */
	@GameTest
	public void everyWeaponModelWearsOneLed(GameTestHelper helper) throws IOException {
		for (String id : new String[] {"paint_gun", "charger", "slosher", "roller"}) {
			JsonObject model;
			try (InputStream in = Rivals.class.getResourceAsStream("/assets/" + Rivals.MOD_ID + "/models/item/" + id + ".json")) {
				model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			}
			int leds = 0;
			for (JsonElement element : model.getAsJsonArray("elements")) {
				JsonObject box = element.getAsJsonObject();
				int led = 0;
				for (var face : box.getAsJsonObject("faces").entrySet()) {
					JsonObject json = face.getValue().getAsJsonObject();
					if (!"#led".equals(json.get("texture").getAsString())) continue;
					led++;
					helper.assertValueEqual(json.get("tintindex").getAsInt(), 1,
							id + ": the LED takes the second tint, which is where the meter is written");
				}
				if (led == 0) continue;
				leds++;
				// Every face, because the vertex shader maps all six onto the same quad and lets the
				// winding decide which of them survives.
				helper.assertValueEqual(led, 6, id + ": every face of the LED element is the LED");
				helper.assertValueEqual(box.getAsJsonObject("faces").size(), 6, id + ": and it has six faces");
			}
			helper.assertValueEqual(leds, 1, id + ": exactly one LED element");
		}
		helper.succeed();
	}

	/**
	 * The half of held use that lives on the client. A vanilla client decides for itself whether it is
	 * using an item, and it is the side that sends the release packet — so a client that never held the
	 * button meant the roller's release never arrived and its flick never fired, the use packet was
	 * repeated every four ticks instead, and the {@code use_effects} the client reads (no sprint, the
	 * speed multiplier) were never applied at all.
	 *
	 * <p>26.3's {@code Item.use} starts using anything carrying a {@code minecraft:consumable}, so the
	 * client stack carries one. It only works on a bare {@link net.minecraft.world.item.Item}: the old
	 * {@code warped_fungus_on_a_stick} disguise overrides {@code use} and returns PASS on the client
	 * before reading a component, which is why the disguise is a stick now.
	 */
	@GameTest
	public void theClientStackMakesTheClientHoldTheTrigger(GameTestHelper helper) {
		for (Weapon weapon : Weapon.values()) {
			PaintWeapon item = PaintWeapon.of(weapon);
			ItemStack server = new ItemStack(item);
			ItemStack client = item.getPolymerItemStack(server, TooltipFlag.Default.NORMAL, PacketContext.get(),
					helper.getLevel().registryAccess());
			Consumable consumable = client.get(DataComponents.CONSUMABLE);
			if (weapon == Weapon.CHARGER) {
				// The spyglass starts using the item on its own, and its animation is the scope.
				helper.assertValueEqual(client.getItem(), Items.SPYGLASS, "the charger is a spyglass");
				helper.assertTrue(consumable == null, "and needs no consumable to be held");
				continue;
			}
			helper.assertValueEqual(client.getItem(), Items.STICK,
					weapon + ": a bare Item, whose use() reads the consumable (a food-on-a-stick's does not)");
			if (weapon == Weapon.SLOSHER) {
				helper.assertTrue(consumable == null, "the slosher is a click, not a hold");
				continue;
			}
			helper.assertTrue(consumable != null, weapon + ": the client stack carries a consumable");
			helper.assertTrue(consumable.animation() == ItemUseAnimation.NONE,
					weapon + ": no animation, so the hand is not raised to a mouth");
			helper.assertTrue(!consumable.hasConsumeParticles(), weapon + ": and no eating crumbs");
			// As long as getUseDuration, which is vanilla's "as long as you like". Nothing ever completes
			// it — the server ends the use — and the sound and particles only start after 21.875% of it.
			helper.assertValueEqual(consumable.consumeTicks(), Weapon.CHARGE_MAX_TICKS,
					weapon + ": held for as long as the button is");
			UseEffects effects = client.get(DataComponents.USE_EFFECTS);
			helper.assertTrue(effects != null, weapon + ": and the use effects reach the client, which applies them");
			if (weapon == Weapon.SHOOTER) {
				// Splatoon slows a firing shooter to about seven tenths, not vanilla's one fifth, and it
				// does not take your sprint away.
				helper.assertTrue(effects.canSprint(), "a firing shooter may still sprint");
				helper.assertTrue(Math.abs(effects.speedMultiplier() - 0.72f) < 1.0e-6, "at 72% speed");
			} else {
				// You are pushing a drum along the floor. The roll's own speed attribute is the only
				// speed rule it gets, and sprinting with it was what made rolling read as free.
				helper.assertTrue(!effects.canSprint(), "a rolling roller may not sprint");
				helper.assertTrue(Math.abs(effects.speedMultiplier() - 1.0f) < 1.0e-6,
						"and takes no multiplier: roll_speed is the one rule about how fast a roll is");
			}
		}
		helper.succeed();
	}

	/** A fresh gun holds 40 ink, a shot costs one, an empty gun refills after the delay, own paint tops it up. */
	@GameTest
	public void inkDrainsRefillsAndTopsUp(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		// The tank is a hundred, which is Splatoon's own scale: every ink cost in Weapon is that game's
		// percentage without a factor in front of it. A stack written when it was forty holds a number
		// inside this one, and get() clamps either way, so nothing stored can read as a full tank it is not.
		helper.assertValueEqual(Ink.MAX, 100, "the tank reads as a percentage");
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "fresh gun is full");
		ItemStack old = new ItemStack(PaintWeapon.of(Weapon.SHOOTER));
		CustomData.update(DataComponents.CUSTOM_DATA, old, tag -> tag.putInt("rivals_ink", 40));
		helper.assertValueEqual(Ink.get(old), 40, "a round-6 tank comes back part-full, not wrong");
		CustomData.update(DataComponents.CUSTOM_DATA, old, tag -> tag.putInt("rivals_ink", 4000));
		helper.assertValueEqual(Ink.get(old), Ink.MAX, "and a number from nowhere is clamped");
		PaintWeapon.of(Weapon.SHOOTER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertValueEqual(Ink.get(gun), Ink.MAX - 1, "a shot costs one");
		readyToFire(player);
		Ink.set(gun, 0);
		long now = helper.getLevel().getServer().getTickCount();
		InteractionResult empty = PaintWeapon.of(Weapon.SHOOTER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(empty == InteractionResult.FAIL && Ink.isRefilling(gun, now), "empty gun starts refilling");
		Ink.finishIfDue(gun, now + Ink.REFILL_TICKS);
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "refilled after the delay");
		// A heavy weapon needs its whole shot in the tank: one ink refuses and starts a refill instead.
		ItemStack slosher = new ItemStack(PaintWeapon.of(Weapon.SLOSHER));
		player.setItemInHand(InteractionHand.MAIN_HAND, slosher);
		Ink.set(slosher, 1);
		long low = helper.getLevel().getServer().getTickCount();
		InteractionResult tooLow = PaintWeapon.of(Weapon.SLOSHER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(tooLow == InteractionResult.FAIL && Ink.isRefilling(slosher, low), "one ink does not buy a slosh");
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		Ink.set(gun, 10);
		Ink.add(gun, 1);
		helper.assertValueEqual(Ink.get(gun), 11, "top-up adds");
		Ink.add(gun, 100);
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "top-up clamps");
		// A deadline saved before a restart: the server tick count starts again at 0, so what was
		// "30 ticks from now" comes back as a deadline far in the future and would leave the gun
		// refilling for the rest of the session. Written straight into the tag, as loading would.
		Ink.set(gun, 0);
		CustomData.update(DataComponents.CUSTOM_DATA, gun, tag -> tag.putLong("rivals_refill_until", now + 100000L));
		helper.assertTrue(!Ink.isRefilling(gun, now), "a deadline from before a restart is not a refill in progress");
		Ink.finishIfDue(gun, now);
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "the stale deadline completes the refill instead of bricking the gun");
		helper.assertTrue(!Ink.isRefilling(gun, now), "and the deadline is gone");
		helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0).forEach(Entity::discard);
		helper.succeed();
	}

	/**
	 * Standing in your own ink refills the tank at Splatoon 1's rates, on the hundred-unit tank those
	 * rates were written for: ten seconds on your feet, three as a squid. Driven through the real tick
	 * with a real floor of paint underneath, over a whole second of ticks, so what is measured is the
	 * rate rather than one period of it — a rate written as "every N ticks, add M" is only correct if
	 * N and M divide out to the number Splatoon uses.
	 *
	 * <p>And none of it happens while the trigger is held, which is Splatoon's rule too. Without it the
	 * roller was a perpetual motion machine: rolling spends one ink every five ticks and standing in your
	 * own paint pays one every two, so rolling through your own ink filled the tank faster than rolling
	 * emptied it.
	 */
	@GameTest
	public void ownPaintRefillsAtSplatoonsRates(GameTestHelper helper) {
		stoneFloor(helper, 5);
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		// A floor of their own colour under their feet, which is what a top-up wants.
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, z)), Direction.UP, PaintColor.DATA);
			}
		}
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		helper.assertTrue(PlayerTick.paintUnder(player) == PaintColor.DATA, "standing in their own ink");
		// Both rates are "every N ticks, add M", so the count over a window depends on where in the period
		// the window starts. Started on a multiple of six, which both periods divide, thirty ticks is a
		// whole number of each: the measurement is the rate and not the phase.
		long base = helper.getLevel().getServer().getTickCount();
		base += (6 - base % 6) % 6;
		Ink.set(gun, 0);
		for (int i = 0; i < 30; i++) PlayerTick.tick(player, base + i);
		helper.assertValueEqual(Ink.get(gun), 15, "on foot: half an ink a tick, a hundred-unit tank in ten seconds");
		// And as a squid, which is sneaking on your own paint: the same window is worth three times as much.
		Ink.set(gun, 0);
		player.setShiftKeyDown(true);
		for (int i = 0; i < 30; i++) PlayerTick.tick(player, base + 60 + i);
		helper.assertTrue(PlayerTick.isSquid(player), "sneaking on own paint is squid form");
		helper.assertValueEqual(Ink.get(gun), 50, "as a squid: five ink every three ticks, a full tank in three seconds");
		player.setShiftKeyDown(false);
		// Trigger down on a paint weapon: nothing at all, however long you stand in your own paint.
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.ROLLER)));
		ItemStack roller = player.getItemInHand(InteractionHand.MAIN_HAND);
		Ink.set(roller, 0);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		helper.assertTrue(player.isUsingItem(), "the roller is being held down");
		for (int i = 0; i < 10; i++) PlayerTick.tick(player, base + 120 + i);
		helper.assertValueEqual(Ink.get(roller), 0, "a held trigger refills nothing, however wet the floor is");
		// And letting go is the tank running again, on the very next tick that is due one.
		player.releaseUsingItem();
		helper.assertTrue(!player.isUsingItem(), "the button is up");
		for (int i = 0; i < 10; i++) PlayerTick.tick(player, base + 180 + i);
		helper.assertValueEqual(Ink.get(roller), 5, "and letting go starts it again: half an ink a tick");
		Roll.stop(player);
		helper.succeed();
	}

	/** The action-bar text has ten cells, one per tenth of the tank, and says REFILLING while a refill runs. */
	@GameTest
	public void inkBarText(GameTestHelper helper) {
		String full = InkHud.bar(PaintColor.DATA, Ink.MAX, false, false).getString();
		helper.assertTrue(full.startsWith("INK ") && full.contains("100/100") && full.chars().filter(c -> c == '\u2588').count() == 10, "full bar: " + full);
		String half = InkHud.bar(PaintColor.DATA, Ink.MAX / 2, false, false).getString();
		helper.assertTrue(half.chars().filter(c -> c == '\u2588').count() == 5 && half.chars().filter(c -> c == '\u2591').count() == 5, "half bar: " + half);
		helper.assertTrue(InkHud.bar(PaintColor.DATA, 0, true, false).getString().contains("REFILLING"), "refilling text");
		helper.assertTrue(InkHud.bar(PaintColor.DATA, 5, false, true).getString().contains("SQUID"), "squid tag");
		// The charger's charge rides the same line, and only when there is one to show.
		String charging = InkHud.bar(PaintColor.DATA, 26, false, false, 0.48f).getString();
		helper.assertTrue(charging.contains("CHARGE") && charging.contains("48%"), "the charge reads out: " + charging);
		helper.assertTrue(charging.contains("▮") && charging.contains("▯"), "as a part-filled bar: " + charging);
		helper.assertTrue(!full.contains("CHARGE"), "and is left off when nothing is charging: " + full);
		Component charged = InkHud.bar(PaintColor.DATA, 26, false, false, 1.0f);
		helper.assertTrue(charged.getString().contains("100%"), "a full charge reads 100%: " + charged.getString());
		helper.assertTrue(!charged.getSiblings().isEmpty() && charged.getSiblings().getFirst().getStyle().isBold(),
				"and stands out when it is full");
		// No team is still a real tank: same text, grey instead of a team colour.
		Component noTeam = InkHud.bar(null, Ink.MAX, false, false);
		helper.assertValueEqual(noTeam.getString(), full, "the no-team bar reads the same");
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY).equals(noTeam.getStyle().getColor()),
				"no team: the bar is grey, got " + noTeam.getStyle().getColor());
		helper.succeed();
	}

	/** Sneaking on own paint is squid form (invisible, fast, no shooting); standing on enemy paint slows. */
	@GameTest
	public void squidFormAndEnemySlowness(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
		Player player = gunner(helper); // stands at relative (4, 3, 4), i.e. in the cell above that stone
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), Direction.UP, PaintColor.DATA);
		helper.assertTrue(PlayerTick.paintUnder(player) == PaintColor.DATA, "own paint under the player");
		player.setShiftKeyDown(true);
		PlayerTick.tick(player, 0);
		helper.assertTrue(PlayerTick.isSquid(player), "squid form on");
		helper.assertTrue(player.hasEffect(MobEffects.INVISIBILITY), "invisible");
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		helper.assertTrue(scale != null && scale.hasModifier(SquidState.SCALE_ID) && scale.getValue() < 0.6, "squid is half size");
		helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SquidState.SPEED_ID), "squid is fast");
		helper.assertTrue(player.getAttribute(Attributes.JUMP_STRENGTH).hasModifier(SquidState.JUMP_ID), "squid hops");
		helper.assertTrue(player.getAttribute(Attributes.STEP_HEIGHT).hasModifier(SquidState.STEP_ID), "squid glides over steps");
		helper.assertTrue(player.getAttribute(Attributes.SNEAKING_SPEED).hasModifier(SquidState.SNEAK_ID), "squid sneak penalty is lifted");
		helper.assertTrue(player.getAttribute(Attributes.SAFE_FALL_DISTANCE).hasModifier(SquidState.SAFE_FALL_ID), "squid hop lands safely");
		helper.assertTrue(player.getAttribute(Attributes.GRAVITY).hasModifier(SquidState.GRAVITY_ID), "squid arc is floatier");
		// A fresh effect ticks down for real, one server tick at a time. Re-applying it here should not
		// reset it back to full: it is still well above the running-low threshold, so `keep` must leave it.
		MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
		int firstDuration = invisibility.getDuration();
		invisibility.tickServer(helper.getLevel(), player, () -> {});
		PlayerTick.tick(player, 0);
		int secondDuration = player.getEffect(MobEffects.INVISIBILITY).getDuration();
		helper.assertTrue(secondDuration == firstDuration - 1,
				"effect ticks down instead of resetting to full: first=" + firstDuration + " second=" + secondDuration);
		InteractionResult shot = PaintWeapon.of(Weapon.SHOOTER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(shot == InteractionResult.FAIL, "no shooting as a squid");
		player.setShiftKeyDown(false);
		PlayerTick.tick(player, 1);
		helper.assertTrue(!PlayerTick.isSquid(player), "squid form off when not sneaking");
		helper.assertTrue(!player.getAttribute(Attributes.SCALE).hasModifier(SquidState.SCALE_ID)
				&& !player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SquidState.SPEED_ID), "modifiers removed on exit");
		helper.assertTrue(!player.getAttribute(Attributes.SNEAKING_SPEED).hasModifier(SquidState.SNEAK_ID)
				&& !player.getAttribute(Attributes.SAFE_FALL_DISTANCE).hasModifier(SquidState.SAFE_FALL_ID)
				&& !player.getAttribute(Attributes.GRAVITY).hasModifier(SquidState.GRAVITY_ID), "dive modifiers removed on exit");
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), Direction.UP, PaintColor.IT);
		PlayerTick.tick(player, 2);
		helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "enemy paint slows");
		helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 1, "Slowness II");
		helper.assertTrue(player.getAttribute(Attributes.JUMP_STRENGTH).hasModifier(SquidState.NO_JUMP_ID), "enemy ink kills the jump");
		helper.succeed();
	}

	/** Entering squid form from a stand is a dive: a horizontal shove along the look direction. */
	@GameTest
	public void squidDiveSurges(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
		Player player = gunner(helper); // stands at relative (4, 3, 4), i.e. in the cell above that stone
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), Direction.UP, PaintColor.DATA);
		player.setYRot(-90f); // look +X
		player.setXRot(0f);
		helper.assertTrue(!player.isShiftKeyDown(), "not sneaking yet");
		PlayerTick.tick(player, 0); // standing in own paint, not shift: no squid form, no surge
		helper.assertTrue(!PlayerTick.isSquid(player), "not squid before shifting");
		helper.assertTrue(player.getDeltaMovement().horizontalDistance() < 0.01, "no surge before the dive");
		player.setShiftKeyDown(true);
		PlayerTick.tick(player, 1);
		helper.assertTrue(PlayerTick.isSquid(player), "squid form on after diving");
		Vec3 delta = player.getDeltaMovement();
		helper.assertTrue(delta.horizontalDistance() >= 0.3, "dive surge pushes horizontally, got " + delta);
		helper.assertTrue(delta.x > 0, "surge follows the look direction (+X), got " + delta);
		helper.succeed();
	}

	/**
	 * Others see a squid, not a floating nothing: a team-coloured Pirkko rides the player's feet while the
	 * form is on, and goes down with it. Her own player is never sent her, which is a per-viewer thing a
	 * server-side test cannot see; what it can see is that the holder exists, carries one element, is
	 * attached, and is destroyed on the way out.
	 */
	@GameTest
	public void squidShowsPirkkoToOthers(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
		Player player = gunner(helper); // stands at relative (4, 3, 4), the cell above that stone
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), Direction.UP, PaintColor.DATA);
		helper.getLevel().addFreshEntity(player); // a display rides an entity the level knows about
		player.setShiftKeyDown(true);
		PlayerTick.tick(player, 0);
		helper.assertTrue(PlayerTick.isSquid(player), "squid form on");
		ElementHolder holder = SquidDisplay.holderOf(player);
		helper.assertTrue(holder != null, "a Pirkko rides the squid");
		helper.assertValueEqual(holder.getElements().size(), 1, "one display element");
		helper.assertTrue(holder.getAttachment() != null, "attached to the player");
		// The squid's own player is never sent it: the holder refuses to start watching them, which is a
		// per-viewer thing a game test has no second connection to see, so the rule is asked directly.
		helper.assertTrue(SquidDisplay.hiddenFrom(player, player.getUUID()), "the squid never sees its own Pirkko");
		helper.assertTrue(!SquidDisplay.hiddenFrom(player, UUID.randomUUID()), "everybody else does");
		// Lying still in its own ink is how a squid hides, so the figure is not drawn at all. The first tick
		// has no measured movement, which is exactly that case.
		helper.assertTrue(!SquidDisplay.isShown(player), "a still squid in its own ink shows nothing");
		// A second tick with the squid moved along keeps the one figure rather than making another, and
		// shows it: anything that leaves a wake is worth seeing.
		Vec3 stepped = player.position().add(0.3, 0, 0);
		player.setPos(stepped.x, stepped.y, stepped.z);
		PlayerTick.tick(player, 1);
		helper.assertTrue(SquidDisplay.holderOf(player) == holder, "the same figure, turned rather than replaced");
		helper.assertTrue(SquidDisplay.isShown(player), "a swimming squid is a Pirkko");
		// And stopping hides it again, without taking the holder down.
		PlayerTick.tick(player, 2);
		helper.assertTrue(!SquidDisplay.isShown(player), "holding still hides it again");
		helper.assertTrue(SquidDisplay.holderOf(player) == holder, "the holder is not rebuilt for it");
		player.setShiftKeyDown(false);
		PlayerTick.tick(player, 3);
		helper.assertTrue(SquidDisplay.holderOf(player) == null, "the figure goes with the form");
		helper.assertTrue(holder.getAttachment() == null || holder.getAttachment().isRemoved(), "and its attachment with it");
		player.discard();
		helper.succeed();
	}

	/**
	 * The figure itself: Julle's Pirkko, in the team colour, lying on the floor with her head leading.
	 *
	 * <p>{@link SquidDisplay#show} is called directly rather than through a tick, because what is being
	 * checked is the map from a measured movement to a pose and a tick can only produce one movement at a
	 * time. The pose is read back off the element the way a client would: the model id and the dye off the
	 * stack, and the heading off the left rotation by turning the model's own nose direction — its
	 * <em>−Z</em>, which is the half-turn the display has to add — and seeing where it points.
	 */
	@GameTest
	public void pirkkoWearsTheTeamColourAndLeadsWithHerHead(GameTestHelper helper) {
		stoneFloor(helper, 5);
		Player player = gunner(helper);
		helper.getLevel().addFreshEntity(player); // a display rides an entity the level knows about
		// A slow crawl: moving enough to have a direction, slowly enough that the swimming pitch is a
		// degree and a half, so the heading can be read off the nose almost on its own.
		SquidDisplay.show(player, PaintColor.DATA, new Vec3(0.03, 0, 0), false);
		ElementHolder holder = SquidDisplay.holderOf(player);
		helper.assertTrue(holder != null, "a figure rides the squid");
		ItemDisplayElement element = (ItemDisplayElement) holder.getElements().getFirst();
		ItemStack stack = element.getItem();
		helper.assertValueEqual(stack.get(DataComponents.ITEM_MODEL), Rivals.id("pirkko"),
				"the display wears the pirkko model");
		DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
		helper.assertTrue(dye != null && dye.rgb() == PaintColor.DATA.rgb,
				"dyed the team colour, which is what tint index 0 multiplies the grey sheet by");
		// NONE, not FIXED: the delivery's `fixed` transform stands her up for an item frame, which on the
		// floor would balance her on her nose.
		helper.assertValueEqual(element.getItemDisplayContext(), ItemDisplayContext.NONE,
				"rendered in the context that applies no transform of its own");
		// The underside on the floor: an item's coordinates are centred, so the underside of a model whose
		// own y starts at 0 is half a block below the origin — LIFT has to put that back, plus a hair.
		helper.assertTrue(Math.abs(SquidDisplay.LIFT - 0.5 * SquidDisplay.SCALE - SquidDisplay.CLEARANCE) < 1.0e-9,
				"LIFT lands the underside " + SquidDisplay.CLEARANCE + " above the feet, got " + SquidDisplay.LIFT);
		helper.assertTrue(Math.abs(SquidDisplay.SCALE * SquidDisplay.MODEL_LONG / 16f - SquidDisplay.LENGTH) < 1.0e-6,
				"and the scale makes her " + SquidDisplay.LENGTH + " blocks long");
		// Where her nose points. The model's head is towards -Z, so that is the vector to turn.
		Vector3f nose = new Vector3f(0, 0, -1).rotate(new Quaternionf(element.getLeftRotation()));
		helper.assertTrue(nose.x > 0.99f, "the head leads along the movement (+X), got " + nose);
		helper.assertTrue(Math.abs(nose.z) < 0.05f, "and not across it, got " + nose);
		// Turn around and it turns around: the heading is the measured movement, not the player's look.
		SquidDisplay.show(player, PaintColor.DATA, new Vec3(0, 0, -0.03), false);
		nose = new Vector3f(0, 0, -1).rotate(new Quaternionf(element.getLeftRotation()));
		helper.assertTrue(nose.z < -0.99f, "swimming north, the head points north, got " + nose);
		SquidDisplay.hide(player);
		player.discard();
		helper.succeed();
	}

	/**
	 * The poses. A leap pitches the nose up and lifts her a little, a fall pitches it down, and a swim on
	 * the flat leans in by speed without ever standing up — "dives and jumps around", which the old blob
	 * could only say by getting longer.
	 */
	@GameTest
	public void pirkkoDivesAndLeapsWithThePlayer(GameTestHelper helper) {
		stoneFloor(helper, 5);
		Player player = gunner(helper);
		helper.getLevel().addFreshEntity(player);
		SquidDisplay.show(player, PaintColor.DATA, new Vec3(0.3, 0, 0), false);
		ItemDisplayElement element = (ItemDisplayElement) SquidDisplay.holderOf(player).getElements().getFirst();
		// Swimming flat out: nose down, but only a lean — a tenth of a turn would be drilling into the floor.
		float swimming = pitchDegrees(element);
		helper.assertTrue(swimming < -1f && swimming >= -10.5f,
				"a fast swim leans nose-down by up to ten degrees, got " + swimming);
		// And the figure keeps its shape: the stretch along the swim is a hint of one now, not the blob's.
		Vector3f scale = new Vector3f(element.getScale());
		helper.assertTrue(scale.z / scale.x <= 1.7f + 1.0e-4f,
				"the along-swim stretch stays mild, got z/x " + scale.z / scale.x);
		helper.assertValueEqual(scale.y, SquidDisplay.SCALE, "and her height is left alone while swimming");
		// Rising fast: nose up, and off the floor a little.
		SquidDisplay.show(player, PaintColor.DATA, new Vec3(0.3, 0.5, 0), false);
		float leaping = pitchDegrees(element);
		helper.assertTrue(leaping > 25f, "a leaping squid points its nose up, got " + leaping);
		helper.assertTrue(!element.getLeftRotation().equals(new Quaternionf(), 1.0e-4f),
				"which is a rotation, not the identity the old blob carried on a leap");
		helper.assertTrue(element.getTranslation().y() > 0.0f,
				"and lifts a little, got " + element.getTranslation().y());
		// Falling fast, still in the air: nose down the same amount. Off the ground explicitly, because a
		// fast drop onto the ground is a landing instead, which is the next case.
		player.setOnGround(false);
		SquidDisplay.show(player, PaintColor.DATA, new Vec3(0.3, -0.5, 0), false);
		helper.assertTrue(pitchDegrees(element) < -25f, "a diving squid points it down, got " + pitchDegrees(element));
		// Flat again: the pitch comes back, and nothing is left lifted.
		SquidDisplay.show(player, PaintColor.DATA, new Vec3(0.03, 0, 0), false);
		helper.assertTrue(pitchDegrees(element) > -3f, "a crawl is nearly flat, got " + pitchDegrees(element));
		helper.assertValueEqual(element.getTranslation().y(), 0.0f, "and back down on the floor");
		// And the landing: the same drop, but onto the floor, pancakes her flat for a couple of ticks —
		// milder than the blob's squash, and with no pitch at all, because a pancake has its nose down.
		player.setOnGround(true);
		SquidDisplay.show(player, PaintColor.DATA, new Vec3(0.3, -0.5, 0), false);
		Vector3f squashed = new Vector3f(element.getScale());
		helper.assertTrue(squashed.y < SquidDisplay.SCALE && squashed.x > SquidDisplay.SCALE,
				"a landing is wider and flatter than she stands, got " + squashed);
		helper.assertTrue(squashed.y / SquidDisplay.SCALE > 0.6f && squashed.x / SquidDisplay.SCALE < 1.3f,
				"but a mild one, got " + squashed);
		helper.assertValueEqual(pitchDegrees(element), 0.0f, "and flat on the floor while it holds");
		SquidDisplay.hide(player);
		player.discard();
		helper.succeed();
	}

	/**
	 * How far above the horizontal the figure's nose is pointing, in degrees: positive is nose-up. Read
	 * from the rotation the client is sent, by turning the model's own head direction with it.
	 */
	private static float pitchDegrees(ItemDisplayElement element) {
		Vector3f nose = new Vector3f(0, 0, -1).rotate(new Quaternionf(element.getLeftRotation()));
		return (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, nose.y))));
	}

	/**
	 * The invisibility ends with the form, on the same tick. Squid form's size and speed are attribute
	 * modifiers and come off the instant it ends, but invisibility has no attribute, so it is a potion
	 * effect with a duration — and the user's report was the gap that left: the Pirkko vanished and the
	 * player stayed invisible for up to three quarters of a second after, neither squid nor player, just a
	 * hole in the floor. Both ways out are checked, standing up and running out of ink, and so is the one
	 * invisibility that must survive: a brewed one, which is not ambient and was never ours to take.
	 */
	@GameTest
	public void squidFormTakesItsInvisibilityWithIt(GameTestHelper helper) {
		stoneFloor(helper, 5); // floor at y=1
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), Direction.UP, PaintColor.DATA);
		Vec3 inTheInk = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
		player.setPos(inTheInk.x, inTheInk.y, inTheInk.z);
		helper.getLevel().addFreshEntity(player); // a display rides an entity the level knows about
		player.setShiftKeyDown(true);
		PlayerTick.tick(player, 0);
		helper.assertTrue(PlayerTick.isSquid(player), "squid form on");
		MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
		helper.assertTrue(invisibility != null, "and invisible with it");
		// Short enough that even a tick the loop never reaches cannot leave much of it: it used to be 15,
		// which is the three quarters of a second the user saw.
		helper.assertTrue(invisibility.getDuration() <= 6,
				"for a handful of ticks, not most of a second, got " + invisibility.getDuration());
		helper.assertTrue(invisibility.isAmbient() && !invisibility.isVisible(),
				"ambient and quiet, which is how the exit tells ours from a brewed one");
		helper.assertTrue(SquidDisplay.holderOf(player) != null, "and a Pirkko riding the player");
		// Standing back up. On the very next tick: no form, no figure, no invisibility.
		player.setShiftKeyDown(false);
		PlayerTick.tick(player, 1);
		helper.assertTrue(!PlayerTick.isSquid(player), "standing up ends the form on the next tick");
		helper.assertTrue(SquidDisplay.holderOf(player) == null, "and the Pirkko goes with it");
		helper.assertTrue(!player.hasEffect(MobEffects.INVISIBILITY),
				"and so does the invisibility, on that same tick");
		// The other way out: still shifting, but the ink is gone and the grace has run out.
		player.setShiftKeyDown(true);
		PlayerTick.tick(player, 2);
		helper.assertTrue(PlayerTick.isSquid(player) && player.hasEffect(MobEffects.INVISIBILITY), "a squid again");
		Vec3 high = helper.absoluteVec(new Vec3(4.5, 7.0, 4.5)); // out of reach of the ink below
		player.setPos(high.x, high.y, high.z);
		PlayerTick.tick(player, 20); // past SQUID_GRACE
		helper.assertTrue(!PlayerTick.isSquid(player), "out of ink, out of the form");
		helper.assertTrue(SquidDisplay.holderOf(player) == null, "no figure left over");
		helper.assertTrue(!player.hasEffect(MobEffects.INVISIBILITY), "and no invisibility left over either");
		// A potion, though, is the player's own. keep() leaves a longer one alone on the way in, and the
		// exit must leave it alone on the way out: it is not ambient, so it was never squid form's.
		player.setPos(inTheInk.x, inTheInk.y, inTheInk.z);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0, false, true, true));
		PlayerTick.tick(player, 21);
		helper.assertTrue(PlayerTick.isSquid(player), "a squid with a potion on");
		player.setShiftKeyDown(false);
		PlayerTick.tick(player, 22);
		helper.assertTrue(!PlayerTick.isSquid(player), "the form ends");
		MobEffectInstance brewed = player.getEffect(MobEffects.INVISIBILITY);
		helper.assertTrue(brewed != null && !brewed.isAmbient() && brewed.getDuration() > 6,
				"but a brewed invisibility is the player's own and stays: " + brewed);
		player.removeEffect(MobEffects.INVISIBILITY);
		player.discard();
		helper.succeed();
	}

	/**
	 * Squid form holds over the ink, not only in it. A jump or a ledge takes the paint out from under a
	 * squid's feet for a few ticks, and dropping the form (with the invisibility) for that is what the
	 * user saw as "you go out of invisibility because you were away from ink too long". Ink anywhere in
	 * the four cells below the feet holds it, and a ten-tick grace carries the gap after that.
	 */
	@GameTest
	public void squidStaysSquidOverInk(GameTestHelper helper) {
		stoneFloor(helper, 5); // floor at y=1
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), Direction.UP, PaintColor.DATA);
		Vec3 inTheInk = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)); // the painted cell itself
		player.setPos(inTheInk.x, inTheInk.y, inTheInk.z);
		player.setShiftKeyDown(true);
		PlayerTick.tick(player, 0);
		helper.assertTrue(PlayerTick.isSquid(player), "squid in its own ink");
		// Two blocks up: nothing under the feet, paint two cells below. Still a squid, tick after tick.
		Vec3 jumped = helper.absoluteVec(new Vec3(4.5, 4.0, 4.5));
		player.setPos(jumped.x, jumped.y, jumped.z);
		helper.assertTrue(PlayerTick.paintUnder(player) == null, "no paint in the cell the feet are in");
		helper.assertTrue(PlayerTick.inkBelow(player, PaintColor.DATA), "but ink below");
		for (long now = 1; now <= 5; now++) PlayerTick.tick(player, now);
		helper.assertTrue(PlayerTick.isSquid(player), "squid form holds over its own ink");
		helper.assertTrue(player.hasEffect(MobEffects.INVISIBILITY), "and stays invisible");
		// Five blocks up: out of reach of the ink, so only the grace is holding it now.
		Vec3 high = helper.absoluteVec(new Vec3(4.5, 7.0, 4.5));
		player.setPos(high.x, high.y, high.z);
		helper.assertTrue(!PlayerTick.inkBelow(player, PaintColor.DATA), "too high for the ink below");
		PlayerTick.tick(player, 6);
		helper.assertTrue(PlayerTick.isSquid(player), "the grace carries the gap");
		PlayerTick.tick(player, 16);
		helper.assertTrue(!PlayerTick.isSquid(player), "and runs out: no ink, no squid");
		helper.succeed();
	}

	/**
	 * A bottom slab's paint lands as display quads keyed one cell above the slab (like a stair tread), but
	 * a player standing on the slab has {@code blockPosition()} at the slab's own cell, one below that.
	 * {@code paintUnder} must still find it by falling back to the cell above the feet.
	 */
	/**
	 * A swimming squid leaves a wake, a still one does not, and none of the wake lands in the squid's own
	 * camera. Particles leave nothing behind on the server to assert on, so {@link PlayerTick#ripples} answers
	 * with what it sent: the count at the feet for everyone else, and the crumbs behind the squid for itself.
	 */
	@GameTest
	public void ripplesLeaveTheSquidsOwnCamera(GameTestHelper helper) {
		Player player = gunner(helper);
		ServerLevel level = helper.getLevel();
		// Two dust pillars and a crumb: the pillars are the mace-smash particle, which is what makes the
		// wake read as a mass of ink rather than as grit.
		PlayerTick.Wake east = PlayerTick.ripples(level, player, PaintColor.DATA, new Vec3(0.2, 0, 0));
		helper.assertValueEqual(east.others(), 3, "swimming east leaves a wake for everyone else");
		helper.assertValueEqual(east.total(), 3, "and nothing more");
		// A plain mock player is no viewer at all (only a ServerPlayer can be sent a particle packet), so the
		// self wake is not even attempted; the path it would have taken is asserted below.
		helper.assertValueEqual(east.self(), 0, "a plain mock player gets no wake of its own");
		helper.assertValueEqual(PlayerTick.ripples(level, player, PaintColor.IT, new Vec3(0, 0, -0.2)).others(), 3,
				"swimming north too");
		helper.assertTrue(Painter.pillar(PaintColor.DATA).getType() == ParticleTypes.DUST_PILLAR, "the wake is dust pillars");
		helper.assertTrue(Painter.pillar(PaintColor.DATA).getState().getBlock() == Painter.crumbs(PaintColor.DATA).getState().getBlock(),
				"carrying the same paint state the crumbs do, so it comes out in the team colour");
		helper.assertValueEqual(PlayerTick.ripples(level, player, PaintColor.DATA, Vec3.ZERO).total(), 0, "a still squid leaves nothing");
		// Only horizontal movement counts: falling is not swimming, and a crawl under the threshold is
		// the squid holding position rather than moving.
		helper.assertValueEqual(PlayerTick.ripples(level, player, PaintColor.DATA, new Vec3(0, -0.8, 0)).total(), 0,
				"falling is not swimming");
		helper.assertValueEqual(PlayerTick.ripples(level, player, PaintColor.DATA, new Vec3(0.01, 0, 0.01)).total(), 0,
				"a crawl is not swimming");
		// The self path: the squid's own wake trails a stride behind it at foot level, which is far enough from
		// its own eyes that Painter.burst keeps it — at the feet a rising pillar goes through the camera.
		ServerPlayer squid = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		Vec3 stand = helper.absoluteVec(new Vec3(4, 3, 4));
		squid.setPos(stand.x, stand.y, stand.z);
		Vec3 swum = new Vec3(0.2, 0, 0);
		Vec3 trail = PlayerTick.wakeBehind(squid, swum);
		helper.assertTrue(trail.x < squid.getX() - 1.0, "the wake is behind the swimmer, not under it");
		helper.assertValueEqual(trail.y, squid.getY() + 0.05, "at foot level");
		Vec3 feet = new Vec3(squid.getX(), squid.getY() + 0.05, squid.getZ());
		helper.assertTrue(squid.getEyePosition().distanceToSqr(trail) > squid.getEyePosition().distanceToSqr(feet),
				"and farther from the squid's own eyes than the wake everyone else gets");
		PlayerTick.Wake own = PlayerTick.ripples(level, squid, PaintColor.DATA, swum);
		helper.assertValueEqual(own.self(), 1, "a viewer of its own wake gets the crumb behind it");
		helper.assertValueEqual(own.others(), 3, "while everyone else still gets the full wake at its feet");
		helper.succeed();
	}

	/**
	 * {@link Painter#burst} drops any viewer whose eyes the burst would land in: a block crumb wears a random
	 * quarter of its state's particle sprite, so one spawned on a camera is a translucent team-coloured square
	 * over the whole screen.
	 *
	 * <p>The viewer is {@link #connected}: sending a particle to one player goes through the connection
	 * vanilla's mock player does not have, so without that the delivered case could not be counted at all.
	 */
	@GameTest
	public void burstsClearTheEyes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer viewer = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		Vec3 stand = helper.absoluteVec(new Vec3(4, 3, 4));
		viewer.setPos(stand.x, stand.y, stand.z);
		Vec3 eyes = viewer.getEyePosition();
		helper.assertValueEqual(Painter.burst(level, List.of(viewer), Painter.crumbs(PaintColor.DATA), eyes, 4, 0.1, 0.1, 0.1, 0.02),
				0, "a burst in a viewer's eyes reaches nobody");
		helper.assertFalse(Painter.clearOfEyes(viewer, eyes, 0.1), "the eyes themselves are never clear");
		helper.assertValueEqual(Painter.burst(level, List.of(viewer), Painter.crumbs(PaintColor.DATA), eyes.add(2.0, 0, 0), 4, 0.1, 0.1, 0.1, 0.02),
				1, "two blocks away it reaches them");
		// The spread counts: grains thrown half a block wide from a point that close still land on the camera.
		helper.assertFalse(Painter.clearOfEyes(viewer, eyes.add(1.2, 0, 0), 0.5), "a wide spread needs more room");
		helper.assertTrue(Painter.clearOfEyes(viewer, eyes.add(1.2, 0, 0), 0.1), "a tight one at that distance does not");
		// Vanilla only sends an unforced particle packet within thirty-two blocks; past that we do not either.
		Vec3 faraway = eyes.add(40.0, 0, 0);
		helper.assertFalse(Painter.clearOfEyes(viewer, faraway, 0.1), "past vanilla's cut-off nothing is worth sending");
		helper.assertValueEqual(Painter.burst(level, List.of(viewer), Painter.crumbs(PaintColor.DATA), faraway, 4, 0.1, 0.1, 0.1, 0.02),
				0, "so that burst reaches nobody either");
		helper.assertValueEqual(Painter.burst(level, List.of(), Painter.crumbs(PaintColor.IT), eyes, 4, 0.1, 0.1, 0.1, 0.02),
				0, "and with nobody in the level, nobody at all");
		helper.succeed();
	}

	/**
	 * Invisibility hides the body but not the gun, so squid form lies to everyone else's client about
	 * what is in the hands. The lie and the truth are built here; the broadcast itself needs a second
	 * tracking player, which a game test has no way to make.
	 */
	@GameTest
	public void squidHidesHeldItemsFromOthers(GameTestHelper helper) {
		Player player = gunner(helper);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
		List<Pair<EquipmentSlot, ItemStack>> hidden = SquidState.hiddenEquipment(player);
		helper.assertTrue(!hidden.isEmpty(), "the lie covers some slots");
		Set<EquipmentSlot> lied = new HashSet<>();
		for (Pair<EquipmentSlot, ItemStack> slot : hidden) {
			helper.assertTrue(slot.getSecond().isEmpty(), "hidden " + slot.getFirst() + " is empty");
			lied.add(slot.getFirst());
		}
		helper.assertTrue(lied.contains(EquipmentSlot.MAINHAND) && lied.contains(EquipmentSlot.OFFHAND), "both hands are hidden");
		List<Pair<EquipmentSlot, ItemStack>> real = SquidState.realEquipment(player);
		helper.assertValueEqual(real.size(), hidden.size(), "the truth covers the same slots as the lie");
		ItemStack mainhand = ItemStack.EMPTY;
		ItemStack offhand = ItemStack.EMPTY;
		for (Pair<EquipmentSlot, ItemStack> slot : real) {
			if (slot.getFirst() == EquipmentSlot.MAINHAND) mainhand = slot.getSecond();
			if (slot.getFirst() == EquipmentSlot.OFFHAND) offhand = slot.getSecond();
		}
		helper.assertTrue(mainhand.getItem() instanceof PaintWeapon, "the truth still carries the paint gun, not " + mainhand);
		helper.assertValueEqual(offhand.getItem(), Items.STICK, "and whatever is in the off hand");
		helper.succeed();
	}

	@GameTest
	public void squidDetectsPaintOnSlabTread(GameTestHelper helper) {
		BlockPos slab = new BlockPos(4, 2, 4);
		helper.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState());
		Player player = mockPlayer(helper, GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.5, 4.5)); // standing on top of the bottom slab
		player.setPos(at.x, at.y, at.z);
		boolean painted = Painter.paintFace(helper.getLevel(), helper.absolutePos(slab), Direction.UP, PaintColor.DATA);
		helper.assertTrue(painted, "slab top accepted paint");
		helper.assertTrue(PaintDisplays.of(helper.getLevel()).colorAt(helper.absolutePos(slab)) == null,
				"quads are keyed one cell above the slab, not at the slab's own cell");
		helper.assertTrue(PlayerTick.paintUnder(player) == PaintColor.DATA,
				"paint on the slab tread is found from the player's feet cell below it");
		helper.succeed();
	}

	/** Pushing against an own-colour painted wall while a squid lifts the player. */
	@GameTest
	public void squidWallSwim(GameTestHelper helper) {
		stoneFloor(helper, 5);
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(4, y, 2), Blocks.STONE);
		ServerPlayer player = wallSquid(helper, PaintColor.DATA);
		// Hugging the wall: its west plane is the x of relative cell 4, and a full-size player's box
		// reaches 0.3 either side of its centre.
		Vec3 at = helper.absoluteVec(new Vec3(3.7, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 2)), Direction.UP, PaintColor.DATA); // floor under
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 2)), Direction.WEST, PaintColor.DATA); // wall beside
		player.setShiftKeyDown(true);
		player.setYRot(-90f); // forward is +X, into the wall
		player.setLastClientInput(PUSHING);
		player.setDeltaMovement(0.1, 0, 0);
		// The tick squid form is entered is the dive: the surge is that tick's one velocity packet, and a
		// climb packet on top of it would overwrite the surge. The climb is the tick after.
		PlayerTick.tick(player, 0);
		helper.assertTrue(SquidState.isSquid(player), "squid");
		player.setDeltaMovement(0.1, 0, 0);
		PlayerTick.tick(player, 1);
		helper.assertTrue(player.getDeltaMovement().y > 0.2, "lifted up the inked wall, dy=" + player.getDeltaMovement().y);
		// Only floor paint left: the wall itself carries no paint, so the squid must not climb it.
		helper.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
		player.setDeltaMovement(0.1, 0, 0);
		PlayerTick.tick(player, 2);
		helper.assertTrue(player.getDeltaMovement().y < 0.2, "not lifted without a painted wall, dy=" + player.getDeltaMovement().y);
		helper.succeed();
	}

	/**
	 * The same climb up a wall that is not a full cube. A fence post's paint is display quads in the player's
	 * own cell rather than a paint block face, so this is the other half of {@code paintedWallBeside}:
	 * the quads must carry the face pointing back from the fence at the player.
	 *
	 * <p>It used to be a glass pane, which is the thinner shape and was the obvious one; panes are in
	 * {@code #rivals-paint:unpaintable} now, so the wall is a fence post instead — still a face that is
	 * not full, which is all this test needs it to be.
	 */
	@GameTest
	public void squidWallSwimUpAFence(GameTestHelper helper) {
		stoneFloor(helper, 5);
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(4, y, 2), Blocks.OAK_FENCE);
		ServerPlayer player = wallSquid(helper, PaintColor.DATA);
		Vec3 at = helper.absoluteVec(new Vec3(3.7, 2.0, 2.5)); // hugging the fence's cell
		player.setPos(at.x, at.y, at.z);
		BlockPos feet = helper.absolutePos(new BlockPos(3, 2, 2));
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 2)), Direction.UP, PaintColor.DATA); // floor under
		helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 2)), Direction.WEST, PaintColor.DATA),
				"the fence took paint");
		PaintDisplays displays = PaintDisplays.of(helper.getLevel());
		helper.assertTrue(displays.colorAt(feet) == PaintColor.DATA && displays.faceAt(feet) == Direction.WEST,
				"a fence's paint is quads in the player's own cell, facing back at the fence");
		// A wall cell's paint attaches the other way: the quads carry the EAST state, pointing at the fence.
		for (BlockState quad : displays.statesAt(feet)) {
			helper.assertValueEqual(quad, PaintStates.connected(PaintColor.DATA, Direction.EAST, 0),
					"the DATA wall state, with nothing painted in the plane beside it to border against");
		}
		player.setShiftKeyDown(true);
		player.setYRot(-90f); // forward is +X, into the fence
		player.setLastClientInput(PUSHING);
		player.setDeltaMovement(0.1, 0, 0);
		// The tick squid form is entered is the dive: the surge is that tick's one velocity packet, and a
		// climb packet on top of it would overwrite the surge. The climb is the tick after.
		PlayerTick.tick(player, 0);
		helper.assertTrue(SquidState.isSquid(player), "squid");
		player.setDeltaMovement(0.1, 0, 0);
		PlayerTick.tick(player, 1);
		helper.assertTrue(player.getDeltaMovement().y > 0.2, "lifted up the inked fence, dy=" + player.getDeltaMovement().y);
		// The quads are the only thing holding the climb up: take the fence away and the cell's quads die
		// with it, so the same push must go nowhere.
		helper.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
		displays.count(helper.getLevel()); // the sweep that drops cells whose surface is gone
		player.setDeltaMovement(0.1, 0, 0);
		PlayerTick.tick(player, 2);
		helper.assertTrue(player.getDeltaMovement().y < 0.2, "not lifted once the fence is gone, dy=" + player.getDeltaMovement().y);
		helper.succeed();
	}

	/**
	 * No paint under the feet at all — only a painted wall beside the player. Squid form must still
	 * hold (a climb off the floor paint would otherwise end squid form and drop the player mid-wall),
	 * and pressing into the wall climbs it while easing off holds the squid in place instead of
	 * sliding back down.
	 */
	@GameTest
	public void squidClingsToAnInkedWall(GameTestHelper helper) {
		stoneFloor(helper, 5);
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(4, y, 2), Blocks.STONE);
		ServerPlayer player = wallSquid(helper, PaintColor.DATA);
		Vec3 at = helper.absoluteVec(new Vec3(3.7, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		// Painted one cell up from the feet (head height): a full-cube wall face lands as a real
		// paint block in the cell in front of it — the feet cell itself if painted at feet height, which
		// paintUnder would find directly and defeat the point of this test. Painting at head height
		// instead keeps the feet cell (and its own paintUnder check) genuinely clean.
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 3, 2)), Direction.WEST, PaintColor.DATA);
		helper.assertTrue(PlayerTick.paintUnder(player) == null, "no paint under the feet");
		player.setShiftKeyDown(true);
		player.setYRot(-90f); // facing the wall, but not asking to move
		player.setLastClientInput(Input.EMPTY);
		player.setDeltaMovement(0.0, -0.05, 0.0);
		PlayerTick.tick(player, 0);
		helper.assertTrue(PlayerTick.isSquid(player), "squid form holds beside a wall with no floor paint");
		// The cling is gravity switched off, not a velocity packet: nothing touches the player's motion.
		assertClinging(helper, player, "beside the wall");
		helper.assertValueEqual(player.getDeltaMovement().y, -0.05, "the cling leaves the player's own motion alone");
		// A squid's box is half as wide, so hugging the same wall puts its centre closer to it.
		Vec3 hug = helper.absoluteVec(new Vec3(3.85, 2.0, 2.5));
		player.setPos(hug.x, hug.y, hug.z);
		player.setLastClientInput(PUSHING);
		player.setDeltaMovement(0.1, 0, 0);
		PlayerTick.tick(player, 1);
		helper.assertTrue(PlayerTick.isSquid(player), "still squid while pushing into the wall");
		double dy = player.getDeltaMovement().y;
		helper.assertTrue(dy > 0.35 && dy < 0.5, "climbs the wall at the wall-swim speed, dy=" + dy);
		helper.assertTrue(!player.getAttribute(Attributes.GRAVITY).hasModifier(SquidState.CLING_ID),
				"a climbing squid is not clinging");
		helper.succeed();
	}

	/** A squid held on a wall by gravity alone: the modifier is on and the attribute is exactly zero. */
	private static void assertClinging(GameTestHelper helper, Player player, String what) {
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		helper.assertTrue(gravity != null && gravity.hasModifier(SquidState.CLING_ID), what + ": clinging by gravity");
		helper.assertValueEqual(gravity.getValue(), 0.0, what + ": gravity is off");
	}

	/**
	 * A squid that jumps off an inked wall keeps its momentum. The cling used to be a velocity packet
	 * built from the server's own delta, sent every tick a wall was beside the squid, which overwrote
	 * the client's real motion — the jump impulse included. Now: no packet at all while the measured
	 * movement is already a jump, and no cling modifier to hold the arc down either.
	 */
	@GameTest
	public void squidJumpKeepsMomentum(GameTestHelper helper) {
		stoneFloor(helper, 5);
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(4, y, 2), Blocks.STONE);
		ServerPlayer player = wallSquid(helper, PaintColor.DATA);
		Vec3 at = helper.absoluteVec(new Vec3(3.7, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 2)), Direction.WEST, PaintColor.DATA);
		player.setShiftKeyDown(true);
		player.setYRot(-90f); // forward is +X, into the wall
		player.setLastClientInput(PUSHING);
		PlayerTick.tick(player, 0); // squid, and the tick that records where the player is
		helper.assertTrue(PlayerTick.isSquid(player), "squid beside the inked wall");
		// A jump: 0.3 blocks along the wall and 0.75 up since the last tick — a squid's own hop, which is
		// what the client actually did and what the server can only see by measuring.
		Vec3 jumped = at.add(0.0, 0.75, 0.3);
		player.setPos(jumped.x, jumped.y, jumped.z);
		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = false;
		int syncs = PlayerTick.velocitySyncs();
		PlayerTick.tick(player, 1);
		helper.assertValueEqual(PlayerTick.velocitySyncs(), syncs, "a jumping squid gets no velocity packet");
		helper.assertTrue(!player.syncVelocity, "nothing queued a velocity sync this tick");
		helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "and the player's own motion is untouched");
		helper.assertTrue(!player.getAttribute(Attributes.GRAVITY).hasModifier(SquidState.CLING_ID),
				"no cling while the squid is on its way up");
		helper.succeed();
	}

	/**
	 * The climb keeps going past the first block. Pushing into the wall is read from the client's own
	 * input, not from {@code horizontalCollision}: a real player walking into a wall has their movement
	 * clipped client-side and sends a delta of about zero, so the server copy never collides and the
	 * old check only ever lifted the one block squid form's taller step height carried them over.
	 */
	@GameTest
	public void squidClimbsAnInkedWall(GameTestHelper helper) {
		stoneFloor(helper, 5);
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(2, y, 1), Blocks.STONE);
		ServerPlayer player = wallSquid(helper, PaintColor.DATA);
		for (int y = 2; y <= 4; y++) {
			helper.assertTrue(Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(2, y, 1)), Direction.SOUTH, PaintColor.DATA),
					"the wall took paint at y=" + y);
		}
		// Hugging the wall: its south plane is the z of relative cell 2, and a full-size player's box
		// reaches 0.3 either side of its centre.
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.3));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(180f); // forward is -Z, into the wall
		player.setShiftKeyDown(true);
		player.setLastClientInput(PUSHING);
		player.setDeltaMovement(0, -0.08, 0);
		// The dive tick first: its surge is that tick's velocity packet, so the climb is the tick after.
		PlayerTick.tick(player, 0);
		helper.assertTrue(SquidState.isSquid(player), "squid");
		player.setDeltaMovement(0, -0.08, 0);
		PlayerTick.tick(player, 1);
		double first = player.getDeltaMovement().y;
		helper.assertTrue(first > 0.35 && first < 0.5, "lifted off the floor at the wall-swim speed, dy=" + first);
		// A block higher up the same wall — the case the user reported as "not working past one block".
		// A squid's box is half as wide, so hugging the wall puts its centre closer to it.
		Vec3 higher = helper.absoluteVec(new Vec3(2.5, 3.0, 2.15));
		player.setPos(higher.x, higher.y, higher.z);
		// Teleporting the player a whole block up reads, quite correctly, as a jump: the climb never
		// sends a packet over someone already rising faster than it would push them. One tick lets the
		// measurement settle at the new spot, and the tick after that is the climb this is about.
		PlayerTick.tick(player, 2);
		player.setDeltaMovement(0, -0.08, 0);
		PlayerTick.tick(player, 3);
		double second = player.getDeltaMovement().y;
		helper.assertTrue(second > 0.35 && second < 0.5, "still lifted a block higher up the wall, dy=" + second);
		// Off the keys: the squid clings where it is rather than climbing on by itself.
		player.setLastClientInput(Input.EMPTY);
		player.setDeltaMovement(0, -0.08, 0);
		int syncs = PlayerTick.velocitySyncs();
		PlayerTick.tick(player, 4);
		helper.assertTrue(PlayerTick.isSquid(player), "squid form holds while clinging");
		assertClinging(helper, player, "off the keys");
		// Zero gravity stops a squid falling further but does not take away the speed it already had, so
		// the tick the cling begins sends one flattening packet.
		helper.assertValueEqual(PlayerTick.velocitySyncs(), syncs + 1, "the cling arrests the fall, once");
		helper.assertValueEqual(player.getDeltaMovement().y, 0.0, "and leaves the squid hanging");
		// Every tick after that is gravity alone: no packets, and the player's motion is the player's.
		player.setDeltaMovement(0, -0.08, 0);
		PlayerTick.tick(player, 5);
		assertClinging(helper, player, "still clinging");
		helper.assertValueEqual(PlayerTick.velocitySyncs(), syncs + 1, "no packet on the ticks after it");
		helper.assertValueEqual(player.getDeltaMovement().y, -0.08, "and nothing touches the motion");
		// Facing away from the wall is not a climb either, however hard the player pushes.
		player.setYRot(0f); // forward is +Z, away from the wall
		player.setLastClientInput(PUSHING);
		player.setDeltaMovement(0, -0.08, 0);
		PlayerTick.tick(player, 6);
		assertClinging(helper, player, "pushing away from the wall");
		helper.assertValueEqual(PlayerTick.velocitySyncs(), syncs + 1, "still no velocity packet");
		helper.succeed();
	}

	/** Enemy ink drips: 1 damage every 20 ticks in survival, never below 1 health. */
	@GameTest
	public void enemyInkDripDamage(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Painter.paintFace(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), Direction.UP, PaintColor.IT);
		float before = player.getHealth();
		PlayerTick.tick(player, 20);
		helper.assertTrue(player.getHealth() <= before - 1.0f, "hurt on a damage tick, health " + player.getHealth());
		// The guard itself, isolated from vanilla's post-hit invulnerability: at 1.5 health a drip would
		// take the player below one, so it must not land at all.
		player.setHealth(1.5f);
		player.damageCooldownTime = 0;
		PlayerTick.tick(player, 40);
		helper.assertTrue(player.getHealth() == 1.5f, "guard skips the drip below one health, health " + player.getHealth());
		// At 3.0 a drip lands as normal.
		player.setHealth(3.0f);
		player.damageCooldownTime = 0;
		PlayerTick.tick(player, 60);
		helper.assertTrue(player.getHealth() == 2.0f, "drip lands with health to spare, health " + player.getHealth());
		// Never below one health: without resetting damageCooldownTime this would be vacuous, since vanilla
		// itself rejects a second hit within the previous drip's invulnerability window.
		player.setHealth(1.5f);
		player.damageCooldownTime = 0;
		PlayerTick.tick(player, 80);
		helper.assertTrue(player.getHealth() == 1.5f, "never below one health, health " + player.getHealth());
		player.setHealth(before);
		helper.succeed();
	}

	/**
	 * The shooter's ball takes two bounces before an impact spends it: two reflections off the floor,
	 * then the third hit ends it. Dropped straight down, so every reflection is a clean upward one.
	 */
	@GameTest(maxTicks = 100)
	public void paintBallBouncesTwice(GameTestHelper helper) {
		stoneFloor(helper, 5);
		PaintBall ball = new PaintBall(helper.getLevel(), gunner(helper), PaintColor.DATA, Weapon.SHOOTER_BOUNCES, 0);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 4, 2.5));
		ball.setPos(at.x, at.y, at.z);
		ball.setDeltaMovement(0, -0.8, 0);
		helper.getLevel().addFreshEntity(ball);
		// The impact ticks are not known in advance, so sample every tick: a drop in bouncesLeft is a
		// reflection, the last downward reading before the first is the incoming speed, and the first
		// upward one after it is what that bounce gave back.
		double[] falling = {0};
		double[] reflected = {0};
		int[] left = {Weapon.SHOOTER_BOUNCES};
		int[] reflections = {0};
		for (int tick = 1; tick <= 70; tick++) {
			helper.runAfterDelay(tick, () -> {
				double dy = ball.getDeltaMovement().y;
				if (ball.bouncesLeft() < left[0]) {
					left[0] = ball.bouncesLeft();
					reflections[0]++;
				}
				if (reflections[0] == 0 && dy < 0) falling[0] = dy;
				if (reflections[0] == 1 && reflected[0] == 0 && dy > 0) reflected[0] = dy;
			});
		}
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(!ball.isRemoved(), "still flying after the first impact");
			helper.assertValueEqual(ball.bouncesLeft(), Weapon.SHOOTER_BOUNCES - 1, "one of the two bounces used");
			helper.assertTrue(isPaint(helper.getBlockState(new BlockPos(2, 2, 2)), PaintColor.DATA), "first impact painted");
			// Neither reading is the instant of the bounce — the ball was still accelerating when the last
			// downward one was taken, and drag and gravity had already run when the upward one was — so
			// the ratio lands near BOUNCE_RESTITUTION rather than on it.
			helper.assertTrue(reflected[0] > 0, "the bounce sends the ball back up, dy=" + reflected[0]);
			double kept = reflected[0] / -falling[0];
			helper.assertTrue(Math.abs(kept - PaintBall.BOUNCE_RESTITUTION) < 0.10,
					"the bounce keeps about " + PaintBall.BOUNCE_RESTITUTION + " of the incoming speed, kept " + kept);
		});
		helper.runAfterDelay(75, () -> {
			helper.assertValueEqual(reflections[0], Weapon.SHOOTER_BOUNCES, "both bounces reflected the ball");
			helper.assertTrue(ball.isRemoved(), "gone after the impact that follows the last bounce");
			// The droplets each bounce threw off outlive nothing, but a stray one mid-flight would sail
			// into the next test structure.
			helper.getEntities(PaintBall.TYPE, new BlockPos(2, 2, 2), 8.0).forEach(Entity::discard);
			helper.succeed();
		});
	}

	/** Every bounce throws off a couple of droplets, and those droplets throw off none of their own. */
	@GameTest(maxTicks = 40)
	public void bounceSpawnsDroplets(GameTestHelper helper) {
		stoneFloor(helper, 5);
		PaintBall ball = new PaintBall(helper.getLevel(), gunner(helper), PaintColor.DATA, 1, 0);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 4, 2.5));
		ball.setPos(at.x, at.y, at.z);
		ball.setDeltaMovement(0, -0.8, 0);
		helper.getLevel().addFreshEntity(ball);
		// Droplets live eight ticks and are thrown at an impact tick that is not known in advance, so
		// look every tick and judge them on the first one they exist, while they still carry what the
		// bounce gave them.
		List<PaintBall> seen = new ArrayList<>();
		List<Vec3> thrownAt = new ArrayList<>();
		List<Vec3> thrownWith = new ArrayList<>();
		for (int tick = 1; tick <= 10; tick++) {
			helper.runAfterDelay(tick, () -> {
				if (!seen.isEmpty()) return;
				for (PaintBall drop : helper.getEntities(PaintBall.TYPE, new BlockPos(2, 2, 2), 8.0)) {
					if (!drop.isDroplet()) continue;
					// Read here, not at the end: a droplet is only briefly carrying what the bounce gave it.
					seen.add(drop);
					thrownAt.add(drop.position());
					thrownWith.add(drop.getDeltaMovement());
				}
			});
		}
		helper.runAfterDelay(11, () -> {
			helper.assertValueEqual(ball.bouncesLeft(), 0, "the ball has bounced");
			helper.assertTrue(!ball.isDroplet(), "the ball itself is not one of its own droplets");
			helper.assertValueEqual(seen.size(), 2, "the bounce threw off two droplets");
			Vec3 impact = helper.absoluteVec(new Vec3(2.5, 2.125, 2.5));
			for (int i = 0; i < seen.size(); i++) {
				PaintBall drop = seen.get(i);
				helper.assertValueEqual(drop.bouncesLeft(), 0, "a droplet does not bounce");
				helper.assertValueEqual(drop.splatRadius(), 0, "a droplet paints a single face");
				helper.assertTrue(thrownWith.get(i).y > 0,
						"a droplet leaves along the reflection, upward off a floor, dy=" + thrownWith.get(i).y);
				// Thrown from the bounce, not from the gun: the shooter stands at (4, 3, 4).
				double away = thrownAt.get(i).distanceTo(impact);
				helper.assertTrue(away < 1.5, "a droplet starts at the impact, " + away + " from it");
			}
			helper.getEntities(PaintBall.TYPE, new BlockPos(2, 2, 2), 8.0).forEach(Entity::discard);
			helper.succeed();
		});
	}

	/** A spatter droplet with a 12-tick lifetime splashes the floor beneath it when time runs out. */
	@GameTest
	public void dropletSplashesAfterLifetime(GameTestHelper helper) {
		stoneFloor(helper, 5);
		PaintBall drop = new PaintBall(helper.getLevel(), gunner(helper), PaintColor.DATA, 0, 12);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 3.2, 2.5));
		drop.setPos(at.x, at.y, at.z);
		drop.setDeltaMovement(0, 0.02, 0); // hovering: only the lifetime can end it
		drop.setNoGravity(true);
		helper.getLevel().addFreshEntity(drop);
		helper.runAfterDelay(16, () -> {
			helper.assertTrue(drop.isRemoved(), "droplet expired");
			helper.assertTrue(isPaint(helper.getBlockState(new BlockPos(2, 2, 2)), PaintColor.DATA), "floor under the droplet painted");
			helper.succeed();
		});
	}

	/** The blob display follows the ball and is torn down with it. */
	@GameTest
	public void blobFollowsTheBall(GameTestHelper helper) {
		PaintBall ball = new PaintBall(helper.getLevel(), gunner(helper), PaintColor.DATA, 0, 0);
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

	/**
	 * Splatoon's shot shape: straight and fast for a fixed distance, then slow and falling. The ball keeps
	 * the direction it was thrown in and drops to {@code decayed_speed} in one step, and only then does
	 * gravity touch it — which is what makes a shooter accurate up close and a paint hose at range, and
	 * what a straight line with a constant gravity under it never was.
	 */
	@GameTest
	public void aShotFliesStraightThenDecays(GameTestHelper helper) {
		// Ticked by hand, and over a shorter window than a shooter's eight blocks: the test's structure is
		// eight blocks across and walled, so a real shooter's flight would end against the wall rather
		// than at the end of its window. That the shooter's own numbers are 8 and 0.5 is
		// tuningDefaultsMatchTheEnum's business; this is the shape they drive.
		double window = 3.0;
		double launch = 1.0;
		PaintBall ball = new PaintBall(helper.getLevel(), null, PaintColor.DATA, 0, 0);
		Vec3 at = helper.absoluteVec(new Vec3(4.0, 6.0, 0.5));
		ball.setPos(at.x, at.y, at.z);
		ball.setGravity(Weapon.SHOOTER_GRAVITY);
		ball.setFlight(window, Weapon.SHOOTER_DECAYED_SPEED);
		ball.setDeltaMovement(0, 0, launch); // straight along +z, level
		helper.assertTrue(ball.isNoGravity(), "the straight stretch has no gravity under it");
		double startY = ball.getY();
		for (int i = 0; i < 3; i++) ball.tick();
		helper.assertTrue(ball.travelled() < window, "still inside the window at " + ball.travelled() + " blocks");
		helper.assertTrue(ball.isNoGravity(), "and still straight");
		helper.assertTrue(Math.abs(ball.getY() - startY) < 1.0e-6, "and dead level: " + (ball.getY() - startY));
		helper.assertTrue(ball.getDeltaMovement().z > 0.9, "and still fast: " + ball.getDeltaMovement().z);
		ball.tick();
		helper.assertTrue(ball.travelled() >= window, "past the window at " + ball.travelled() + " blocks");
		helper.assertTrue(!ball.isNoGravity(), "past the window, gravity is on");
		helper.assertTrue(ball.getDeltaMovement().z < Weapon.SHOOTER_DECAYED_SPEED + 0.05,
				"and the speed has dropped to the decayed one: " + ball.getDeltaMovement().z);
		// The window closes at the end of the tick that crossed it, so the first fall is the tick after.
		ball.tick();
		helper.assertTrue(ball.getY() < startY, "and it is falling: " + (ball.getY() - startY));
		ball.discard();
		helper.succeed();
	}

	/**
	 * The damage falloff. A shooter's ball is worth its full damage for the first few ticks of flight and
	 * then loses a slice a tick down to a floor, so the same weapon takes two shots to splat across a
	 * doorway and four across a courtyard. Read at the hit, not baked in at the throw.
	 */
	@GameTest(maxTicks = 80)
	public void damageFallsOffWithTimeInTheAir(GameTestHelper helper) {
		PaintBall ball = new PaintBall(helper.getLevel(), null, PaintColor.DATA, 0, 0);
		ball.setDamage(Weapon.SHOOTER.damage);
		ball.setDecay(Weapon.SHOOTER_DECAY_START, Weapon.SHOOTER_DECAY_PER_TICK, Weapon.SHOOTER_DECAYED_DAMAGE);
		ball.setNoGravity(true);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 6.0, 2.5));
		ball.setPos(at.x, at.y, at.z);
		helper.assertValueEqual(ball.damageNow(), Weapon.SHOOTER.damage, "a shot leaves the barrel at full damage");
		helper.getLevel().addFreshEntity(ball);
		helper.runAfterDelay(Weapon.SHOOTER_DECAY_START, () ->
				helper.assertValueEqual(ball.damageNow(), Weapon.SHOOTER.damage, "and holds it until the falloff starts"));
		helper.runAfterDelay(Weapon.SHOOTER_DECAY_START + 4, () -> {
			float expected = Weapon.SHOOTER.damage - 4 * Weapon.SHOOTER_DECAY_PER_TICK;
			helper.assertTrue(Math.abs(ball.damageNow() - expected) < 1.0e-4,
					"then a slice a tick: " + ball.damageNow() + ", expected " + expected);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertValueEqual(ball.damageNow(), Weapon.SHOOTER_DECAYED_DAMAGE, "and never below the floor");
			ball.discard();
			helper.succeed();
		});
	}

	/**
	 * Two pellets in one tick are two hits. Vanilla keeps a twenty-tick window after a hit and, for the
	 * first ten of it, applies only the <em>excess</em> over the last one — which is why a slosher's
	 * bucketful used to do the damage of a single pellet, and why a shooter firing every three ticks lost
	 * two shots in three. {@link PaintDamage} takes the window off before and after every paint hit;
	 * this is the rule that makes every weapon in the module work, so it is measured as a number rather
	 * than asserted as a comment.
	 */
	@GameTest
	public void everyPelletLandsItsOwnDamage(GameTestHelper helper) {
		stoneFloor(helper, 5);
		Player target = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(target.getScoreboardName(), team(helper, PaintColor.IT));
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		target.setPos(at.x, at.y, at.z);
		target.setHealth(target.getMaxHealth());
		float damage = 3.0f;
		float before = target.getHealth();
		// Two pellets of one slosh, landing on the same tick.
		for (int i = 0; i < 2; i++) {
			PaintBall pellet = new PaintBall(helper.getLevel(), null, PaintColor.DATA, 0, 0);
			pellet.setPos(at.x, at.y + 1.0, at.z);
			pellet.setDamage(damage);
			pellet.onHitEntity(new EntityHitResult(target));
		}
		helper.assertValueEqual(target.getHealth(), before - 2 * damage,
				"both pellets of one slosh land in full, not the second as the excess over the first");
		// And vanilla's own window is gone rather than merely shortened: nothing is left to gate a third.
		helper.assertValueEqual(target.damageCooldownTime, 0, "the cooldown is left at zero for the next one");
		PaintBall third = new PaintBall(helper.getLevel(), null, PaintColor.DATA, 0, 0);
		third.setPos(at.x, at.y + 1.0, at.z);
		third.setDamage(damage);
		third.onHitEntity(new EntityHitResult(target));
		helper.assertValueEqual(target.getHealth(), before - 3 * damage, "and a third lands too");
		// The other half of the rule, and the one a shooter lives on: three shots three ticks apart are
		// three shots, which is inside the ten ticks vanilla would have swallowed two of them in.
		target.setHealth(target.getMaxHealth());
		float volleyStart = target.getHealth();
		for (int shot = 0; shot < 3; shot++) {
			helper.runAfterDelay(shot * 3, () -> {
				PaintBall ball = new PaintBall(helper.getLevel(), null, PaintColor.DATA, 0, 0);
				ball.setPos(at.x, at.y + 1.0, at.z);
				ball.setDamage(damage);
				ball.onHitEntity(new EntityHitResult(target));
			});
		}
		helper.runAfterDelay(8, () -> {
			helper.assertValueEqual(target.getHealth(), volleyStart - 3 * damage,
					"a three-tick cadence lands every shot");
			helper.getEntities(PaintBall.TYPE, new BlockPos(2, 2, 2), 6.0).forEach(Entity::discard);
			helper.succeed();
		});
	}

	/**
	 * The splat bomb is not a contact grenade. It bounces where it is thrown and counts its fuse down on
	 * the ground, so it is a thing that can be run away from; the blast then falls off from its centre
	 * damage to its edge damage over {@code special_blast} blocks, linear in distance squared.
	 */
	@GameTest(maxTicks = 60)
	public void theSplatBombLandsBeforeItGoesOff(GameTestHelper helper) {
		stoneFloor(helper, 7);
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setXRot(90.0f); // straight down, so it lands at once and cannot wander out of the structure
		helper.assertTrue(((PaintWeapon) gun.getItem()).special(helper.getLevel(), player, gun), "the bomb is thrown");
		helper.assertValueEqual(Ink.get(gun), Ink.MAX - Weapon.SPECIAL_INK, "seventy ink of a hundred-unit tank");
		List<PaintBall> bombs = helper.getEntities(PaintBall.TYPE, new BlockPos(3, 2, 3), 6.0);
		helper.assertValueEqual(bombs.size(), 1, "one bomb");
		PaintBall bomb = bombs.get(0);
		helper.assertTrue(bomb.isBomb(), "and it is a bomb");
		helper.assertValueEqual(bomb.fuse(), -1, "which has not landed yet");
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(bomb.fuse() > 0, "it has landed and is counting down: " + bomb.fuse());
			helper.assertTrue(!bomb.isRemoved(), "and has not gone off on contact");
		});
		helper.runAfterDelay(Weapon.SPECIAL_FUSE + 8, () -> {
			helper.assertTrue(bomb.isRemoved(), "the fuse runs out and it goes off");
			helper.assertTrue(isPaint(helper.getBlockState(new BlockPos(3, 2, 3)), PaintColor.DATA),
					"leaving paint where it lay");
			helper.succeed();
		});
	}

	/**
	 * The burst bomb is the opposite bargain: it goes off on the first thing it touches, so it is thrown
	 * at a body rather than at a place. Aimed at a wall two blocks ahead it is gone within a couple of
	 * ticks, with the paint on the face it struck rather than on the floor under it, and it never starts
	 * a fuse at all.
	 */
	@GameTest(maxTicks = 40)
	public void theBurstBombBurstsOnImpact(GameTestHelper helper) {
		stoneFloor(helper, 7);
		for (int y = 2; y <= 5; y++) {
			for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
		}
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(-90.0f); // down the +x axis, into the wall
		player.setXRot(0.0f);
		helper.assertTrue(((PaintWeapon) gun.getItem()).special(helper.getLevel(), player, gun, Special.BURST_BOMB),
				"the burst bomb is thrown");
		helper.assertValueEqual(Ink.get(gun), Ink.MAX - Special.BURST_INK,
				"for its own forty ink, not the splat bomb's seventy");
		List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(3, 3, 3), 6.0);
		helper.assertValueEqual(balls.size(), 1, "one bomb");
		PaintBall bomb = balls.getFirst();
		helper.assertTrue(bomb.isBomb(), "it is a bomb");
		helper.assertValueEqual(bomb.special(), Special.BURST_BOMB, "and it knows which one");
		helper.assertValueEqual(bomb.blast(), Special.BURST_BLAST, "the burst bomb's smaller blast");
		// Well inside the splat bomb's fuse, which is the point: there is no wait on this one.
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(bomb.isRemoved(), "it bursts on the wall rather than falling to the floor");
			helper.assertValueEqual(bomb.fuse(), -1, "and never started a fuse");
			int painted = 0;
			for (int y = 2; y <= 5; y++) {
				for (int z = 1; z <= 5; z++) {
					if (isPaint(helper.getBlockState(new BlockPos(4, y, z)), PaintColor.DATA)) painted++;
				}
			}
			helper.assertTrue(painted >= 1, "leaving paint on the face it struck, got " + painted);
			helper.succeed();
		});
	}

	/**
	 * The curling bomb is thrown to cover ground: it slides along the floor painting a line under itself,
	 * comes off walls instead of stopping in corners, and bursts at the end of the slide. Put onto the
	 * floor here rather than thrown across the structure, because a slide is a dozen blocks long and the
	 * test lives in eight; the throw itself is the special dialog's test.
	 */
	@GameTest(maxTicks = 100)
	public void theCurlingBombSlidesAndPaintsALine(GameTestHelper helper) {
		stoneFloor(helper, 7);
		// A box, so a long slide stays inside an eight-block structure: it reflects off the walls.
		for (int y = 2; y <= 3; y++) {
			for (int i = 0; i <= 6; i++) {
				helper.setBlock(new BlockPos(i, y, 0), Blocks.STONE);
				helper.setBlock(new BlockPos(i, y, 6), Blocks.STONE);
				helper.setBlock(new BlockPos(0, y, i), Blocks.STONE);
				helper.setBlock(new BlockPos(6, y, i), Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();
		SpecialTuning tuning = SpecialTuning.get(Special.CURLING_BOMB);
		PaintBall bomb = new PaintBall(level, null, PaintColor.DATA, 0, tuning.intValue(SpecialTuning.Param.LIFETIME));
		bomb.setSpecial(Special.CURLING_BOMB);
		bomb.setSplatRadius(tuning.intValue(SpecialTuning.Param.RADIUS));
		bomb.setDamage(tuning.floatValue(SpecialTuning.Param.DAMAGE));
		bomb.setGravity(tuning.value(SpecialTuning.Param.GRAVITY));
		bomb.setBlast(tuning.value(SpecialTuning.Param.BLAST), tuning.floatValue(SpecialTuning.Param.EDGE_DAMAGE),
				tuning.value(SpecialTuning.Param.CORE));
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.6, 3.5));
		bomb.setPos(at.x, at.y, at.z);
		bomb.setDeltaMovement(0.6, -0.1, 0.0); // thrown low, down the +x axis
		level.addFreshEntity(bomb);
		helper.assertValueEqual(bomb.slide(), -1, "it is not sliding yet: it has not found a floor");
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(bomb.slide() > 0, "it landed and is sliding: " + bomb.slide());
			helper.assertValueEqual(bomb.getDeltaMovement().y, 0.0, "flat on the floor, not falling");
		});
		// Mid-slide, before the burst adds its own 5x5: the line is the slide's own work.
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(!bomb.isRemoved(), "it is still sliding at twenty ticks");
			helper.assertTrue(lineCells(helper) >= 3, "painting a line as it goes, got " + lineCells(helper) + " cells");
		});
		helper.runAfterDelay(tuning.intValue(SpecialTuning.Param.SLIDE_TICKS) + 20, () -> {
			helper.assertTrue(bomb.isRemoved(), "and it bursts at the end of the slide");
			helper.assertTrue(lineCells(helper) >= 3, "leaving the line behind it");
			helper.succeed();
		});
	}

	/** Painted cells on the floor of the curling bomb's box, which is the line it left. */
	private static int lineCells(GameTestHelper helper) {
		int painted = 0;
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				if (isPaint(helper.getBlockState(new BlockPos(x, 2, z)), PaintColor.DATA)) painted++;
			}
		}
		return painted;
	}

	/**
	 * The specials have a tuning sheet of their own, keyed by special rather than by weapon: what F
	 * throws belongs to the thrower, so a splat bomb is the same splat bomb out of a roller as out of a
	 * shooter. Every default is inside its own range, the three columns are the numbers each special was
	 * written with, and the two numbers only one special reads are offered to that one only.
	 */
	@GameTest
	public void theSpecialsAreTuned(GameTestHelper helper) {
		for (Special special : Special.values()) {
			SpecialTuning tuning = SpecialTuning.get(special);
			for (SpecialTuning.Param param : SpecialTuning.Param.values()) {
				// A default outside its own bounds would be a number the command could never type back.
				helper.assertTrue(param.holds(tuning.defaultValue(param)), special.commandId() + " " + param.id
						+ " default " + tuning.defaultValue(param) + " is within " + param.range());
			}
			helper.assertValueEqual(Special.byId(special.commandId()).orElse(null), special,
					special.commandId() + " answers to its own id");
			helper.assertTrue(SpecialTuning.paramList(special).contains("ink"),
					special.commandId() + " shows what it costs");
		}
		// The splat bomb's column is the numbers it has always had, in a new place.
		SpecialTuning splat = SpecialTuning.get(Special.SPLAT_BOMB);
		helper.assertValueEqual(splat.value("ink"), (double) Weapon.SPECIAL_INK, "the splat bomb's ink is unmoved");
		helper.assertValueEqual(splat.value("cooldown"), (double) Weapon.SPECIAL_COOLDOWN, "and its wait");
		helper.assertValueEqual(splat.value("refill_delay"), (double) Weapon.SPECIAL_REFILL_DELAY, "and its refill delay");
		helper.assertValueEqual(splat.value("fuse"), (double) Weapon.SPECIAL_FUSE, "and its fuse");
		helper.assertValueEqual(splat.value("blast"), Weapon.SPECIAL_BLAST, "and its blast");
		helper.assertValueEqual(splat.value("damage"), (double) Weapon.SPECIAL_DAMAGE, "and its damage");
		// And the two new columns are the cheaper, smaller bargains the table promises.
		SpecialTuning burst = SpecialTuning.get(Special.BURST_BOMB);
		helper.assertValueEqual(burst.value("ink"), (double) Special.BURST_INK, "the burst bomb costs forty");
		helper.assertValueEqual(burst.value("cooldown"), (double) Special.BURST_COOLDOWN, "and waits two seconds");
		helper.assertValueEqual(burst.value("damage"), (double) Special.BURST_DAMAGE, "25 at the centre");
		helper.assertValueEqual(burst.value("edge_damage"), (double) Special.BURST_EDGE_DAMAGE, "down to 5");
		helper.assertValueEqual(burst.value("blast"), Special.BURST_BLAST, "over two blocks");
		SpecialTuning curling = SpecialTuning.get(Special.CURLING_BOMB);
		helper.assertValueEqual(curling.value("ink"), (double) Special.CURLING_INK, "the curling bomb costs 55");
		helper.assertValueEqual(curling.value("cooldown"), (double) Special.CURLING_COOLDOWN, "and waits 70 ticks");
		helper.assertValueEqual(curling.value("slide_ticks"), (double) Special.CURLING_SLIDE_TICKS, "and slides 40");
		// The two numbers only one special has: a burst bomb has no fuse, and nothing but the curling bomb slides.
		helper.assertTrue(SpecialTuning.applies(Special.SPLAT_BOMB, SpecialTuning.Param.FUSE), "the splat bomb has a fuse");
		helper.assertFalse(SpecialTuning.applies(Special.BURST_BOMB, SpecialTuning.Param.FUSE),
				"the burst bomb has none: it is gone on contact");
		helper.assertTrue(SpecialTuning.applies(Special.CURLING_BOMB, SpecialTuning.Param.SLIDE_TICKS),
				"the curling bomb slides");
		helper.assertFalse(SpecialTuning.applies(Special.SPLAT_BOMB, SpecialTuning.Param.FRICTION),
				"and nothing else does");
		// Moved and put back, which is what /rivals tune special does to it.
		double was = curling.set(SpecialTuning.Param.SLIDE_TICKS, 12.0);
		helper.assertValueEqual(was, (double) Special.CURLING_SLIDE_TICKS, "set reports what it was");
		helper.assertValueEqual(curling.changed().size(), 1, "and the sheet knows one number has moved");
		curling.reset();
		helper.assertTrue(SpecialTuning.allDefault(), "reset puts it back");
		helper.succeed();
	}

	/**
	 * The shooter is a held-use weapon: the press starts using it and fires at once, and every tick the
	 * button stays down goes through {@code onUseTick}, which fires again as soon as the item cooldown is
	 * up. That is the whole point of the change — a vanilla client repeats a held right click only every
	 * four ticks, so a three-tick cadence is unreachable from {@code use} alone — so the test fires the
	 * use tick the number of times the cadence says and counts the balls.
	 */
	@GameTest
	public void shooterFiresFromTheUseTick(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		PaintWeapon shooter = PaintWeapon.of(Weapon.SHOOTER);
		helper.assertTrue(shooter.isHeld(), "the shooter is held, not clicked");
		helper.assertValueEqual(shooter.getUseDuration(gun, player), Weapon.CHARGE_MAX_TICKS,
				"held for as long as the button is down");
		// A client slows a player who is holding an item in use to a fifth of their speed and takes their
		// sprint away — vanilla's default for a bow, and ruinous for a weapon that is held to fire. The
		// use_effects component is what that behaviour is read from, so the held weapons carry their own.
		UseEffects effects = gun.get(DataComponents.USE_EFFECTS);
		helper.assertTrue(effects != null, "the shooter says what holding it costs");
		helper.assertTrue(effects.canSprint(), "and it does not take the sprint away");
		helper.assertTrue(effects.speedMultiplier() > 0.5f,
				"nor most of the speed: " + effects.speedMultiplier());
		UseEffects rolling = new ItemStack(PaintWeapon.of(Weapon.ROLLER)).get(DataComponents.USE_EFFECTS);
		helper.assertTrue(rolling != null && rolling.speedMultiplier() == 1.0f,
				"and a roller loses none of it: its own roll_speed is the movement rule");
		UseEffects scoped = new ItemStack(PaintWeapon.of(Weapon.CHARGER)).get(DataComponents.USE_EFFECTS);
		helper.assertTrue(scoped != null && !scoped.canSprint() && scoped.speedMultiplier() < 0.5f,
				"the charger keeps vanilla's own — no sprint and a fifth of the speed: being pinned down "
						+ "is what a scope costs, got " + scoped);
		int cooldown = WeaponTuning.get(Weapon.SHOOTER).intValue(Param.COOLDOWN);
		helper.assertTrue(cooldown < 4, "the cadence is faster than a client's four-tick repeat: " + cooldown);
		int before = Ink.get(gun);
		helper.assertTrue(shooter.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).consumesAction(),
				"the press takes");
		helper.assertTrue(player.isUsingItem(), "and starts using the item");
		helper.assertValueEqual(helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 6.0).size(), 1,
				"the press itself fires, so there is no dead frame before the first shot");
		// Use ticks inside the cooldown fire nothing; the one that finds it expired fires.
		for (int i = 0; i < cooldown - 1; i++) {
			player.getCooldowns().tick();
			shooter.onUseTick(helper.getLevel(), player, gun, Weapon.CHARGE_MAX_TICKS - i - 1);
			helper.assertValueEqual(helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 6.0).size(), 1,
					"still the one ball " + (i + 1) + " ticks in: the cooldown is the fire rate");
		}
		player.getCooldowns().tick();
		shooter.onUseTick(helper.getLevel(), player, gun, Weapon.CHARGE_MAX_TICKS - cooldown);
		List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 6.0);
		helper.assertValueEqual(balls.size(), 2, "the tick the cooldown runs out is the second shot");
		helper.assertValueEqual(Ink.get(gun), before - 2 * WeaponTuning.get(Weapon.SHOOTER).intValue(Param.INK),
				"and each shot paid for itself");
		// Letting go of a shooter does nothing at all: there is no tap gesture on it.
		helper.assertTrue(!shooter.releaseUsing(gun, helper.getLevel(), player, 0), "the release is not a shot");
		balls.forEach(Entity::discard);
		helper.succeed();
	}

	/**
	 * The roll: a strip of paint where the roller walks, and nothing where it stands. Splatoon's roller
	 * paints what it is pushed over, so a roller held down on the spot must not repaint one cell forever
	 * — and the movement is measured from two positions, so the first tick of a roll has nothing to
	 * measure and paints nothing by construction.
	 */
	@GameTest
	public void rollerPaintsAStripWhenItMoves(GameTestHelper helper) {
		stoneFloor(helper, 7);
		Roll.clearAll();
		Player player = gunner(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.ROLLER)));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		// Facing south (+z) on the floor, so the strip goes across x and lands one cell ahead in z.
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		Vec3 start = helper.absoluteVec(new Vec3(3.5, 2.0, 2.5));
		player.setPos(start.x, start.y, start.z);
		helper.assertTrue(!Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA),
				"the first tick has no previous position and paints nothing");
		helper.assertTrue(!Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA),
				"and standing still paints nothing however long the button is held");
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "a roller that has not moved has spent nothing");
		// A step forward: the strip lands on the three cells across the facing, one ahead of the feet.
		Vec3 stepped = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		player.setPos(stepped.x, stepped.y, stepped.z);
		helper.assertTrue(Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA), "a step paints");
		for (int x = 2; x <= 4; x++) {
			helper.assertTrue(isPaint(helper.getBlockState(new BlockPos(x, 2, 4)), PaintColor.DATA),
					"the strip covers " + x + ", three wide across the facing");
		}
		helper.assertTrue(!isPaint(helper.getBlockState(new BlockPos(1, 2, 4)), PaintColor.DATA),
				"and no wider than that");
		helper.assertTrue(!isPaint(helper.getBlockState(new BlockPos(3, 2, 3)), PaintColor.DATA),
				"and lands ahead of the feet, not under them");
		helper.assertTrue(Roll.isRolling(player), "rolling carries the movement bonus");
		// The trickle: one ink every roll_ink_every ticks of moving, and not a drop before.
		int every = WeaponTuning.get(Weapon.ROLLER).intValue(Param.ROLL_INK_EVERY);
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "the first rolled tick is not yet a unit of ink");
		for (int i = 1; i < every; i++) {
			Vec3 on = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5 + i * 0.1));
			player.setPos(on.x, on.y, on.z);
			Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA);
		}
		helper.assertValueEqual(Ink.get(gun), Ink.MAX - 1, "one ink every " + every + " ticks of rolling");
		Roll.stop(player);
		helper.assertTrue(!Roll.isRolling(player), "and putting it away takes the bonus off");
		helper.succeed();
	}

	/**
	 * An empty roller. The tank drains at one ink every {@code roll_ink_every} ticks of rolling — five,
	 * which empties a full tank in about twenty-five seconds of solid rolling, near enough Splatoon 1's
	 * Splat Roller; at sixteen it lasted a minute and a half, which is what "never runs out of ink" was.
	 *
	 * <p>Running dry does not end the roll. The head keeps rolling and keeps running people over, exactly
	 * as Splatoon's does — a drum is a drum whether there is paint in it or not — it simply paints
	 * nothing. It says so once, because a roller that has quietly stopped painting reads as broken, and
	 * once rather than every tick because this is every tick of a held button.
	 */
	@GameTest
	public void aRollerOutOfInkStillRollsAndSaysSoOnce(GameTestHelper helper) {
		stoneFloor(helper, 7);
		Roll.clearAll();
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.ROLLER)));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		helper.assertValueEqual(WeaponTuning.get(Weapon.ROLLER).intValue(Param.ROLL_INK_EVERY), 5,
				"a unit of ink every five ticks of rolling: a full tank is about 25 seconds of it");
		Vec3 start = helper.absoluteVec(new Vec3(3.5, 2.0, 2.5));
		player.setPos(start.x, start.y, start.z);
		Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA); // the first tick only measures
		Ink.set(gun, 0);
		Vec3 stepped = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		player.setPos(stepped.x, stepped.y, stepped.z);
		helper.assertTrue(!Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA), "an empty roller paints nothing");
		helper.assertTrue(!isPaint(helper.getBlockState(new BlockPos(3, 2, 4)), PaintColor.DATA),
				"and leaves the floor ahead of it alone");
		helper.assertTrue(Roll.isDry(player), "it has been told its tank is empty");
		helper.assertTrue(Ink.isRefilling(gun, helper.getLevel().getServer().getTickCount()),
				"and the tank has started refilling, which is what running any weapon dry does");
		helper.assertTrue(Roll.isRolling(player), "the roll itself carries on: the button is still held");
		Vec3 further = helper.absoluteVec(new Vec3(3.5, 2.0, 4.5));
		player.setPos(further.x, further.y, further.z);
		Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA);
		helper.assertTrue(Roll.isDry(player), "a second empty tick says nothing new");
		// A tank that has filled again is a roller that paints again, and one that may be told off again.
		Ink.set(gun, Ink.MAX);
		Vec3 wet = helper.absoluteVec(new Vec3(3.5, 2.0, 5.5));
		player.setPos(wet.x, wet.y, wet.z);
		helper.assertTrue(Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA), "a refilled roller paints again");
		helper.assertTrue(!Roll.isDry(player), "and is no longer holding an empty tank");
		Roll.stop(player);
		helper.succeed();
	}

	/**
	 * The spray where the head meets the ground. A roller is the one weapon whose paint lands under the
	 * player rather than in front of them, so without this nothing on the screen says the drum is on the
	 * floor at all — which is what the pose and the particles were both asked for.
	 *
	 * <p>The crumbs go through {@link Painter#burst}, which drops any viewer whose own camera the
	 * particle would spawn on; the head is {@code roll_reach} ahead of the feet, so the roller is not one
	 * of them and does see its own spray.
	 */
	@GameTest
	public void theRollerSpraysWhereItsHeadTouchesTheGround(GameTestHelper helper) {
		stoneFloor(helper, 7);
		Roll.clearAll();
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.ROLLER)));
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		// A viewer two blocks away, connected, so the particle packet has somewhere to go.
		ServerPlayer viewer = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		Vec3 watching = helper.absoluteVec(new Vec3(5.5, 2.0, 2.5));
		viewer.setPos(watching.x, watching.y, watching.z);
		helper.getLevel().addFreshEntity(viewer);
		helper.assertTrue(Roll.spray(helper.getLevel(), player, PaintColor.DATA, true) > 0,
				"the head's contact point throws paint at whoever can see it");
		// The reach is taken along the FLAT look, so looking down the barrel of the roller still puts the
		// contact point a whole roll_reach ahead of the feet rather than under them.
		player.setXRot(80.0f);
		helper.assertTrue(Roll.spray(helper.getLevel(), player, PaintColor.DATA, false) > 0,
				"a steep look still sprays a reach ahead");
		// Straight down is the one look with no flat direction at all, and nothing can be a reach ahead
		// of the feet along it. The run-over box has the same hole, and for the same reason.
		player.setXRot(90.0f);
		helper.assertValueEqual(Roll.spray(helper.getLevel(), player, PaintColor.DATA, false), 0,
				"and straight down has no direction to put the head in");
		// Over a hole there is no floor under the head, and there is nothing to throw paint off.
		helper.setBlock(new BlockPos(3, 1, 4), Blocks.AIR);
		helper.setBlock(new BlockPos(3, 0, 4), Blocks.AIR);
		player.setXRot(0.0f);
		Vec3 edge = helper.absoluteVec(new Vec3(3.5, 2.0, 2.6));
		player.setPos(edge.x, edge.y, edge.z);
		helper.assertValueEqual(Roll.spray(helper.getLevel(), player, PaintColor.DATA, false), 0,
				"and a head over a hole sprays nothing");
		viewer.discard();
		helper.succeed();
	}

	/**
	 * Running someone over. The head sweeps in front of a roller that is <em>moving</em> — a roller held
	 * down on the spot is not a wall of damage anyone who walks past is splatted by — and it lands once
	 * per victim per {@code roll_hit_cooldown} ticks, so it is a hit rather than a grinder.
	 */
	@GameTest
	public void rollerRunsOverAHostile(GameTestHelper helper) {
		stoneFloor(helper, 7);
		Roll.clearAll();
		Player player = gunner(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.ROLLER)));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		player.setYRot(0.0f); // facing +z
		player.setXRot(0.0f);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		Player victim = mockPlayer(helper, GameType.SURVIVAL);
		helper.getLevel().getScoreboard().addPlayerToTeam(victim.getScoreboardName(), team(helper, PaintColor.IT));
		Vec3 inFront = helper.absoluteVec(new Vec3(3.5, 2.0, 4.0));
		victim.setPos(inFront.x, inFront.y, inFront.z);
		// The head is an area sweep, so the victim has to be somewhere the level can find them.
		helper.getLevel().addFreshEntity(victim);
		victim.setHealth(victim.getMaxHealth());
		// The real roll is worth more than a player has, so a test that wants to be run over three times
		// has to turn it down first. That the default is a near-splat is asserted on its own below.
		helper.assertTrue(Weapon.ROLL_DAMAGE >= 20.0f, "a roll is worth most of a player: " + Weapon.ROLL_DAMAGE);
		withTuning(() -> {
			float damage = 5.0f;
			WeaponTuning.get(Weapon.ROLLER).set(Param.ROLL_DAMAGE, damage);
			float full = victim.getHealth();
			// The first tick of a roll has no previous position to measure against, so it does nothing at
			// all; and a tick that measures no movement does nothing either.
			Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA);
			Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA);
			helper.assertValueEqual(victim.getHealth(), full,
					"a roller held down on the spot runs nobody over: it is a charge, not a hazard");
			rollStep(helper, player, gun);
			helper.assertValueEqual(victim.getHealth(), full - damage, "the head runs them over for the roll's damage");
			rollStep(helper, player, gun);
			helper.assertValueEqual(victim.getHealth(), full - damage, "and not again inside its own window");
			// The window is per victim and kept here, so clearing it is the same as waiting it out.
			Roll.clearAll();
			Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA); // the measurement starts again
			rollStep(helper, player, gun);
			helper.assertValueEqual(victim.getHealth(), full - 2 * damage, "once the window is past, the head hits again");
			// And the second hit was a whole hit, not the excess over the first: that is PaintDamage's
			// doing, and it is what makes a weapon that lands more than one thing at a time work at all.
			// A teammate walks through it untouched: the roll is a weapon, not a hazard.
			helper.getLevel().getScoreboard().addPlayerToTeam(victim.getScoreboardName(), team(helper, PaintColor.DATA));
			Roll.clearAll();
			Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA);
			rollStep(helper, player, gun);
			helper.assertValueEqual(victim.getHealth(), full - 2 * damage, "a teammate is not run over");
		});
		victim.discard();
		Roll.stop(player);
		helper.succeed();
	}

	/**
	 * One tick of a roll that actually rolls: a short step along the facing, then the tick. The roll
	 * paints and runs people over only where the roller is pushed, so a test that wants either has to
	 * push it — and the step is small enough that the head keeps sweeping the same place.
	 */
	private static void rollStep(GameTestHelper helper, Player player, ItemStack gun) {
		Vec3 at = player.position();
		player.setPos(at.x, at.y, at.z + 0.1);
		Roll.tick(helper.getLevel(), player, gun, PaintColor.DATA);
	}

	/**
	 * The roller flicks on the left button, and a roll in progress is stopped for the throw. It used to be
	 * a <em>tap</em> of the right button, told from a roll by how soon the release came after the press;
	 * the two gestures now have a button each, so a release is only ever the end of a roll and a flick
	 * costs nothing but the left click.
	 */
	@GameTest
	public void rollerFlicksOnLeftClickAndStopsTheRoll(GameTestHelper helper) {
		stoneFloor(helper, 7);
		Roll.clearAll();
		Player player = gunner(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.ROLLER)));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		PaintWeapon roller = PaintWeapon.of(Weapon.ROLLER);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		Vec3 start = helper.absoluteVec(new Vec3(3.5, 2.0, 2.5));
		player.setPos(start.x, start.y, start.z);
		// A release is the end of a roll and nothing else, however short the hold was.
		roller.releaseUsing(gun, helper.getLevel(), player, Weapon.CHARGE_MAX_TICKS - 1);
		helper.assertTrue(helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 8.0).isEmpty(),
				"letting the right button go throws nothing, however briefly it was held");
		helper.assertValueEqual(Ink.get(gun), Ink.MAX, "and costs no flick's worth of ink");
		// A roll under way, and the left click in the middle of it: the flick goes and the roll is off.
		rollStep(helper, player, gun);
		rollStep(helper, player, gun);
		helper.assertTrue(Roll.isRolling(player), "rolling");
		helper.assertTrue(PaintWeapon.leftClick(player), "left click flicks, mid-roll");
		helper.assertTrue(!Roll.isRolling(player), "and the roll is stopped for the throw");
		List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 8.0);
		helper.assertValueEqual(balls.size(), Weapon.ROLLER_FLICK_BALLS, "the bucketful goes");
		helper.assertTrue(Ink.get(gun) <= Ink.MAX - WeaponTuning.get(Weapon.ROLLER).intValue(Param.INK),
				"and pays the flick's ink, got " + Ink.get(gun));
		helper.assertTrue(player.getCooldowns().isOnCooldown(gun), "and takes the flick's recovery");
		balls.forEach(Entity::discard);
		helper.succeed();
	}

	/**
	 * The shooter and the slosher have nothing on the left button: their right click is the whole weapon
	 * and their bomb is on F. A left click with one in hand spends nothing and throws nothing — which is
	 * also what keeps a player who is firing from throwing a bomb by accident.
	 */
	@GameTest
	public void leftClickDoesNothingWithTheShooterOrSlosher(GameTestHelper helper) {
		for (Weapon weapon : List.of(Weapon.SHOOTER, Weapon.SLOSHER)) {
			Player player = gunner(helper);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(weapon)));
			helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
			ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
			helper.assertTrue(!PaintWeapon.leftClick(player), "a left click with the " + weapon.commandId() + " does nothing");
			helper.assertValueEqual(Ink.get(gun), Ink.MAX, "and spends no ink");
			helper.assertTrue(helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 6.0).isEmpty(),
					"and throws nothing: the bomb is on F");
			helper.assertValueEqual(PaintWeapon.specialWait(player, helper.getLevel().getServer().getTickCount()), 0L,
					"and does not start the special's wait");
		}
		helper.succeed();
	}

	/**
	 * The roller's flick is a fan of three thrown high and slow: Splatoon's roller swing throws its drops
	 * in an arc that lands a few blocks ahead rather than along the crosshair, which here is three balls
	 * {@link Weapon#ROLLER_FAN_YAW} degrees apart pitched {@link Weapon#ROLLER_PITCH} above the view at the
	 * weapon's own low velocity, each landing as a 5×5 bucketful. What pulls the trigger is the release,
	 * and that is {@code rollerFlicksOnATapAndNotOnAHold}'s business; this is the shape of the shot.
	 */
	@GameTest
	public void rollerFlickThrowsThreeBalls(GameTestHelper helper) {
		Player player = gunner(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.ROLLER)));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		player.setXRot(0.0f);
		player.setYRot(0.0f);
		PaintWeapon.of(Weapon.ROLLER).fire(helper.getLevel(), player, PaintColor.DATA);
		List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 6.0);
		helper.assertValueEqual(balls.size(), Weapon.ROLLER_FLICK_BALLS, "three drops off the flick");
		for (PaintBall ball : balls) {
			helper.assertValueEqual(ball.weapon(), Weapon.ROLLER, "thrown by the roller");
			helper.assertValueEqual(ball.splatRadius(), Weapon.ROLLER_SPLAT_RADIUS, "a flick lands as a bucketful");
			helper.assertValueEqual(ball.bouncesLeft(), 0, "a flick's drops do not bounce");
			helper.assertValueEqual(ball.damage(), Weapon.ROLLER.damage, "the flick's damage");
			// Thrown above the crosshair: with a level view every drop leaves going up.
			helper.assertTrue(ball.getDeltaMovement().y > 0,
					"the flick arcs: " + ball.getDeltaMovement().y + " upward at a level view");
		}
		balls.forEach(Entity::discard);
		helper.succeed();
	}

	/**
	 * One click of the slosher throws two pellets in a fan — Splatoon's slosher throws two, eight degrees
	 * apart — each with a 5x5 splat radius and flat damage, and the slosher is the one weapon whose use
	 * swings the arm and the one that is still a click rather than a hold.
	 */
	@GameTest
	public void slosherThrowsTwoPelletsInAFan(GameTestHelper helper) {
		Player player = gunner(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.SLOSHER)));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		InteractionResult result = PaintWeapon.of(Weapon.SLOSHER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result.consumesAction(), "sloshes");
		helper.assertTrue(result == InteractionResult.SUCCESS_SERVER, "the slosher swings the arm, got " + result);
		List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0);
		helper.assertValueEqual(balls.size(), Weapon.SLOSHER_FAN.length, "two pellets, as Splatoon's slosher throws");
		List<Double> yaws = new ArrayList<>();
		for (PaintBall ball : balls) {
			helper.assertValueEqual(ball.splatRadius(), 2, "5x5 splat");
			helper.assertValueEqual(ball.bouncesLeft(), 0, "no bounce");
			// Flat damage: a bucketful is worth the same wherever it lands, so the falloff is off.
			helper.assertValueEqual(ball.damageNow(), Weapon.SLOSHER.damage, "a pellet's damage does not decay");
			Vec3 v = ball.getDeltaMovement();
			helper.assertTrue(v.y > 0, "the slosh is lobbed, not thrown flat: " + v.y);
			yaws.add(Math.atan2(-v.x, v.z));
		}
		for (int i = 0; i < yaws.size(); i++) {
			for (int j = i + 1; j < yaws.size(); j++) {
				helper.assertTrue(Math.abs(yaws.get(i) - yaws.get(j)) > 1.0e-4,
						"the pellets fan out: " + yaws.get(i) + " vs " + yaws.get(j));
			}
		}
		helper.assertValueEqual(Ink.get(player.getItemInHand(InteractionHand.MAIN_HAND)), Ink.MAX - Weapon.SLOSHER.inkPerShot, "ink cost");
		helper.assertTrue(player.getCooldowns().isOnCooldown(player.getItemInHand(InteractionHand.MAIN_HAND)),
				"the slosher goes on cooldown: the fire rate is the cooldown");
		// The slosher is the one weapon that stays a click rather than a hold, and the reason is its own
		// cadence: Splatoon's is 2 ticks of startup and 10 of endlag, which is slower than the four-tick
		// repeat a vanilla client sends, so nothing is lost by leaving it on the click.
		helper.assertTrue(!PaintWeapon.of(Weapon.SLOSHER).isHeld(), "the slosher is clicked, not held");
		helper.assertValueEqual(WeaponTuning.get(Weapon.SLOSHER).value("cooldown"), 12.0, "twelve ticks a slosh");
		helper.assertTrue(WeaponTuning.get(Weapon.SLOSHER).intValue(Param.COOLDOWN) > 4,
				"which is slower than a held click repeats, so the click is enough");
		// A second click inside the cadence throws nothing: the cooldown is the rate.
		InteractionResult early = PaintWeapon.of(Weapon.SLOSHER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertValueEqual(helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0).size(), balls.size(),
				"a second click inside the cadence throws nothing more, got " + early);
		balls.forEach(Entity::discard);
		helper.succeed();
	}

	/** The kit hands out one of every weapon. */
	@GameTest
	public void kitGivesEveryWeapon(GameTestHelper helper) {
		Player player = gunner(helper);
		player.getInventory().clearContent();
		int given = PaintWeapon.giveKit(player);
		helper.assertValueEqual(given, Weapon.values().length, "one of each");
		// Four, and the sprayer is not one of them: it did what the shooter does, and the roller took its
		// place in the roster.
		helper.assertValueEqual(Weapon.values().length, 4, "four weapons");
		helper.assertTrue(Weapon.byId("sprayer").isEmpty(), "the sprayer is gone");
		helper.assertTrue(Weapon.byId("roller").orElse(null) == Weapon.ROLLER, "and the roller is here");
		for (Weapon weapon : Weapon.values()) {
			helper.assertTrue(player.getInventory().contains(new ItemStack(PaintWeapon.of(weapon))), "has " + weapon.id);
			helper.assertTrue(Weapon.byId(weapon.id).orElse(null) == weapon, "byId round-trips " + weapon.id);
			helper.assertTrue(Weapon.byId(weapon.commandId()).orElse(null) == weapon,
					"byId also takes the lowercase name " + weapon.commandId());
		}
		helper.assertTrue(Weapon.byId("shooter").orElse(null) == Weapon.SHOOTER, "the shooter answers to \"shooter\"");
		helper.assertTrue(Weapon.byId("paint_gun").orElse(null) == Weapon.SHOOTER, "and still to its registry id");
		helper.assertTrue(Weapon.idList().contains("shooter") && !Weapon.idList().contains("paint_gun"),
				"the help offers \"shooter\", not the registry id: " + Weapon.idList());
		helper.assertTrue(Weapon.byId("nonesuch").isEmpty(), "an unknown id resolves to nothing");
		player.getInventory().clearContent();
		helper.succeed();
	}

	/** The charger's shot paints the floor under the scanned line and splats where it ends. */
	@GameTest
	public void chargerPaintsALineUnderTheScan(GameTestHelper helper) {
		stoneFloor(helper, 7); // floor at y=1, x/z 0..6
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(6, y, 3), Blocks.STONE); // end wall
		Player player = gunner(helper);
		ItemStack charger = new ItemStack(PaintWeapon.of(Weapon.CHARGER));
		player.setItemInHand(InteractionHand.MAIN_HAND, charger);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Vec3 at = helper.absoluteVec(new Vec3(0.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(-90f); // look +X
		player.setXRot(0f);
		boolean fired = PaintWeapon.of(Weapon.CHARGER).chargerShot(helper.getLevel(), player, charger, 1.0f);
		helper.assertTrue(fired, "full charge fires");
		int painted = 0;
		for (int x = 1; x <= 5; x++) {
			if (isPaint(helper.getBlockState(new BlockPos(x, 2, 3)), PaintColor.DATA)) painted++;
		}
		helper.assertTrue(painted >= 3, "floor painted along the line, got " + painted);
		helper.assertTrue(hasFace(helper.getBlockState(new BlockPos(5, 2, 3)), PaintColor.DATA, Direction.EAST), "end wall splatted");
		helper.assertValueEqual(Ink.get(charger), Ink.MAX - (Weapon.CHARGE_BASE_COST + Weapon.CHARGE_EXTRA_COST),
				"a full charge costs charge_ink_full");
		helper.succeed();
	}

	/**
	 * Someone standing in the line stops it: the paint lands under their feet, and the wall they were
	 * standing in front of is left clean.
	 */
	@GameTest
	public void chargerSplashesUnderAHitPlayer(GameTestHelper helper) {
		stoneFloor(helper, 7); // floor at y=1, x/z 0..6
		for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(6, y, 3), Blocks.STONE); // the wall behind them
		Player player = gunner(helper);
		ItemStack charger = new ItemStack(PaintWeapon.of(Weapon.CHARGER));
		player.setItemInHand(InteractionHand.MAIN_HAND, charger);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Vec3 at = helper.absoluteVec(new Vec3(0.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(-90f); // look +X
		player.setXRot(0f);
		// The mock-player helper builds a player but never adds it to the level, and the scan only sees
		// entities the level knows about, so this one has to be put there by hand. On DATA with the
		// shooter, so the line is stopped by a body rather than by a kill: what is asserted below is
		// where the paint went, and a target that took ten hearts and died would take its hitbox with it.
		Player target = mockPlayer(helper, GameType.SURVIVAL);
		helper.getLevel().getScoreboard().addPlayerToTeam(target.getScoreboardName(), team(helper, PaintColor.DATA));
		Vec3 stand = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		target.setPos(stand.x, stand.y, stand.z);
		helper.getLevel().addFreshEntity(target);
		boolean fired = PaintWeapon.of(Weapon.CHARGER).chargerShot(helper.getLevel(), player, charger, 1.0f);
		helper.assertTrue(fired, "full charge fires");
		helper.assertTrue(isPaint(helper.getBlockState(new BlockPos(3, 2, 3)), PaintColor.DATA),
				"the floor under the player in the way is painted");
		helper.assertTrue(!isPaint(helper.getBlockState(new BlockPos(5, 2, 3)), PaintColor.DATA),
				"the line stopped at the player: the wall behind them is clean");
		target.discard();
		helper.succeed();
	}

	/**
	 * Letting go of the scope is the shot. A vanilla client refuses to attack while it is using an item —
	 * and the scope is a spyglass in use — so a scoped charger has no left click at all; the release is
	 * the one gesture that always arrives, and it fires at whatever charge the hold built.
	 */
	@GameTest
	public void chargerFiresWhenTheScopeIsLetGo(GameTestHelper helper) {
		stoneFloor(helper, 5);
		Player player = gunner(helper);
		ItemStack charger = new ItemStack(PaintWeapon.of(Weapon.CHARGER));
		player.setItemInHand(InteractionHand.MAIN_HAND, charger);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Vec3 at = helper.absoluteVec(new Vec3(0.5, 2.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(-90f); // look +X, down the floor
		player.setXRot(0f);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		PaintWeapon.of(Weapon.CHARGER).releaseUsing(charger, helper.getLevel(), player,
				Weapon.CHARGE_MAX_TICKS - Weapon.CHARGE_FULL_TICKS);
		helper.assertTrue(Ink.get(charger) < Ink.MAX, "a full-charge release is a shot, and it costs ink: " + Ink.get(charger));
		boolean painted = false;
		for (int x = 1; x <= 4; x++) painted |= isPaint(helper.getBlockState(new BlockPos(x, 2, 2)), PaintColor.DATA);
		helper.assertTrue(painted, "and the line landed on the floor in front of the player");
		helper.succeed();
	}

	/**
	 * Left click fires the charger at whatever charge the scope has built. Right click scopes and the
	 * charge runs up while it is held; the swing packet arrives while the item is in use, which is what
	 * lets one weapon aim with one button and fire with the other.
	 */
	@GameTest(maxTicks = 120)
	public void chargerFiresOnLeftClickWhileScoped(GameTestHelper helper) {
		stoneFloor(helper, 7); // floor at y=1, x/z 0..6
		Player player = gunner(helper);
		ItemStack charger = new ItemStack(PaintWeapon.of(Weapon.CHARGER));
		player.setItemInHand(InteractionHand.MAIN_HAND, charger);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Vec3 at = helper.absoluteVec(new Vec3(0.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(-90f); // look +X
		player.setXRot(0f);
		// The charge only runs down while the player is being ticked, so this one has to be in the level.
		helper.getLevel().addFreshEntity(player);
		InteractionResult scoped = PaintWeapon.of(Weapon.CHARGER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(scoped == InteractionResult.CONSUME, "right click scopes without swinging the arm, got " + scoped);
		helper.assertTrue(player.isUsingItem(), "and the hold has started");
		helper.runAfterDelay(Weapon.CHARGE_FULL_TICKS, () -> {
			helper.assertTrue(player.getTicksUsingItem() >= Weapon.CHARGE_FULL_TICKS,
					"a full charge is held, got " + player.getTicksUsingItem());
			helper.assertTrue(PaintWeapon.leftClick(player), "left click fires");
			helper.assertTrue(!player.isUsingItem(), "and lets go of the scope");
			helper.assertValueEqual(Ink.get(player.getItemInHand(InteractionHand.MAIN_HAND)),
					Ink.MAX - (Weapon.CHARGE_BASE_COST + Weapon.CHARGE_EXTRA_COST), "a full charge costs charge_ink_full");
			int painted = 0;
			for (int x = 1; x <= 5; x++) {
				if (isPaint(helper.getBlockState(new BlockPos(x, 2, 3)), PaintColor.DATA)) painted++;
			}
			helper.assertTrue(painted >= 3, "a line of paint down the floor, got " + painted);
			player.discard();
			helper.succeed();
		});
	}

	/**
	 * A charger shot refused for an empty tank leaves the player still aiming. Letting go of the scope
	 * for a shot that never happened would throw away the charge as well as the ink.
	 */
	@GameTest
	public void chargerKeepsTheScopeWhenOutOfInk(GameTestHelper helper) {
		Player player = gunner(helper);
		ItemStack charger = new ItemStack(PaintWeapon.of(Weapon.CHARGER));
		player.setItemInHand(InteractionHand.MAIN_HAND, charger);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Ink.set(charger, 0);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		helper.assertTrue(player.isUsingItem(), "scoped");
		helper.assertTrue(!PaintWeapon.leftClick(player), "no shot on a tank that cannot cover it");
		helper.assertTrue(player.isUsingItem(), "and the scope is still up");
		helper.succeed();
	}

	/** Left click without the scope is a snap shot: the minimum charge, so the minimum ink and range. */
	@GameTest
	public void chargerSnapShotWhenUnscoped(GameTestHelper helper) {
		stoneFloor(helper, 7);
		Player player = gunner(helper);
		ItemStack charger = new ItemStack(PaintWeapon.of(Weapon.CHARGER));
		player.setItemInHand(InteractionHand.MAIN_HAND, charger);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		Vec3 at = helper.absoluteVec(new Vec3(0.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(-90f); // look +X
		player.setXRot(0f);
		helper.assertTrue(!player.isUsingItem(), "not scoped");
		helper.assertTrue(PaintWeapon.leftClick(player), "left click still fires");
		helper.assertValueEqual(Ink.get(charger), Ink.MAX - Weapon.CHARGE_BASE_COST, "a snap shot costs the base ink only");
		int painted = 0;
		for (int x = 1; x <= 5; x++) {
			if (isPaint(helper.getBlockState(new BlockPos(x, 2, 3)), PaintColor.DATA)) painted++;
		}
		helper.assertTrue(painted >= 1, "and still paints, got " + painted);
		helper.succeed();
	}

	/**
	 * F is the special: a splat bomb on everything but the charger — one slow, fat, no-bounce ball carrying
	 * the wide splat radius and the blast, for the special's own ink. The swap-hands key reaches the server
	 * as a player action, the mixin hands it to {@link PaintWeapon#swapHands}, and a paint weapon in the
	 * main hand answers with the bomb — and only with the bomb: the packet is cancelled, so nothing moves
	 * between the hands. A player holding anything else is left to vanilla, which is the swap.
	 */
	@GameTest
	public void theSwapHandsKeyThrowsTheSpecial(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		long now = helper.getLevel().getServer().getTickCount();
		helper.assertTrue(PaintWeapon.swapHands(player), "F with a paint weapon is ours, so the packet is cancelled");
		List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0);
		helper.assertValueEqual(balls.size(), 1, "one bomb");
		PaintBall bomb = balls.getFirst();
		helper.assertTrue(bomb.isBomb(), "it is a bomb: it goes off where it lands");
		helper.assertValueEqual(bomb.blast(), Weapon.SPECIAL_BLAST, "the blast radius");
		helper.assertValueEqual(bomb.splatRadius(), Weapon.SPECIAL_RADIUS, "the wide splat radius");
		helper.assertValueEqual(bomb.damage(), Weapon.SPECIAL_DAMAGE, "the special's damage");
		helper.assertValueEqual(bomb.lifetime(), Weapon.SPECIAL_LIFETIME, "the special's lifetime");
		helper.assertValueEqual(bomb.bouncesLeft(), 0, "a bomb does not bounce");
		helper.assertValueEqual(bomb.blobScale(), Weapon.SPECIAL_SCALE, "and it is a big blob");
		helper.assertTrue(bomb.getDeltaMovement().y > 0, "lobbed above the crosshair, got " + bomb.getDeltaMovement());
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		helper.assertTrue(!player.getCooldowns().isOnCooldown(gun),
				"the special's wait is not the gun's cooldown: the trigger is still free");
		helper.assertValueEqual(Ink.get(gun), Ink.MAX - Weapon.SPECIAL_INK, "the special's ink");
		helper.assertValueEqual(PaintWeapon.specialWait(player, now), (long) Weapon.SPECIAL_COOLDOWN, "and its own wait");
		// The whole point of cancelling: the hands are where they were.
		helper.assertTrue(gun.getItem() instanceof PaintWeapon, "the gun stays in the main hand");
		helper.assertValueEqual(player.getItemInHand(InteractionHand.OFF_HAND).getItem(), Items.SHIELD,
				"and the off hand is untouched");
		Player bare = mockPlayer(helper, GameType.SURVIVAL);
		bare.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		helper.assertTrue(!PaintWeapon.swapHands(bare), "F with anything else in hand is vanilla's swap");
		balls.forEach(Entity::discard);
		helper.succeed();
	}

	/**
	 * The charger carries no bomb: its charge is its special, it reads none of the {@code special_*}
	 * tuning, and F with one in hand says so rather than quietly doing nothing.
	 */
	@GameTest
	public void theChargerHasNoBombOnF(GameTestHelper helper) {
		Player player = gunner(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PaintWeapon.of(Weapon.CHARGER)));
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack charger = player.getItemInHand(InteractionHand.MAIN_HAND);
		long now = helper.getLevel().getServer().getTickCount();
		helper.assertTrue(PaintWeapon.swapHands(player), "F is still ours, so the hands do not swap");
		helper.assertTrue(helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 6.0).isEmpty(), "but no bomb");
		helper.assertValueEqual(Ink.get(charger), Ink.MAX, "and nothing spent");
		helper.assertValueEqual(PaintWeapon.specialWait(player, now), 0L, "and no wait started");
		helper.succeed();
	}

	/**
	 * Every weapon names its own buttons in its tooltip, because the control table is the one thing a
	 * player cannot work out by trying: right click is obvious, a left click that flicks is not and F is
	 * not at all.
	 */
	@GameTest
	public void theWeaponsTellTheirControls(GameTestHelper helper) {
		for (Weapon weapon : Weapon.values()) {
			List<Component> tooltip = new ArrayList<>();
			ItemStack stack = new ItemStack(PaintWeapon.of(weapon));
			PaintWeapon.of(weapon).modifyClientTooltip(tooltip, stack, null);
			helper.assertValueEqual(tooltip.size(), 1, weapon.commandId() + " says one line");
			String line = tooltip.getFirst().getString().toLowerCase(java.util.Locale.ROOT);
			helper.assertTrue(line.contains("right click"), weapon.commandId() + " names its right click: " + line);
			// The two weapons with a second trigger say which button it is; the two with a bomb say F.
			boolean second = weapon == Weapon.ROLLER || weapon == Weapon.CHARGER;
			helper.assertValueEqual(line.contains("left click"), second, weapon.commandId() + " on the left button: " + line);
			helper.assertValueEqual(line.contains("f for the bomb"), weapon != Weapon.CHARGER,
					weapon.commandId() + " on F: " + line);
		}
		helper.succeed();
	}

	/** The special has a wait of its own and a price of its own, and refuses when either is not met. */
	@GameTest
	public void specialRespectsItsOwnCooldownAndInk(GameTestHelper helper) {
		PaintWeapon shooter = PaintWeapon.of(Weapon.SHOOTER);
		ServerLevel level = helper.getLevel();
		long now = level.getServer().getTickCount();
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack gun = player.getItemInHand(InteractionHand.MAIN_HAND);
		helper.assertTrue(shooter.special(level, player, gun), "the first bomb goes");
		helper.assertValueEqual(PaintWeapon.specialWait(player, now), (long) Weapon.SPECIAL_COOLDOWN, "the wait starts");
		helper.assertTrue(!shooter.special(level, player, gun), "a second bomb inside the wait is refused");
		helper.assertValueEqual(Ink.get(gun), Ink.MAX - Weapon.SPECIAL_INK, "and costs nothing extra");
		helper.assertValueEqual(helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0).size(), 1, "still one bomb");
		helper.assertValueEqual(PaintWeapon.specialWait(player, now + Weapon.SPECIAL_COOLDOWN), 0L, "ready again after the wait");
		// A tank that cannot cover the bomb is refused too, and starts the refill the way a shot does.
		Player poor = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(poor.getScoreboardName(), team(helper, PaintColor.DATA));
		ItemStack low = poor.getItemInHand(InteractionHand.MAIN_HAND);
		Ink.set(low, Weapon.SPECIAL_INK - 1);
		helper.assertTrue(!shooter.special(level, poor, low), "no bomb on a tank that cannot cover it");
		helper.assertTrue(poor.getCooldowns().isOnCooldown(low), "the refill holds the gun instead");
		helper.assertValueEqual(PaintWeapon.specialWait(poor, now), 0L, "and the special is still ready");
		helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0).forEach(Entity::discard);
		helper.succeed();
	}

	/**
	 * A left click with a paint weapon in hand never breaks a block or hits an entity: both Fabric
	 * attack callbacks refuse the vanilla action, so the arena survives the special being thrown at it.
	 * A player holding something else is left alone.
	 */
	@GameTest
	public void attackDoesNotBreakBlocks(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
		Player armed = gunner(helper); // on no team, so the click itself throws nothing
		BlockPos stone = helper.absolutePos(new BlockPos(2, 2, 2));
		InteractionResult onBlock = AttackBlockCallback.EVENT.invoker()
				.interact(armed, helper.getLevel(), InteractionHand.MAIN_HAND, stone, Direction.UP);
		helper.assertTrue(onBlock == InteractionResult.FAIL, "a paint weapon does not break blocks, got " + onBlock);
		Player target = mockPlayer(helper, GameType.SURVIVAL);
		InteractionResult onEntity = AttackEntityCallback.EVENT.invoker()
				.interact(armed, helper.getLevel(), InteractionHand.MAIN_HAND, target, null);
		helper.assertTrue(onEntity == InteractionResult.FAIL, "and does not melee, got " + onEntity);
		Player bare = mockPlayer(helper, GameType.SURVIVAL);
		InteractionResult barehanded = AttackBlockCallback.EVENT.invoker()
				.interact(bare, helper.getLevel(), InteractionHand.MAIN_HAND, stone, Direction.UP);
		helper.assertTrue(barehanded == InteractionResult.PASS, "an empty hand is vanilla's business, got " + barehanded);
		Player carrier = mockPlayer(helper, GameType.SURVIVAL);
		carrier.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		InteractionResult withStone = AttackBlockCallback.EVENT.invoker()
				.interact(carrier, helper.getLevel(), InteractionHand.MAIN_HAND, stone, Direction.UP);
		helper.assertTrue(withStone == InteractionResult.PASS, "and so is a block in hand, got " + withStone);
		helper.succeed();
	}



	/** Spec §2: every server paint state has its own client state, and none of them shows water or nothing. */
	@GameTest
	public void paintStatesAreUniqueAndSafe(GameTestHelper helper) {
		List<BlockState> all = PaintStates.all();
		helper.assertValueEqual(all.size(), PaintColor.values().length * (PaintStates.CONNECTED_PER_COLOR + PaintStates.SPLAT_PER_COLOR), "client states in use");
		helper.assertValueEqual(new HashSet<>(all).size(), all.size(), "client states are distinct");
		for (BlockState state : all) {
			helper.assertTrue(PaintStates.DONORS.contains(state.getBlock()), "a donor block: " + state);
			helper.assertTrue(state.getBlock() != Blocks.GLOW_LICHEN, "glow lichen is not a donor: " + state);
			// No paint cell may sit on a state a vanilla client does anything with: no water, no light,
			// no collision, no animateTick that emits. PaintStates.inert is the rule; this is it held to
			// from the outside, on every state the table actually hands out.
			helper.assertTrue(PaintStates.inert(state), "inert on a vanilla client: " + state);
			helper.assertTrue(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty(),
					"no collision, so a client never stands a notch above the paint: " + state);
			helper.assertTrue(state.getFluidState().isEmpty(), "no fluid, so the client draws no water in the cell: " + state);
			// A donor's whole blockstate file is overridden, so every block of that kind in the world draws
			// as paint. Paint Splat Town is built with redstone in it, which is why wire is not a donor.
			helper.assertFalse(state.getBlock() == Blocks.REDSTONE_WIRE,
					"redstone wire is not a donor: the arena is built with it: " + state);
			helper.assertValueEqual(state.getLightEmission(), 0, "unlit: " + state);
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				helper.assertFalse(state.getValue(BlockStateProperties.WATERLOGGED), "never waterlogged: " + state);
			}
			// The all-faces-false multiface state is deliberately in use: the pack replaces the donor's
			// whole blockstate file, so what the client draws is our quad and not vanilla's union of face
			// slabs. Only the state's outline shape is empty, and adventure mode never draws one.
		}
		// The same request always gives the same state, and popcount-1 splat masks fold into connected.
		helper.assertValueEqual(PaintStates.connected(PaintColor.DATA, Direction.UP, 5), PaintStates.connected(PaintColor.DATA, Direction.UP, 5), "deterministic");
		helper.assertValueEqual(PaintStates.splat(PaintColor.IT, 1 << Direction.NORTH.ordinal()), PaintStates.connected(PaintColor.IT, Direction.NORTH, 0), "single-face mask is a connected state");
		// entry() inverts connected()/splat() for every colour, face/bits and every splat mask.
		for (PaintColor color : PaintColor.values()) {
			for (Direction face : Direction.values()) {
				for (int bits = 0; bits < 16; bits++) {
					PaintStates.Entry expected = new PaintStates.Entry(color, face, bits, 1 << face.ordinal());
					helper.assertValueEqual(PaintStates.entry(PaintStates.connected(color, face, bits)), expected, "connected round-trip: " + color + " " + face + " " + bits);
				}
			}
			for (int mask = 1; mask < 64; mask++) {
				if (Integer.bitCount(mask) < 2) continue;
				PaintStates.Entry expected = new PaintStates.Entry(color, null, 0, mask);
				helper.assertValueEqual(PaintStates.entry(PaintStates.splat(color, mask)), expected, "splat round-trip: " + color + " " + mask);
			}
		}
		helper.succeed();
	}

	/**
	 * Every cell kind comes from the donor that kind is for, and there are enough states to go round.
	 * The outline does not come into it — Rivals is adventure mode, so the targeted-block highlight a
	 * borrowed state's shape would draw never appears. What does come into it is what else is in the
	 * world: a donor's whole blockstate file is overridden, so every block of that kind anybody places
	 * draws as paint. Redstone wire used to hold the floors and ceilings and is now no donor at all,
	 * because Paint Splat Town is built with redstone in it; the four buttons that replaced it were
	 * picked for being the ones nobody builds with.
	 */
	@GameTest
	public void paintStatesComeFromTheirOwnDonor(GameTestHelper helper) {
		int colors = PaintColor.values().length;
		helper.assertValueEqual(colors, 2, "two colours, one multiface donor each");
		// Wall cells: the colour's own multiface donor, and the whole of its pool — four attach
		// directions of sixteen bit patterns is exactly the 64 non-waterlogged states one has.
		Set<BlockState> walls = new HashSet<>();
		for (PaintColor color : PaintColor.values()) {
			Block donor = color == PaintColor.DATA ? Blocks.SCULK_VEIN : Blocks.RESIN_CLUMP;
			int usable = 0;
			for (BlockState state : donor.getStateDefinition().getPossibleStates()) {
				if (!state.hasProperty(BlockStateProperties.WATERLOGGED) || !state.getValue(BlockStateProperties.WATERLOGGED)) usable++;
			}
			helper.assertTrue(usable >= 4 * PaintArt.BITS, donor + " has " + usable + " usable states for " + (4 * PaintArt.BITS) + " wall cells");
			for (Direction wall : Direction.Plane.HORIZONTAL) {
				for (int bits = 0; bits < PaintArt.BITS; bits++) {
					BlockState state = PaintStates.connected(color, wall, bits);
					helper.assertValueEqual(state.getBlock(), donor, color + " " + wall + " " + bits + " is that colour's own multiface donor");
					helper.assertTrue(walls.add(state), "each wall cell has a state of its own: " + state);
				}
			}
		}
		helper.assertValueEqual(walls.size(), colors * 4 * PaintArt.BITS, "wall states in use");
		// Floors and ceilings: the four flat donors, which are buttons nobody builds an arena out of.
		Set<BlockState> flats = new HashSet<>();
		for (PaintColor color : PaintColor.values()) {
			for (Direction face : List.of(Direction.DOWN, Direction.UP)) {
				for (int bits = 0; bits < PaintArt.BITS; bits++) {
					BlockState state = PaintStates.connected(color, face, bits);
					helper.assertTrue(FLAT_DONORS.contains(state.getBlock()),
							color + " " + face + " " + bits + " comes from a flat donor, not " + state);
					helper.assertTrue(flats.add(state), "each floor cell has a state of its own: " + state);
				}
			}
		}
		helper.assertValueEqual(flats.size(), colors * 2 * PaintArt.BITS, "floor and ceiling states in use");
		// Splat masks: the pale moss carpet, then whatever the buttons had left after the floors.
		Set<BlockState> splats = new HashSet<>();
		for (PaintColor color : PaintColor.values()) {
			for (int mask = 1; mask < 64; mask++) {
				if (Integer.bitCount(mask) < 2) continue;
				BlockState state = PaintStates.splat(color, mask);
				helper.assertTrue(SPLAT_DONORS.contains(state.getBlock()),
						color + " splat " + mask + " comes from a splat donor, not " + state);
				splats.add(state);
			}
		}
		helper.assertValueEqual(splats.size(), colors * PaintStates.SPLAT_PER_COLOR, "splat states in use");
		// The carpet is spent outright — it is most of the reason the splats fit at all, so if it stops
		// lending what it lends now the table is short and says so at start-up.
		for (Block donor : List.of(Blocks.PALE_MOSS_CARPET)) {
			int inert = 0;
			int used = 0;
			for (BlockState state : donor.getStateDefinition().getPossibleStates()) {
				if (!PaintStates.inert(state)) continue;
				inert++;
				if (splats.contains(state)) used++;
			}
			helper.assertValueEqual(used, inert, donor + " lends every one of its " + inert + " inert states to the splats");
		}
		// Pale moss carpet only lends the base=false half: MossyCarpetBlock.getCollisionShape gives the
		// base=true states a real box, and a client standing a notch above the paint fights the server.
		int collides = 0;
		for (BlockState state : Blocks.PALE_MOSS_CARPET.getStateDefinition().getPossibleStates()) {
			if (!state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) collides++;
		}
		helper.assertTrue(collides > 0, "pale moss carpet still has colliding states, so the base filter still earns its keep");
		for (BlockState state : splats) {
			helper.assertTrue(state.getBlock() != Blocks.PALE_MOSS_CARPET || !state.getValue(MossyCarpetBlock.BASE),
					"no splat sits on a carpet base: " + state);
		}
		// The particle state is a wall cell, so it is multiface and vanilla's BlockColors leaves it alone.
		for (PaintColor color : PaintColor.values()) {
			BlockState particles = PaintStates.particles(color);
			helper.assertTrue(particles.getBlock() instanceof MultifaceBlock, color + " particle state is multiface, not " + particles);
			helper.assertValueEqual(PaintStates.entry(particles).color(), color, "and it is that colour's own");
		}
		helper.succeed();
	}

	/**
	 * The coexistence rule, pinned. polymer-blocks' {@code BlockModelType} pools hand vanilla states
	 * out to any mod that asks — moredyes' carpets sit in {@code TRIPWIRE_FLAT} — and whoever asked
	 * then writes that block's whole {@code minecraft/blockstates/*.json} into the served pack. Two
	 * packs cannot own one file: on the minigame server, which ships moredyes and Rivals together,
	 * tripwire was double-booked and every floor cell drew nothing while the wall paint carried on.
	 * So no Rivals donor may be a block Polymer pools.
	 *
	 * <p>Polymer's side of that is derived rather than pinned: {@code DefaultModelData.USABLE_STATES}
	 * is the table {@code BlockModelType} is served from, so the blocks come straight out of it. The
	 * tripwire assertion is the canary — if Polymer ever moves that table the derived set comes out
	 * empty, and this test says so instead of passing on nothing. For the record, the blocks in it as
	 * of polymer-blocks 0.18.0+26.3-rc-1 are tripwire; a long list of full blocks (stone, the plank
	 * sets, wool, note block, target, dispenser, dropper, beehive, creaking heart and so on); leaves,
	 * saplings, the mangrove propagule, cave/twisting/weeping vines, kelp, sugar cane and cactus;
	 * farmland; slabs, stairs, trapdoors, doors, shelves, fence gates, beds, scaffolding, copper
	 * chains, copper lanterns and copper bars; fire and campfires; sculk sensors; the weighted
	 * pressure plates; the lightning rod; and player heads.
	 */
	@GameTest
	public void donorsAreOutsidePolymersBlockPools(GameTestHelper helper) {
		Set<Block> pooled = new HashSet<>();
		for (List<BlockState> states : DefaultModelData.USABLE_STATES.values()) {
			for (BlockState state : states) pooled.add(state.getBlock());
		}
		DefaultModelData.SPECIAL_REMAPS.forEach((from, to) -> {
			pooled.add(from.getBlock());
			pooled.add(to.getBlock());
		});
		helper.assertTrue(pooled.contains(Blocks.TRIPWIRE), "polymer-blocks still pools tripwire; read " + pooled.size() + " blocks");
		helper.assertTrue(pooled.size() >= 20, "polymer's pools cover " + pooled.size() + " blocks, expected dozens");
		helper.assertFalse(PaintStates.DONORS.contains(Blocks.TRIPWIRE), "tripwire is not a donor any more");
		// The donors the splats and then the floors moved onto, called out by name: they are the newest,
		// so they are the ones most likely to collide with a pool Polymer grows later.
		helper.assertFalse(pooled.contains(Blocks.PALE_MOSS_CARPET), "pale moss carpet is outside Polymer's pools");
		for (Block button : FLAT_DONORS) {
			helper.assertFalse(pooled.contains(button),
					BuiltInRegistries.BLOCK.getKey(button) + " is outside Polymer's pools");
		}
		helper.assertFalse(PaintStates.DONORS.contains(Blocks.REDSTONE_WIRE),
				"redstone wire is not a donor any more: the arena is built with it");
		for (Block donor : PaintStates.DONORS) {
			helper.assertFalse(pooled.contains(donor),
					BuiltInRegistries.BLOCK.getKey(donor) + " is in a Polymer block pool, so both packs would write its blockstate file");
		}
		helper.succeed();
	}

	/**
	 * Every ink burst in the module is made of block crumbs carrying a paint client state, so the client
	 * pulls the sprite off that state's model {@code particle} texture and the crumbs come out in the
	 * team colour. The state has to be one of ours and it has to be multiface: a redstone-wire-backed
	 * state would go through vanilla's colour provider and come out dark red instead.
	 */
	@GameTest
	public void inkCrumbsWearTheTeamColour(GameTestHelper helper) {
		Set<BlockState> paintStates = new HashSet<>(PaintStates.all());
		Set<BlockState> seen = new HashSet<>();
		for (PaintColor color : PaintColor.values()) {
			BlockParticleOption crumbs = Painter.crumbs(color);
			helper.assertValueEqual(crumbs.getType(), ParticleTypes.BLOCK, "a block-break crumb for " + color);
			BlockState state = crumbs.getState();
			helper.assertTrue(paintStates.contains(state), color + " crumbs carry one of our client states, not " + state);
			helper.assertTrue(state.getBlock() instanceof MultifaceBlock, color + " crumbs carry an untinted multiface state, not " + state);
			helper.assertValueEqual(PaintStates.entry(state).color(), color, "and it is that colour's own state");
			helper.assertTrue(seen.add(state), "each colour has its own crumb state");
			helper.assertValueEqual(Painter.crumbs(color), crumbs, "cached per colour");
		}
		helper.succeed();
	}

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

	/**
	 * One face per connected cell, popcount per splat cell — read off the three cells this test paints
	 * rather than off a level-global before/after delta. {@link PaintTally#count} folds in every display
	 * quad in the level, so a delta here answers for whatever else the structure happens to hold; the
	 * face masks of our own cells answer only for us.
	 */
	@GameTest
	public void tallyCountsConnectedAndSplat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PaintTally tally = new PaintTally();
		for (int x = 1; x <= 3; x++) helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
		for (int x = 1; x <= 3; x++) Painter.paintFace(level, helper.absolutePos(new BlockPos(x, 1, 2)), Direction.UP, PaintColor.DATA);
		Painter.paintFace(level, helper.absolutePos(new BlockPos(2, 2, 1)), Direction.SOUTH, PaintColor.DATA);
		int faces = 0;
		for (int x = 1; x <= 3; x++) {
			BlockPos cell = helper.absolutePos(new BlockPos(x, 2, 2));
			tally.track(cell);
			BlockState state = level.getBlockState(cell);
			helper.assertTrue(state.getBlock() instanceof Paint, "paint at " + cell + ", not " + state);
			Paint paint = (Paint) state.getBlock();
			helper.assertValueEqual(paint.color(), PaintColor.DATA, "DATA paint at " + cell);
			faces += Integer.bitCount(paint.faceMask(state));
		}
		helper.assertValueEqual(faces, 4, "three floor faces plus the wall face on the middle cell");
		helper.assertValueEqual(tally.cells(), 3, "three cells tracked");
		helper.assertTrue(tally.count(level).get(PaintColor.DATA) >= faces, "the tally sees at least our own faces");
		helper.succeed();
	}

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

	/** Every client state in use has a blockstate variant; every (colour, bits) has a texture; the marker alpha is on every texel. */
	@GameTest
	public void packCoversEveryPaintState(GameTestHelper helper) throws IOException {
		Map<String, byte[]> files = PaintArt.packFiles();
		for (PaintColor color : PaintColor.values()) {
			for (int bits = 0; bits < 16; bits++) {
				byte[] png = files.get("assets/rivals-paint/textures/block/" + PaintArt.textureName(color, bits) + ".png");
				helper.assertTrue(png != null, "texture for " + color + " bits " + bits);
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
				int argb = image.getRGB(0, 0);
				helper.assertValueEqual(argb >>> 24, PaintArt.PAINT_ALPHA, "marker alpha");
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
		// And nothing for tripwire: that file belongs to whoever asked Polymer's pool for it.
		helper.assertFalse(files.containsKey("assets/minecraft/blockstates/tripwire.json"), "the pack leaves tripwire.json alone");
		for (Block donor : SPLAT_DONORS) {
			String path = "assets/minecraft/blockstates/" + BuiltInRegistries.BLOCK.getKey(donor).getPath() + ".json";
			helper.assertTrue(files.containsKey(path), "the splat donors get their own override: " + path);
		}
		helper.assertFalse(files.containsKey("assets/minecraft/blockstates/stone_button.json"),
				"the stone button is not a donor any more: the arena has stone buttons on it");
		helper.succeed();
	}

	/**
	 * Run something with the tuning to itself. Every weapon's every parameter is noted down first and put
	 * back afterwards, whatever happens in between, because the live tuning is one table for the whole
	 * server and the tests in a batch tick side by side: a test that left the shooter at half velocity
	 * would be re-tuning every other test's gun. The body has to be synchronous for the same reason —
	 * nothing here may span a tick, or another test's shot lands inside the window.
	 */
	private static void withTuning(Runnable body) {
		Map<Weapon, Map<Param, Double>> before = new EnumMap<>(Weapon.class);
		for (Weapon weapon : Weapon.values()) {
			Map<Param, Double> values = new EnumMap<>(Param.class);
			for (Param param : Param.values()) values.put(param, WeaponTuning.get(weapon).value(param));
			before.put(weapon, values);
		}
		try {
			body.run();
		} finally {
			for (Weapon weapon : Weapon.values()) {
				before.get(weapon).forEach((param, value) -> WeaponTuning.get(weapon).set(param, value));
			}
		}
	}

	/**
	 * The tuning starts where the weapons were written: every parameter's default is the constant the
	 * fire modes used to read, so turning the tuning on changed nothing about how anything shoots.
	 */
	@GameTest
	public void tuningDefaultsMatchTheEnum(GameTestHelper helper) {
		withTuning(() -> {
			WeaponTuning.resetAll();
			for (Weapon weapon : Weapon.values()) {
				WeaponTuning tuning = WeaponTuning.get(weapon);
				helper.assertValueEqual(tuning.value("velocity"), (double) weapon.velocity, weapon.commandId() + " velocity");
				helper.assertValueEqual(tuning.value("spread"), (double) weapon.inaccuracy, weapon.commandId() + " spread");
				helper.assertValueEqual(tuning.value("ink"), (double) weapon.inkPerShot, weapon.commandId() + " ink");
				helper.assertValueEqual(tuning.value("cooldown"), (double) weapon.cooldownTicks, weapon.commandId() + " cooldown");
				helper.assertValueEqual(tuning.value("kick"), (double) weapon.kickPitch, weapon.commandId() + " kick");
				helper.assertValueEqual(tuning.value("damage"), (double) weapon.damage, weapon.commandId() + " damage");
				helper.assertValueEqual(tuning.value("restitution"), PaintBall.BOUNCE_RESTITUTION, weapon.commandId() + " restitution");
				// A default outside its own bounds would be a number the command could never type back.
				for (Param param : Param.values()) {
					helper.assertTrue(param.holds(tuning.defaultValue(param)),
							weapon.commandId() + " " + param.id + " default " + tuning.defaultValue(param) + " is within " + param.range());
				}
			}
			// The ball weapons: the numbers each arm of fire used to spell out for itself.
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("bounces"), (double) Weapon.SHOOTER_BOUNCES, "shooter bounces");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("count"), 1.0, "shooter fires one ball");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("gravity"), Weapon.SHOOTER_GRAVITY, "shooter gravity");
			// The straight-shot window, which is the shape of a Splatoon weapon more than any other number.
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("straight_blocks"), Weapon.SHOOTER_STRAIGHT_BLOCKS,
					"shooter straight-shot window");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("decayed_speed"), Weapon.SHOOTER_DECAYED_SPEED,
					"shooter speed after it");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("decay_start"), (double) Weapon.SHOOTER_DECAY_START,
					"shooter falloff start");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("decay_per_tick"), (double) Weapon.SHOOTER_DECAY_PER_TICK,
					"shooter falloff rate");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("decayed_damage"), (double) Weapon.SHOOTER_DECAYED_DAMAGE,
					"shooter damage floor");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("spread_air"), (double) Weapon.SHOOTER_SPREAD_AIR,
					"a shooter fired in the air scatters twice as wide");
			// A weapon that says nothing about falloff has none: the floor is the launch damage.
			helper.assertValueEqual(WeaponTuning.get(Weapon.SLOSHER).value("decay_per_tick"), 0.0, "a bucketful does not decay");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SLOSHER).value("decayed_damage"), (double) Weapon.SLOSHER.damage,
					"so its floor is its damage");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SLOSHER).value("straight_blocks"), 0.0,
					"and it falls from the moment it leaves");
			// The post-shot wait before own paint refills, per weapon — and every weapon has to be able to
			// show and take it. The charger's arm of applies() is a whitelist, so a parameter that every
			// weapon reads has to be named in it; refill_delay was not, and a charger's was a number the
			// command would not show and the config file would have thrown away on the next save.
			for (Weapon weapon : Weapon.values()) {
				helper.assertValueEqual(WeaponTuning.get(weapon).value("refill_delay"), (double) weapon.refillDelay,
						weapon.commandId() + " refill delay");
				for (Param shared : WeaponTuning.everyWeapon()) {
					helper.assertTrue(WeaponTuning.applies(weapon, shared),
							weapon.commandId() + " must show and take " + shared.id + ": every weapon reads it");
				}
			}
			// The special's numbers are no longer a weapon's: what F throws is the thrower's own pick, so
			// they live in SpecialTuning, keyed by special, and no weapon shows or takes one.
			for (Param param : Param.values()) {
				helper.assertFalse(param.id.startsWith("special_"),
						"the special's numbers moved to /rivals tune special: " + param.id);
			}
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("splat_radius"), (double) Painter.RADIUS, "shooter splat radius");
			helper.assertValueEqual(WeaponTuning.get(Weapon.ROLLER).value("count"), (double) Weapon.ROLLER_FLICK_BALLS, "roller flick drops");
			helper.assertValueEqual(WeaponTuning.get(Weapon.ROLLER).value("fan_yaw"), (double) Weapon.ROLLER_FAN_YAW, "roller flick fan");
			helper.assertValueEqual(WeaponTuning.get(Weapon.ROLLER).value("fan_pitch"), (double) Weapon.ROLLER_PITCH, "roller flick arc");
			helper.assertValueEqual(WeaponTuning.get(Weapon.ROLLER).value("gravity"), Weapon.ROLLER_GRAVITY, "roller flick gravity");
			helper.assertValueEqual(WeaponTuning.get(Weapon.ROLLER).value("splat_radius"), (double) Weapon.ROLLER_SPLAT_RADIUS, "roller 5x5");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SLOSHER).value("count"), (double) Weapon.SLOSHER_FAN.length, "slosher balls");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SLOSHER).value("gravity"), Weapon.SLOSHER_GRAVITY, "slosher gravity");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SLOSHER).value("fan_pitch"), (double) Weapon.SLOSHER_PITCH, "slosher lob");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SLOSHER).value("splat_radius"), (double) Weapon.SLOSHER_SPLAT_RADIUS, "slosher 5x5");
			// fan_yaw is the hand-written fan {-4, 4} as one number: two pellets, eight degrees apart.
			double step = WeaponTuning.get(Weapon.SLOSHER).value("fan_yaw");
			for (int i = 0; i < Weapon.SLOSHER_FAN.length; i++) {
				helper.assertValueEqual((double) Weapon.SLOSHER_FAN[i], (i - (Weapon.SLOSHER_FAN.length - 1) / 2.0) * step,
						"slosher fan offset " + i);
			}
			// And the charger's own, which no other weapon reads.
			WeaponTuning charger = WeaponTuning.get(Weapon.CHARGER);
			helper.assertValueEqual(charger.value("charge_min"), (double) Weapon.MIN_CHARGE_TICKS, "charger minimum charge");
			helper.assertValueEqual(charger.value("charge_full"), (double) Weapon.CHARGE_FULL_TICKS, "charger full charge");
			helper.assertValueEqual(charger.value("range_min"), Weapon.CHARGE_BASE_RANGE, "charger range at no charge");
			helper.assertValueEqual(charger.value("range_full"), Weapon.CHARGE_BASE_RANGE + Weapon.CHARGE_EXTRA_RANGE,
					"charger range at a full charge");
			helper.assertValueEqual(charger.value("charge_ink_full"), (double) (Weapon.CHARGE_BASE_COST + Weapon.CHARGE_EXTRA_COST),
					"charger ink at a full charge");
			helper.assertValueEqual(charger.value("charge_damage_partial"), (double) Weapon.CHARGE_PARTIAL_DAMAGE,
					"charger damage at the top of a partial charge");
			helper.assertValueEqual(charger.value("charge_damage_full"), (double) Weapon.CHARGE_FULL_DAMAGE,
					"charger damage at a full charge");
			// Splatoon's charger: 9 blocks to 24, 2 ink to 18, 8 damage to a one-shot splat at full.
			helper.assertValueEqual(charger.value("range_min"), 9.0, "a snap shot reaches nine blocks");
			helper.assertValueEqual(charger.value("range_full"), 24.0, "a full charge reaches twenty-four");
			helper.assertValueEqual(charger.value("charge_ink_min"), 2.0, "a snap shot costs two");
			helper.assertValueEqual(charger.value("charge_ink_full"), 18.0, "a full charge costs eighteen");
			helper.assertValueEqual(charger.value("charge_damage_min"), 8.0, "a snap shot is worth a shooter's shot");
			helper.assertValueEqual(charger.value("charge_damage_partial"), 16.0, "a nearly-full charge is worth two");
			helper.assertValueEqual(charger.value("charge_damage_full"), 32.0, "and a full charge is a splat outright");
			// Splatoon's curve, with the step in it that is the whole weapon: 8 → 16 in proportion to the
			// hold, and then a jump to 32 the moment it is full. A straight 8 → 32 would make every
			// fraction of a charge worth its fraction of a kill, which is a duller gun.
			helper.assertValueEqual(PaintWeapon.chargeDamage(charger, 0.0f), 8.0f, "no charge is the floor");
			helper.assertValueEqual(PaintWeapon.chargeDamage(charger, 0.5f), 12.0f, "half way is half way up the partial");
			helper.assertTrue(PaintWeapon.chargeDamage(charger, 0.99f) < 16.0f,
					"a charge a hair short of full is still a partial: " + PaintWeapon.chargeDamage(charger, 0.99f));
			helper.assertValueEqual(PaintWeapon.chargeDamage(charger, 1.0f), 32.0f, "and full is the splat");
			helper.assertTrue(PaintWeapon.chargeDamage(charger, 1.0f) > 20.0f,
					"which is more than a player has, so it is one shot");
			helper.assertFalse(WeaponTuning.applies(Weapon.SLOSHER, Param.RANGE_FULL), "the slosher has no charge to tune");
			helper.assertFalse(WeaponTuning.applies(Weapon.CHARGER, Param.BOUNCES), "the charger throws nothing to bounce");
			// The roll belongs to the roller alone, as the charge belongs to the charger.
			helper.assertFalse(WeaponTuning.applies(Weapon.SHOOTER, Param.ROLL_WIDTH), "a shooter does not roll");
			helper.assertTrue(WeaponTuning.applies(Weapon.ROLLER, Param.ROLL_WIDTH), "the roller does");
		});
		helper.succeed();
	}

	/** A tuned number is in the next shot: no restart, no re-registering, just the next click. */
	@GameTest
	public void tuningChangesReachTheShot(GameTestHelper helper) {
		Player player = gunner(helper);
		helper.getLevel().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team(helper, PaintColor.DATA));
		withTuning(() -> {
			WeaponTuning shooter = WeaponTuning.get(Weapon.SHOOTER);
			shooter.reset();
			shooter.set(Param.VELOCITY, 0.5);
			shooter.set(Param.BOUNCES, 0.0);
			// Vanilla adds the spread's jitter to the unit direction before scaling by the velocity, so a
			// spread of six degrees moves the speed by a good tenth. Turned off, the tuned velocity is the
			// speed exactly, and that is what this is measuring.
			shooter.set(Param.SPREAD, 0.0);
			shooter.set(Param.SPREAD_AIR, 0.0);
			PaintBall slow = onlyBall(helper, player);
			helper.assertTrue(Math.abs(slow.getDeltaMovement().length() - 0.5) < 1.0e-6,
					"the tuned velocity is the shot's: " + slow.getDeltaMovement().length());
			helper.assertValueEqual(slow.bouncesLeft(), 0, "the tuned bounces are the ball's");
			slow.discard();
			shooter.reset();
			helper.assertValueEqual(shooter.value(Param.VELOCITY), (double) Weapon.SHOOTER.velocity,
					"a reset puts the default velocity back");
			shooter.set(Param.SPREAD, 0.0);
			shooter.set(Param.SPREAD_AIR, 0.0);
			PaintBall fast = onlyBall(helper, player);
			helper.assertTrue(Math.abs(fast.getDeltaMovement().length() - Weapon.SHOOTER.velocity) < 1.0e-6,
					"and the shot leaves at it: " + fast.getDeltaMovement().length());
			helper.assertValueEqual(fast.bouncesLeft(), Weapon.SHOOTER_BOUNCES, "and the default bounces");
			fast.discard();
			// A splat radius is the side of a loop and a count is a spawn, so neither takes a number that
			// would turn one click into a million block writes or five thousand entities.
			refused(helper, () -> shooter.set(Param.SPLAT_RADIUS, 500.0), "a splat radius of 500");
			refused(helper, () -> shooter.set(Param.COUNT, 5000.0), "a count of 5000");
			refused(helper, () -> shooter.set(Param.VELOCITY, Double.NaN), "a velocity of NaN");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("splat_radius"), (double) Painter.RADIUS,
					"a refused set leaves the value alone");
		});
		helper.succeed();
	}

	/** Something the tuning must not accept: {@code set} throws rather than taking it. */
	private static void refused(GameTestHelper helper, Runnable set, String what) {
		try {
			set.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw helper.assertionException(Component.literal(what + " was accepted"));
	}

	/** One click of the shooter, and the ball it threw. */
	private static PaintBall onlyBall(GameTestHelper helper, Player player) {
		// The shooter is a held-use weapon now and its item cooldown is its fire rate, so a second shot in
		// the same tick is refused on purpose. A test that wants two shots has to let the gun catch up.
		readyToFire(player);
		// shootFromRotation adds the shooter's own movement to the shot, and firing shoves the shooter
		// backwards, so a second shot in the same breath would leave 0.06 slower than the tuned velocity.
		player.setDeltaMovement(Vec3.ZERO);
		InteractionResult result = PaintWeapon.of(Weapon.SHOOTER).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(result.consumesAction(), "shoots");
		List<PaintBall> balls = helper.getEntities(PaintBall.TYPE, new BlockPos(4, 3, 4), 4.0);
		helper.assertValueEqual(balls.size(), 1, "one ball");
		return balls.get(0);
	}

	/**
	 * Take the weapon in this player's main hand off cooldown. A test that fires twice without letting
	 * ticks pass is asking for something the fire rate refuses; this is how it asks honestly.
	 */
	private static void readyToFire(Player player) {
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		player.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(held.getItem()));
	}

	/**
	 * The file keeps what has been tuned and nothing else, and reading it back puts exactly that on top
	 * of the defaults — so a default that moves in the code moves for everyone who never touched it. A
	 * file that has been edited by hand is read defensively: nothing in it can put a value somewhere the
	 * command would not have let it go.
	 */
	@GameTest
	public void tuningRoundTripsThroughJson(GameTestHelper helper) throws IOException {
		Path file = Files.createTempFile("rivals-weapons", ".json");
		try {
			withTuningChecked(() -> {
				WeaponTuning.resetAll();
				WeaponTuning.get(Weapon.SHOOTER).set(Param.VELOCITY, 0.5);
				WeaponTuning.get(Weapon.SLOSHER).set(Param.GRAVITY, 0.2);
				WeaponTuning.save(file);
				WeaponTuning.resetAll();
				helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("velocity"), (double) Weapon.SHOOTER.velocity,
						"a reset instance is back at the defaults before the load");
				WeaponTuning.load(file);
				helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("velocity"), 0.5, "the shooter's velocity came back");
				helper.assertValueEqual(WeaponTuning.get(Weapon.SLOSHER).value("gravity"), 0.2, "the slosher's gravity came back");
				helper.assertValueEqual(WeaponTuning.get(Weapon.ROLLER).value("velocity"), (double) Weapon.ROLLER.velocity,
						"a weapon nobody tuned is untouched by the file");
				JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				helper.assertValueEqual(root.keySet(), Set.of("shooter", "slosher"), "only the two tuned weapons are in the file");
				helper.assertValueEqual(root.getAsJsonObject("shooter").keySet(), Set.of("velocity"), "and only the one key");
				helper.assertValueEqual(root.getAsJsonObject("slosher").keySet(), Set.of("gravity"), "and only the one key");
				// Nothing tuned is an empty object, not a full dump of every default.
				WeaponTuning.resetAll();
				WeaponTuning.save(file);
				helper.assertValueEqual(Files.readString(file, StandardCharsets.UTF_8).trim(), "{}", "a fresh tuning writes {}");
				// Now the file as a hand edit can leave it: Gson's parser is lenient enough to hand back NaN,
				// 500 is outside what a splat radius may be, and range_full is a number a shooter never reads.
				Files.writeString(file, "{\"shooter\": {\"velocity\": NaN, \"splat_radius\": 500, \"range_full\": 99},"
						+ " \"nonesuch\": {\"velocity\": 1}}", StandardCharsets.UTF_8);
				WeaponTuning.load(file);
				helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("velocity"), (double) Weapon.SHOOTER.velocity,
						"NaN is not a velocity: the default stands");
				helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("splat_radius"), Param.SPLAT_RADIUS.max,
						"an out-of-range splat radius is clamped, not taken");
				helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("range_full"),
						WeaponTuning.get(Weapon.SHOOTER).defaultValue(Param.RANGE_FULL),
						"a parameter the shooter does not read is ignored");
			});
		} finally {
			Files.deleteIfExists(file);
		}
		helper.succeed();
	}

	/** {@link #withTuning} for a body that may throw a checked exception. */
	private static void withTuningChecked(IOBody body) throws IOException {
		IOException[] thrown = new IOException[1];
		withTuning(() -> {
			try {
				body.run();
			} catch (IOException failure) {
				thrown[0] = failure;
			}
		});
		if (thrown[0] != null) throw thrown[0];
	}

	private interface IOBody {
		void run() throws IOException;
	}

	/**
	 * The specials are tuned from inside the game too, under a literal of their own:
	 * {@code /rivals tune special <id> <param> <value>}. The same shape as a weapon's sheet, the same
	 * {@code reset} in either slot, and the same two refusals — a parameter the special does not have,
	 * and a number outside what it takes — because the point of the command is that the knobs can be
	 * found from in front of the game rather than in a file.
	 */
	@GameTest
	public void tuneSpecialCommandMovesTheSpecialsNumbers(GameTestHelper helper) {
		List<String> said = new ArrayList<>();
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack source = server.createCommandSourceStack().withSource(sink(said));
		withSpecialTuning(() -> {
			SpecialTuning.resetAll();
			// The whole sheet, which is what a tuner reads first.
			server.getCommands().performPrefixedCommand(source, "rivals tune special burst_bomb");
			String sheet = String.join(" | ", said);
			helper.assertTrue(sheet.contains("ink") && sheet.contains("blast") && sheet.contains("velocity"),
					"the sheet lists the burst bomb's numbers: " + sheet);
			helper.assertFalse(sheet.contains("slide_ticks"), "and not the curling bomb's slide: " + sheet);
			// A parameter this special does not have names itself and lists the ones that would work.
			said.clear();
			server.getCommands().performPrefixedCommand(source, "rivals tune special burst_bomb fuse 5");
			String refused = String.join(" | ", said);
			helper.assertTrue(refused.contains("fuse"), "the failure names what was typed: " + refused);
			helper.assertTrue(refused.contains("ink") && refused.contains("blast"),
					"and lists what would have worked: " + refused);
			// A number outside the range is refused with the range in the message, and changes nothing.
			said.clear();
			server.getCommands().performPrefixedCommand(source, "rivals tune special splat_bomb radius 500");
			String outside = String.join(" | ", said);
			helper.assertTrue(outside.contains("radius") && outside.contains(SpecialTuning.Param.RADIUS.range()),
					"the failure states the range: " + outside);
			helper.assertValueEqual(SpecialTuning.get(Special.SPLAT_BOMB).value("radius"),
					(double) Weapon.SPECIAL_RADIUS, "and the splat bomb is untouched");
			// And one that works lands, and reset puts it back. The curling bomb's friction, which nothing
			// else in the suite throws.
			said.clear();
			server.getCommands().performPrefixedCommand(source, "rivals tune special curling_bomb friction 0.8");
			helper.assertValueEqual(SpecialTuning.get(Special.CURLING_BOMB).value("friction"), 0.8,
					"a known parameter is set");
			helper.assertTrue(String.join(" | ", said).contains("0.8"), "and the reply says so: " + said);
			server.getCommands().performPrefixedCommand(source, "rivals tune special reset");
			helper.assertValueEqual(SpecialTuning.get(Special.CURLING_BOMB).value("friction"), Special.CURLING_FRICTION,
					"/rivals tune special reset puts every special back");
		});
		helper.succeed();
	}

	/** {@link #withTuning} for the specials' sheet: every number put back however the body left it. */
	private static void withSpecialTuning(Runnable body) {
		Map<Special, Map<SpecialTuning.Param, Double>> before = new EnumMap<>(Special.class);
		for (Special special : Special.values()) {
			Map<SpecialTuning.Param, Double> values = new EnumMap<>(SpecialTuning.Param.class);
			for (SpecialTuning.Param param : SpecialTuning.Param.values()) {
				values.put(param, SpecialTuning.get(special).value(param));
			}
			before.put(special, values);
		}
		try {
			body.run();
		} finally {
			for (Special special : Special.values()) {
				before.get(special).forEach((param, value) -> SpecialTuning.get(special).set(param, value));
			}
		}
	}

	/**
	 * A parameter nobody has heard of is a failure that says what the weapon does have, rather than a
	 * silent no-op: the whole point of the command is that you can find the knobs from inside the game.
	 * A number outside what the parameter takes is the same story, with the range in the message.
	 */
	@GameTest
	public void tuneCommandRejectsUnknownParameters(GameTestHelper helper) {
		List<String> said = new ArrayList<>();
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack source = server.createCommandSourceStack().withSource(sink(said));
		withTuning(() -> {
			WeaponTuning.resetAll();
			server.getCommands().performPrefixedCommand(source, "rivals tune shooter nope 1");
			String text = String.join(" | ", said);
			helper.assertTrue(text.contains("nope"), "the failure names what was typed: " + text);
			helper.assertTrue(text.contains("velocity") && text.contains("bounces") && text.contains("splat_radius"),
					"the failure lists the names that would have worked: " + text);
			helper.assertFalse(text.contains("range_full"), "and not the charger's, on a shooter: " + text);
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("velocity"), (double) Weapon.SHOOTER.velocity,
					"a refused set changed nothing");
			// A known name with a number it cannot take is refused too, and the message says what it can.
			said.clear();
			server.getCommands().performPrefixedCommand(source, "rivals tune shooter splat_radius 500");
			String refused = String.join(" | ", said);
			helper.assertTrue(refused.contains("splat_radius") && refused.contains(Param.SPLAT_RADIUS.range()),
					"the failure states the range: " + refused);
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("splat_radius"), (double) Painter.RADIUS,
					"an out-of-range set changed nothing");
			// The same command with a name and a number that both work does land. Deliberately the kick,
			// which nothing about a ball in flight reads, since other tests are firing while this runs.
			said.clear();
			server.getCommands().performPrefixedCommand(source, "rivals tune shooter kick -4");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("kick"), -4.0, "a known parameter is set");
			helper.assertTrue(String.join(" | ", said).contains("-4"), "and the reply says so: " + said);
			server.getCommands().performPrefixedCommand(source, "rivals tune reset");
			helper.assertValueEqual(WeaponTuning.get(Weapon.SHOOTER).value("kick"), (double) Weapon.SHOOTER.kickPitch,
					"/rivals tune reset puts every weapon back");
		});
		helper.succeed();
	}

	// ---------------------------------------------------------------- the MAIN datapack

	/** Set MAIN's running flag, the way MAIN's own functions do. */
	private static void running(MinecraftServer server, int value) {
		Stats.write(server, MainPack.STATE_OBJECTIVE, ScoreHolder.forNameOnly(MainPack.RUNNING_HOLDER), value);
	}

	/**
	 * MAIN owns the minigame; this mod owns the playing of it. MAIN keeps {@code ?running} in
	 * {@code splat.state} — 1 while its own {@code ?superstate main.state} is 3 — and the mod reads it and
	 * acts on the <em>edges</em>: a flag that stays at 1 through the ten seconds of fireworks must not read
	 * as "start another round", and the first read of a server's life only reads, so a restart with the
	 * flag already up neither starts nor stops anything.
	 *
	 * <p>{@link MainPack#edge} rather than {@link MainPack#poll} deliberately: see
	 * {@link #matchStopEndsItEarly} for why no second test may start a round. The flag is put back down on
	 * the way out, because {@code MainPack}'s own poll keeps reading it every half second for the rest of
	 * the batch.
	 */
	@GameTest
	public void theRunningFlagReadsOnTheEdges(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		try {
			// An objective MAIN has not made reads as 0: nobody is running the minigame, which is right.
			MainPack.forget();
			running(server, 0);
			helper.assertValueEqual(MainPack.edge(server), MainPack.Edge.NONE, "the first read only reads");
			helper.assertValueEqual(MainPack.lastFlag(), 0, "and it remembers what it read");
			running(server, 1);
			helper.assertValueEqual(MainPack.edge(server), MainPack.Edge.START, "0 → 1 starts a round");
			// Still 1: the fireworks are not a reason to start another one.
			helper.assertValueEqual(MainPack.edge(server), MainPack.Edge.NONE, "a flag that stays up means nothing more");
			running(server, 0);
			helper.assertValueEqual(MainPack.edge(server), MainPack.Edge.STOP, "1 → 0 blows the whistle");
			helper.assertValueEqual(MainPack.edge(server), MainPack.Edge.NONE, "and a flag that stays down likewise");
			// MAIN's contract is off/on, so anything that is not 0 is on rather than a third state.
			running(server, 3);
			helper.assertValueEqual(MainPack.edge(server), MainPack.Edge.START, "anything but 0 is running");
			helper.assertValueEqual(MainPack.flag(server), 3, "read back as it was written, whatever it was");
		} finally {
			running(server, 0);
			helper.getLevel().getScoreboard().resetSinglePlayerScore(
					ScoreHolder.forNameOnly(MainPack.RUNNING_HOLDER),
					Stats.objective(server, MainPack.STATE_OBJECTIVE));
			MainPack.forget();
		}
		helper.succeed();
	}

	/**
	 * The two numbers MAIN's outro reads. {@code splat.stats.blocks} is <em>held</em> paint — the faces a
	 * player was the last to paint — so a cell painted over by the other side changes hands rather than
	 * counting for both. {@code splat.stats.kills} goes on the board the tick the kill happens.
	 */
	@GameTest
	public void theStatsBoardHoldsPaintAndKills(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerScoreboard board = level.getScoreboard();
		ServerPlayer mine = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		ServerPlayer theirs = connected(mockServerPlayer(helper, GameType.SURVIVAL));
		BlockPos floor = new BlockPos(2, 1, 2);
		BlockPos cell = floor.above();
		try {
			board.addPlayerToTeam(mine.getScoreboardName(), team(helper, PaintColor.DATA));
			board.addPlayerToTeam(theirs.getScoreboardName(), team(helper, PaintColor.IT));
			// The start of a round: an empty board, and a zero each, which is how MAIN's sort sees the
			// whole roster — and how the board learns a UUID's name for the whistle.
			Stats.startRound(server, List.of(mine, theirs));
			helper.assertValueEqual(Stats.read(server, Stats.BLOCKS_OBJECTIVE, mine), 0, "everybody playing starts on zero");
			helper.setBlock(floor, Blocks.STONE);
			BlockPos surface = helper.absolutePos(floor);
			PaintTally tally = PaintTally.of(level);
			helper.assertTrue(Painter.paintFace(level, surface, Direction.UP, PaintColor.DATA, mine.getUUID()),
					"a face painted, and credited");
			helper.assertValueEqual(tally.countByPlayer(level).getOrDefault(mine.getUUID(), 0), 1, "one face held");
			// Painted over by the other side: the face is theirs now, and nobody holds it twice.
			helper.assertTrue(Painter.paintFace(level, surface, Direction.UP, PaintColor.IT, theirs.getUUID()),
					"the other colour repaints it");
			Map<UUID, Integer> held = tally.countByPlayer(level);
			helper.assertValueEqual(held.getOrDefault(mine.getUUID(), 0), 0, "the first painter holds nothing now");
			helper.assertValueEqual(held.getOrDefault(theirs.getUUID(), 0), 1, "whoever painted over it holds it");
			// And onto the board, which is where MAIN reads it.
			Stats.publish(server, level);
			helper.assertValueEqual(Stats.read(server, Stats.BLOCKS_OBJECTIVE, theirs), 1, "held paint is on the board");
			helper.assertValueEqual(Stats.read(server, Stats.BLOCKS_OBJECTIVE, mine), 0, "and the painter who lost it has none");
			helper.assertValueEqual(Stats.credit(theirs), 1, "a kill is counted");
			helper.assertValueEqual(Stats.read(server, Stats.KILLS_OBJECTIVE, theirs), 1, "on the board the same tick");
			// And the kills are what breaks an equal-paint draw, so that MAIN is always told a side.
			List<ServerPlayer> both = List.of(mine, theirs);
			helper.assertValueEqual(Stats.sideWithMostKills(both), PaintColor.IT, "the side that killed more takes a draw");
			Stats.credit(mine);
			helper.assertTrue(Stats.sideWithMostKills(both) == null, "level on kills too is a real draw");
		} finally {
			helper.setBlock(cell, Blocks.AIR);
			helper.setBlock(floor, Blocks.AIR);
			PaintTally.of(level).count(level); // prunes the cell this test just took away
			Stats.clearAll();
			for (ServerPlayer player : List.of(mine, theirs)) {
				board.resetSinglePlayerScore(player, Stats.objective(server, Stats.BLOCKS_OBJECTIVE));
				board.resetSinglePlayerScore(player, Stats.objective(server, Stats.KILLS_OBJECTIVE));
				board.removePlayerFromTeam(player.getScoreboardName());
			}
		}
		helper.succeed();
	}
}
