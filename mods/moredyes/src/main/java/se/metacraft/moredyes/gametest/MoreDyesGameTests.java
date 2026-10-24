package se.metacraft.moredyes.gametest;

import eu.pb4.polymer.blocks.impl.BlockExtBlockMapper;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import se.metacraft.moredyes.MoreDyesConfig;
import se.metacraft.moredyes.sign.SignColors;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundEvent;
import eu.pb4.polymer.soundpatcher.impl.SoundRemapperImpl;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import se.metacraft.moredyes.MoreDyes;
import se.metacraft.moredyes.content.item.ModDyeItem;
import se.metacraft.moredyes.banner.BannerPatterns;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import se.metacraft.moredyes.armor.AnimatedArmor;
import se.metacraft.moredyes.armor.PackClock;
import se.metacraft.moredyes.color.Animation;
import se.metacraft.moredyes.color.ModColor;
import se.metacraft.moredyes.color.ModColors;
import se.metacraft.moredyes.content.block.ClientStates;
import se.metacraft.moredyes.content.block.ShapedBlocks;
import se.metacraft.moredyes.content.Family;
import se.metacraft.moredyes.content.Looks;
import se.metacraft.moredyes.content.ModContent;
import se.metacraft.moredyes.recipe.DyeStacks;
import se.metacraft.moredyes.sheep.SheepColors;
import se.metacraft.moredyes.sheep.SheepOverlay;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.network.HashedStack;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Registry;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import net.minecraft.world.level.block.state.properties.SlabType;
import se.metacraft.moredyes.content.block.ModFlower;
import se.metacraft.moredyes.cushion.CushionOverlay;
import se.metacraft.moredyes.cushion.Cushions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side game tests (Fabric GameTest API, vanilla test framework underneath).
 *
 * <p>Run headless: {@code JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:moredyes:runServer}
 * — starts a test server, runs every test in an empty 8×8×8 structure, and fails on any failure. Or in a normal world as an op: {@code /test runall} — the lectern/beacon feedback is
 * vanilla's test framework.
 *
 * <p>What these can and can't cover: everything a vanilla client would be sent (client states,
 * registry contents, tracked-data overrides), drops, and interactions. Not pixels: whether a
 * model looks right is still a visual check with a vanilla client.
 */
public final class MoreDyesGameTests {
	private static final BlockPos FLOOR = new BlockPos(0, 0, 0);

	/** A static colour: the tests that say "a colour" mean one that holds still. */
	private static ModColor first() {
		return ModColors.all().stream().filter(c -> c.animation().isEmpty()).findFirst().orElseThrow();
	}

	/** The first colour that animates; the tests that need one fail if colors.json has none. */
	private static ModColor animated() {
		return ModColors.all().stream().filter(c -> c.animation().isPresent()).findFirst()
				.orElseThrow(() -> new IllegalStateException("colors.json has no animated colour"));
	}

	private static byte[] resourceOrNull(String path) {
		try (var in = MoreDyes.class.getResourceAsStream("/assets/moredyes/textures/" + path)) {
			return in == null ? null : in.readAllBytes();
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private static String resource(String path) {
		byte[] data = resourceOrNull(path);
		if (data == null) throw new IllegalStateException("missing texture resource " + path);
		return new String(data, java.nio.charset.StandardCharsets.UTF_8);
	}

	private static java.awt.image.BufferedImage image(String path) {
		byte[] data = resourceOrNull(path + ".png");
		if (data == null) throw new IllegalStateException("missing texture " + path);
		try {
			return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private static Block block(Family family) {
		return ModContent.block(first(), family);
	}

	/** GameTestHelper.destroyBlock breaks without drops; we want the loot. */
	private static void breakWithDrops(GameTestHelper helper, BlockPos pos) {
		helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** Every server state of every block resolves to a real client state, never the error block. */
	@GameTest
	public void clientStatesResolve(GameTestHelper helper) {
		int states = 0;
		for (ModColor color : ModColors.all()) {
			for (Family family : Family.values()) {
				Block block = ModContent.block(color, family);
				helper.assertTrue(block instanceof PolymerBlock, family.id + " is not a Polymer block");
				for (BlockState state : block.getStateDefinition().getPossibleStates()) {
					BlockState client = ((PolymerBlock) block).getPolymerBlockState(state, null);
					helper.assertTrue(client != null && client != ClientStates.ERROR, "no client state for " + state);
					if (family == Family.BED) {
						helper.assertTrue(client.getBlock() instanceof BedBlock, "bed client state is not a bed: " + client);
					}
					states++;
				}
			}
		}
		MoreDyes.LOGGER.info("[gametest] {} client states resolved", states);
		helper.succeed();
	}

	/** Vanilla's stairs and slabs of each of our stairs and slab families' material, in the sixteen colours. */
	private static final Map<Family, ColorCollection<Block>> VANILLA_SHAPES = Map.of(
			Family.WOOL_STAIRS, Blocks.WOOL_STAIRS, Family.WOOL_SLAB, Blocks.WOOL_SLAB,
			Family.CONCRETE_STAIRS, Blocks.CONCRETE_STAIRS, Family.CONCRETE_SLAB, Blocks.CONCRETE_SLAB);

	/**
	 * A stair or slab is held, on the client, as vanilla's stair or slab of the same material in the
	 * nearest vanilla colour, so the placer's client predicts that shape (not a full cube) with the
	 * material's placement sound, and the block it predicts is the one the server then shows.
	 */
	@GameTest
	public void stairsAndSlabsAreHeldAsVanillaShapes(GameTestHelper helper) {
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		int checked = 0;
		for (ModColor color : ModColors.all()) {
			for (Family family : Family.values()) {
				if (family.kind != Family.Kind.STAIRS && family.kind != Family.Kind.SLAB) continue;
				Block ours = ModContent.block(color, family);
				ItemStack client = PolymerItemUtils.getPolymerItemStack(new ItemStack(ours),
						viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
				helper.assertValueEqual(client.getItem(), VANILLA_SHAPES.get(family).pick(SignColors.nearestDye(color)).asItem(),
						"the client item of " + ours);
				Block shown = ((BlockItem) client.getItem()).getBlock();
				helper.assertTrue(family.kind == Family.Kind.STAIRS ? shown instanceof StairBlock : shown instanceof SlabBlock,
						ours + " is held as " + shown + ", not its shape");
				helper.assertTrue(ModContent.sameSounds(shown.defaultBlockState().getSoundType(), ours.defaultBlockState().getSoundType()),
						ours + " is held as " + shown + ", which places with another sound");
				checked++;
			}
		}
		helper.assertValueEqual(checked, VANILLA_SHAPES.size() * ModColors.all().size(), "stairs and slabs checked");
		helper.succeed();
	}

	/**
	 * Under its display, every state of a stair or slab is vanilla's stair or slab of the same material
	 * in the nearest vanilla colour, with the same properties. A client that does not draw the display
	 * (the block just placed, or beyond the display's view range) sees that stair, not through it. A
	 * double slab has no display and is the material's own full block.
	 */
	@GameTest
	public void stairsAndSlabsAreShownAsVanillaInTheNearestColour(GameTestHelper helper) {
		int checked = 0;
		for (ModColor color : ModColors.all()) {
			DyeColor nearest = SignColors.nearestDye(color);
			for (Map.Entry<Family, ColorCollection<Block>> entry : VANILLA_SHAPES.entrySet()) {
				Block ours = ModContent.block(color, entry.getKey());
				Block vanilla = entry.getValue().pick(nearest);
				for (BlockState state : ours.getStateDefinition().getPossibleStates()) {
					BlockState client = ModContent.clientState(state);
					if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
						BlockState material = ModContent.block(color, entry.getKey().materialFamily()).defaultBlockState();
						helper.assertValueEqual(client, ModContent.clientState(material), "the client state of " + state);
						continue;
					}
					helper.assertValueEqual(client.getBlock(), vanilla, "the client block of " + state);
					helper.assertValueEqual(client.getProperties().size(), state.getProperties().size(), "the client's properties of " + state);
					for (Property<?> property : state.getProperties()) {
						helper.assertValueEqual(client.getValue(property), state.getValue(property), property.getName() + " of " + state + " on the client");
					}
					checked++;
				}
			}
		}
		MoreDyes.LOGGER.info("[gametest] {} stair and slab states shown as vanilla's in the nearest colour", checked);
		helper.succeed();
	}

	/**
	 * A stair's or slab's display covers the vanilla block under it with each face just outside the
	 * vanilla one, so the two never z-fight. It grows about a point that sees every face of the shape
	 * from inside, so any point of the shape's surface, taken back through the growth, lands strictly
	 * inside the shape; one on a face the point does not see would land outside, and that face would
	 * sink into the vanilla block. Also checks a placed block's display carries that growth.
	 */
	@GameTest
	public void stairAndSlabDisplaysCoverTheVanillaFaces(GameTestHelper helper) {
		int checked = 0;
		for (Family family : VANILLA_SHAPES.keySet()) {
			Block ours = block(family);
			ShapedBlocks.DisplayProvider provider = (ShapedBlocks.DisplayProvider) ours;
			for (BlockState state : ours.getStateDefinition().getPossibleStates()) {
				if (provider.displayStack(state) == null) continue; // a double slab: no display
				double growth = provider.displayGrowth(state);
				helper.assertTrue(growth > 1, "the display of " + state + " is not grown");
				// the translation is (1 - growth) × the centre of growth, in world axes from the cell centre
				Vector3fc t = provider.displayTranslation(state);
				Vec3 centre = new Vec3(t.x(), t.y(), t.z()).scale(1 / (1 - growth)).add(0.5, 0.5, 0.5);
				List<AABB> boxes = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
				for (AABB box : boxes) {
					for (int i = 0; i < 27; i++) {
						Vec3 surface = new Vec3(Mth.lerp(i % 3 / 2.0, box.minX, box.maxX),
								Mth.lerp(i / 3 % 3 / 2.0, box.minY, box.maxY), Mth.lerp(i / 9 / 2.0, box.minZ, box.maxZ));
						Vec3 back = centre.add(surface.subtract(centre).scale(1 / growth));
						boolean inside = boxes.stream().anyMatch(b -> b.minX < back.x && back.x < b.maxX
								&& b.minY < back.y && back.y < b.maxY && b.minZ < back.z && back.z < b.maxZ);
						helper.assertTrue(inside, "the display of " + state + ", grown about " + centre + ", leaves " + surface + " uncovered");
					}
				}
				checked++;
			}
		}
		helper.assertValueEqual(checked, 2 * (4 * 2 * 5 * 2) + 2 * (2 * 2), "stair and slab states with a display");

		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		BlockState stair = block(Family.WOOL_STAIRS).defaultBlockState().setValue(StairBlock.FACING, Direction.WEST)
				.setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
		helper.setBlock(pos, stair);
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "no display holder");
		ItemDisplayElement display = (ItemDisplayElement) attachment.holder().getElements().getFirst();
		ShapedBlocks.DisplayProvider provider = (ShapedBlocks.DisplayProvider) stair.getBlock();
		helper.assertValueEqual(display.getScale().x(), 2 * provider.displayGrowth(stair), "the placed display's scale");
		helper.assertValueEqual(new Vector3f(display.getTranslation()), new Vector3f(provider.displayTranslation(stair)), "the placed display's translation");
		helper.succeed();
	}

	/**
	 * Wherever the vanilla block the client is shown (or breaks) sounds different from ours, every
	 * one of both blocks' sounds is server-driven: the client's guess silenced, ours sent.
	 */
	@GameTest
	public void soundsAreServerDriven(GameTestHelper helper) {
		int patched = 0;
		for (ModColor color : ModColors.all()) {
			for (Family family : Family.values()) {
				Block block = ModContent.block(color, family);
				for (BlockState state : block.getStateDefinition().getPossibleStates()) {
					SoundType ours = state.getSoundType();
					for (BlockState shown : List.of(ModContent.clientState(state), ModContent.breakEventClientState((PolymerBlock) block, state))) {
						if (ModContent.sameSounds(shown.getSoundType(), ours)) continue;
						for (SoundEvent event : ModContent.soundEvents(shown.getSoundType())) {
							helper.assertTrue(SoundRemapperImpl.ignoreExceptions(event), state + " shown as " + shown + " lets the client play " + event.location());
						}
						for (SoundEvent event : ModContent.soundEvents(ours)) {
							helper.assertTrue(SoundRemapperImpl.ignoreExceptions(event), state + " never sends its own " + event.location());
						}
						patched++;
					}
				}
			}
		}
		// wool really is one of them: it is shown as a note block, which sounds like wood
		BlockState wool = block(Family.WOOL).defaultBlockState();
		helper.assertFalse(ModContent.sameSounds(ModContent.clientState(wool).getSoundType(), SoundType.WOOL), "wool is already shown as something woolly");
		helper.assertTrue(SoundRemapperImpl.ignoreExceptions(SoundType.WOOL.getBreakSound()), "wool's break sound is not server-driven");
		MoreDyes.LOGGER.info("[gametest] {} (state, shown) pairs sound like our block", patched);
		helper.succeed();
	}

	/** Double slabs drop two, and a bed drops once (from the head). */
	@GameTest
	public void slabAndBedDrops(GameTestHelper helper) {
		floor(helper);
		BlockPos slab = new BlockPos(1, 1, 1);
		helper.setBlock(slab, block(Family.WOOL_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
		breakWithDrops(helper, slab);

		BlockPos foot = new BlockPos(4, 1, 1), head = new BlockPos(4, 1, 2);
		BlockState bed = block(Family.BED).defaultBlockState();
		helper.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
		breakWithDrops(helper, head); // the head half carries the loot; the foot follows via updateShape

		helper.succeedWhen(() -> {
			helper.assertItemEntityCountIs(block(Family.WOOL_SLAB).asItem(), slab, 2.0, 2);
			helper.assertItemEntityCountIs(block(Family.BED).asItem(), head, 3.0, 1);
		});
	}

	/** Four candles in one block drop four candles (the loot table's set_count, in 26.3's shape). */
	@GameTest
	public void candlesDropTheirCount(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, block(Family.CANDLE).defaultBlockState().setValue(CandleBlock.CANDLES, 4));
		breakWithDrops(helper, pos);
		helper.succeedWhen(() -> {
			int n = helper.getEntities(EntityTypes.ITEM, pos, 2.0).stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertValueEqual(n, 4, "candles dropped");
		});
	}

	/** A shulker box in our colour keeps its contents when broken (vanilla block entity + loot). */
	@GameTest
	public void shulkerKeepsContents(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, block(Family.SHULKER_BOX));
		ShulkerBoxBlockEntity box = helper.getBlockEntity(pos, ShulkerBoxBlockEntity.class);
		box.setItem(0, new ItemStack(Items.DIAMOND, 3));
		breakWithDrops(helper, pos);
		helper.succeedWhen(() -> {
			List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
			helper.assertTrue(!drops.isEmpty(), "no drop");
			ItemStack stack = drops.getFirst().getItem();
			helper.assertTrue(stack.is(block(Family.SHULKER_BOX).asItem()), "wrong drop " + stack);
			var container = stack.get(DataComponents.CONTAINER);
			helper.assertTrue(container != null && container.nonEmptyItems().iterator().hasNext(), "contents lost");
		});
	}

	/** Concrete powder falls into water and hardens into OUR concrete. */
	@GameTest(maxTicks = 200)
	public void concretePowderHardens(GameTestHelper helper) {
		floor(helper);
		BlockPos water = new BlockPos(3, 1, 3);
		helper.setBlock(water, Blocks.WATER);
		helper.setBlock(water.above(3), block(Family.CONCRETE_POWDER));
		helper.succeedWhenBlockPresent(block(Family.CONCRETE), water);
	}

	/** A PolyDecorations canvas takes our dyes, in their colour's nearest map colour (CanvasEntityMixin). */
	@GameTest
	public void canvasTakesOurDyes(GameTestHelper helper) {
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("polydecorations")) {
			Canvas.takesOurDyes(helper, first());
			Canvas.paintsRandomKeyframes(helper, animated());
		}
		helper.succeed();
	}

	/** Names PolyDecorations' classes, so it is loaded only when PolyDecorations is there. */
	private static final class Canvas {
		static void takesOurDyes(GameTestHelper helper, ModColor color) {
			var painted = eu.pb4.polydecorations.entity.CanvasEntity.getColor(new ItemStack(ModContent.dye(color)));
			helper.assertTrue(painted.isPresent(), "the canvas does not take " + color.name() + " dye");
			helper.assertValueEqual(painted.get(), eu.pb4.mapcanvas.api.core.CanvasColor.from(color.mapColor(),
					net.minecraft.world.level.material.MapColor.Brightness.NORMAL), "the colour " + color.name() + " paints");
		}

		/** An animated dye paints a random colour from its keyframes' map colours every time. */
		static void paintsRandomKeyframes(GameTestHelper helper, ModColor color) {
			var allowed = color.animation().orElseThrow().mapColors().stream()
					.map(m -> eu.pb4.mapcanvas.api.core.CanvasColor.from(m, net.minecraft.world.level.material.MapColor.Brightness.NORMAL)).toList();
			var seen = new java.util.HashSet<eu.pb4.mapcanvas.api.core.CanvasColor>();
			for (int i = 0; i < 200; i++) {
				var painted = eu.pb4.polydecorations.entity.CanvasEntity.getColor(new ItemStack(ModContent.dye(color)));
				helper.assertTrue(painted.isPresent() && allowed.contains(painted.get()), "painted " + painted + ", not one of " + allowed);
				seen.add(painted.get());
			}
			helper.assertTrue(seen.size() >= 5, "only " + seen.size() + " distinct colours in 200 pixels");
		}
	}

	/** A happy ghast takes our harness, and wears it as our equipment asset. */
	@GameTest
	public void harnessFitsTheHappyGhast(GameTestHelper helper) {
		var ghast = helper.spawnWithNoFreeWill(EntityTypes.HAPPY_GHAST, new BlockPos(2, 4, 2));
		ItemStack harness = new ItemStack(ModContent.harness(first()));
		helper.assertTrue(ghast.isEquippableInSlot(harness, EquipmentSlot.BODY), "a happy ghast does not take " + first().name() + " harness");
		var asset = harness.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier();
		helper.assertValueEqual(asset, Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, first().id() + "_harness"), "the harness's equipment asset");
		helper.succeed();
	}

	/** A llama wears our carpet as its decor. */
	@GameTest
	public void carpetDecoratesTheLlama(GameTestHelper helper) {
		floor(helper);
		var llama = helper.spawnWithNoFreeWill(EntityTypes.LLAMA, new BlockPos(2, 1, 2));
		ItemStack carpet = new ItemStack(block(Family.CARPET));
		helper.assertTrue(llama.isEquippableInSlot(carpet, EquipmentSlot.BODY), "a llama does not take " + first().name() + " carpet");
		var asset = carpet.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier();
		helper.assertValueEqual(asset, Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, first().id() + "_carpet"), "the carpet's equipment asset");
		helper.succeed();
	}

	/**
	 * Our dye on a sign paints its lines our colour (a component colour, any RGB), and editing the
	 * sign afterwards keeps it; the sign's own dye colour is the nearest vanilla one.
	 */
	@GameTest
	public void signTextTakesOurColour(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, Blocks.OAK_SIGN);
		var sign = helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.SignBlockEntity.class);
		sign.updateText(t -> t.asMutable().setLine(0, net.minecraft.network.chat.Component.literal("Spiken")).asImmutable(), net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ModDyeItem dye = ModContent.dye(first());
		ItemStack stack = new ItemStack(dye);
		helper.assertTrue(dye.canApplyToSign(sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT), stack, player), "our dye cannot dye a sign");
		helper.assertTrue(dye.tryApplyToSign(helper.getLevel(), sign, net.minecraft.world.level.block.entity.SignTextSlot.FRONT, stack, player), "dyeing the sign failed");
		int rgb = first().rgb() & 0xFFFFFF;
		helper.assertValueEqual(sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(0).getStyle().getColor().getValue(), rgb, "the line's colour");
		helper.assertValueEqual(sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getColor(), SignColors.nearestDye(first()), "the sign's own colour");

		sign.setAllowedPlayerEditor(player.getUUID());
		sign.updateSignText(player, net.minecraft.world.level.block.entity.SignTextSlot.FRONT, List.of(
				net.minecraft.server.network.FilteredText.passThrough("Släggan"), net.minecraft.server.network.FilteredText.passThrough(""),
				net.minecraft.server.network.FilteredText.passThrough(""), net.minecraft.server.network.FilteredText.passThrough("")));
		var line = sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(0);
		helper.assertValueEqual(line.getString(), "Släggan", "the edited line");
		helper.assertValueEqual(line.getStyle().getColor() == null ? -1 : line.getStyle().getColor().getValue(), rgb, "the edited line's colour");
		helper.succeed();
	}

	/**
	 * Dyeing a sign by right-clicking it, ours and vanilla's in turn: each dye wins over whatever the
	 * sign was before, both ways. Our dye on a sign in its nearest vanilla colour (Cerise on pink),
	 * and a vanilla dye on a sign in ours, including the vanilla colour standing in for ours.
	 */
	@GameTest
	public void signsTakeEveryDyeByClick(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, Blocks.OAK_SIGN);
		var sign = helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.SignBlockEntity.class);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		var slot = sign.getSlotPlayerIsFacing(player);
		sign.updateText(t -> t.asMutable().setLine(0, net.minecraft.network.chat.Component.literal("Spiken")).asImmutable(), slot);
		java.util.function.Consumer<ItemStack> click = stack -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			helper.useBlock(pos, player);
		};
		java.util.function.Supplier<String> seen = () -> {
			var text = sign.getText(slot);
			var c = text.getMessages(false).get(0).getStyle().getColor();
			return text.getColor() + (c == null ? "" : String.format(" #%06X", c.getValue()));
		};

		for (ModColor color : ModColors.all()) {
			DyeColor stand = SignColors.nearestDye(color);
			String ours = stand + String.format(" #%06X", color.rgb() & 0xFFFFFF);
			click.accept(new ItemStack(Items.DYE.pick(stand)));
			helper.assertValueEqual(seen.get(), stand.toString(), "after vanilla " + stand);
			click.accept(new ItemStack(ModContent.dye(color)));
			helper.assertValueEqual(seen.get(), ours, "after " + color.name() + " on " + stand);
			click.accept(new ItemStack(Items.DYE.pick(stand)));
			helper.assertValueEqual(seen.get(), stand.toString(), "after vanilla " + stand + " on " + color.name());
			click.accept(new ItemStack(ModContent.dye(color)));
			click.accept(new ItemStack(Items.DYE.pick(DyeColor.BLUE)));
			helper.assertValueEqual(seen.get(), DyeColor.BLUE.toString(), "after vanilla blue on " + color.name());
		}
		helper.succeed();
	}

	/** Our dye on a tamed wolf's collar: the nearest vanilla colour, and the dye is used. */
	@GameTest
	public void collarTakesTheNearestColour(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		var wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(2, 1, 2));
		wolf.tame(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.dye(first()), 2));
		var result = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
				.interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, wolf, null);
		helper.assertTrue(result.consumesAction(), "the dye did nothing to the collar");
		helper.assertValueEqual(wolf.getCollarColor(), SignColors.nearestDye(first()), "the collar");
		helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, "dye left");
		helper.succeed();
	}

	/** Our flower crafts into our dye, grows on grass and drops itself. */
	@GameTest
	public void flowerMakesTheDye(GameTestHelper helper) {
		ItemStack dye = craft(helper, grid(new ItemStack(ModContent.flower(first()))));
		helper.assertTrue(dye.is(ModContent.dye(first())), "the flower made " + dye);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		Block flower = ((net.minecraft.world.item.BlockItem) ModContent.flower(first())).getBlock();
		helper.assertTrue(flower.defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(pos)), "it cannot grow on grass");
		helper.setBlock(pos, flower);
		breakWithDrops(helper, pos);
		helper.succeedWhen(() -> helper.assertItemEntityPresent(ModContent.flower(first()), pos, 2.0));
	}

	/**
	 * Our flower goes into an empty flower pot and comes back out, as vanilla's do; the client is
	 * shown vanilla's empty pot (the display draws the flower), and a broken pot gives both back.
	 */
	@GameTest
	public void flowerGoesInAPot(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		Item flower = ModContent.flower(first());
		helper.setBlock(pos, Blocks.FLOWER_POT);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(flower));
		helper.useBlock(pos, player);
		BlockState potted = helper.getBlockState(pos);
		helper.assertTrue(potted.getBlock() instanceof ModFlower.Potted, "the pot holds " + potted);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "the flower was not used up");
		helper.assertValueEqual(ModContent.clientState(potted), Blocks.FLOWER_POT.defaultBlockState(), "the client's block");
		helper.useBlock(pos, player);
		helper.assertBlockPresent(Blocks.FLOWER_POT, pos);
		helper.assertTrue(player.getMainHandItem().is(flower), "taking the flower out gave " + player.getMainHandItem());
		helper.setBlock(pos, potted);
		breakWithDrops(helper, pos);
		helper.succeedWhen(() -> {
			helper.assertItemEntityPresent(Items.FLOWER_POT, pos, 2.0);
			helper.assertItemEntityPresent(flower, pos, 2.0);
		});
	}

	/** A sniffer's digging table turns up our flowers too. */
	@GameTest
	public void sniffersDigUpOurFlowers(GameTestHelper helper) {
		var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.SNIFFER_DIGGING);
		var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 1, 1)))
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, helper.spawnWithNoFreeWill(EntityTypes.SNIFFER, new BlockPos(1, 1, 1)))
				.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.GIFT);
		java.util.Set<Item> found = new java.util.HashSet<>();
		for (int i = 0; i < 400; i++) table.getRandomItems(params).forEach(stack -> found.add(stack.getItem()));
		for (ModColor color : ModColors.all()) {
			helper.assertTrue(found.contains(ModContent.flower(color)), "a sniffer never dug up the " + color.flower() + ": " + found);
		}
		helper.assertTrue(found.contains(Items.TORCHFLOWER_SEEDS), "vanilla's finds are gone: " + found);
		helper.succeed();
	}

	/**
	 * A cushion placed from our item is ours: it takes our colour, a client is sent an interaction
	 * box in its place (the look is a display model), and it gives our item back.
	 */
	@GameTest
	public void cushionKeepsOurColour(GameTestHelper helper) {
		floor(helper);
		var cushion = EntityTypes.CUSHION.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		cushion.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 2.5)), 0, 0);
		cushion.applyComponentsFromItemStack(new ItemStack(ModContent.cushion(first())));
		helper.getLevel().addFreshEntity(cushion);
		helper.assertValueEqual(Cushions.colour(cushion), first(), "the placed cushion's colour");
		helper.assertValueEqual(new CushionOverlay(cushion).getPolymerEntityType(null), EntityTypes.INTERACTION, "what a client is sent");
		ItemStack back = cushion.getPickResult();
		helper.assertTrue(back.is(ModContent.cushion(first())), "it gives back " + back);
		helper.succeed();
	}

	/** Our candles can be lit (block tag) and are not cake-able (item tag deliberately absent). */
	@GameTest
	public void candleTags(GameTestHelper helper) {
		BlockState unlit = block(Family.CANDLE).defaultBlockState();
		helper.assertTrue(CandleBlock.canLight(unlit), "candle not lightable: missing #minecraft:candles block tag");
		helper.assertTrue(new ItemStack(block(Family.CANDLE)).is(ItemTags.CANDLES), "candle missing from #minecraft:candles item tag");
		helper.assertTrue(CandleCakeBlock.canLight(block(Family.CANDLE_CAKE).defaultBlockState()),
				"candle cake not lightable: missing #minecraft:candle_cakes block tag");
		helper.succeed();
	}

	/** Dye a sheep: colour attached, vanilla colour white, shearing drops our wool. */
	@GameTest(maxTicks = 100)
	public void sheepDyeAndShear(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(4, 1, 4);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, pos);
		SheepColors.set(sheep, first());
		helper.assertValueEqual(SheepColors.get(sheep), first(), "attached colour");
		helper.assertValueEqual(sheep.getColor(), DyeColor.WHITE, "vanilla colour while ours is set");
		sheep.shear(helper.getLevel(), SoundSource.PLAYERS, ItemStack.EMPTY);
		helper.assertTrue(sheep.isSheared(), "not sheared");
		helper.succeedWhen(() -> {
			List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 3.0);
			boolean ours = drops.stream().anyMatch(e -> e.getItem().is(block(Family.WOOL).asItem()));
			boolean vanilla = drops.stream().anyMatch(e -> e.getItem().is(Items.WOOL.white()));
			helper.assertTrue(ours, "no coloured wool dropped");
			helper.assertFalse(vanilla, net.minecraft.network.chat.Component.literal("vanilla white wool dropped"));
		});
	}

	/** A vanilla dye (setColor) clears our colour. */
	@GameTest
	public void sheepVanillaDyeClears(GameTestHelper helper) {
		floor(helper);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(4, 1, 4));
		SheepColors.set(sheep, first());
		sheep.setColor(DyeColor.RED);
		helper.assertTrue(SheepColors.get(sheep) == null, "our colour survived a vanilla dye");
		helper.assertValueEqual(sheep.getColor(), DyeColor.RED, "vanilla colour");
		helper.succeed();
	}

	/** Death loot: our wool plus mutton, never white wool. */
	@GameTest(maxTicks = 100)
	public void sheepDeathLoot(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(4, 1, 4);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, pos);
		SheepColors.set(sheep, first());
		sheep.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
		helper.succeedWhen(() -> {
			List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 3.0);
			helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(block(Family.WOOL).asItem())), "no coloured wool");
			helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.MUTTON)), "no mutton");
			helper.assertFalse(drops.stream().anyMatch(e -> e.getItem().is(Items.WOOL.white())),
					net.minecraft.network.chat.Component.literal("white wool dropped"));
		});
	}

	/** What a vanilla client is told about a dyed sheep: invisible and sheared. */
	@GameTest
	public void sheepOverlayHidesVanillaSheep(GameTestHelper helper) {
		floor(helper);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(4, 1, 4));
		SheepColors.set(sheep, first());
		List<SynchedEntityData.DataValue<?>> data = new ArrayList<>(sheep.getEntityData().getNonDefaultValues());
		new SheepOverlay(sheep).modifyRawTrackedData(data, null, true);
		boolean invisible = data.stream().anyMatch(v -> v.id() == 0 && v.value() instanceof Byte b && (b & 0x20) != 0);
		helper.assertTrue(invisible, "sheep not sent invisible");
		helper.succeed();
	}

	/** Every non-mod banner pattern gets one derived pattern per colour, with a tinted texture id. */
	@GameTest
	public void bannerPatternsDerived(GameTestHelper helper) {
		Registry<BannerPattern> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		long source = registry.entrySet().stream().filter(e -> !e.getKey().identifier().getNamespace().equals(MoreDyes.MOD_ID)).count();
		long derived = registry.entrySet().stream().filter(e -> e.getKey().identifier().getNamespace().equals(MoreDyes.MOD_ID)).count();
		helper.assertValueEqual(derived, source * ModColors.all().size(), "derived pattern count");
		Identifier creeper = BannerPatterns.derivedId(first(), Identifier.withDefaultNamespace("creeper"));
		helper.assertTrue(registry.containsKey(creeper), "missing " + creeper);
		helper.assertTrue(BannerPatterns.derived(creeper) != null, "derived record missing for " + creeper);
		helper.succeed();
	}

	/**
	 * A stair, slab or pane breaking shows its material's particles. Replays what Polymer does to a
	 * break event for a client with the pack: the break-event chain, then the textured-block mapper,
	 * which turns any donor state it is handed back into the plain vanilla block.
	 */
	@GameTest
	public void breakParticlesUseTheMaterial(GameTestHelper helper) {
		Family[][] shapes = {
				{Family.WOOL_STAIRS, Family.WOOL}, {Family.WOOL_SLAB, Family.WOOL},
				{Family.CONCRETE_STAIRS, Family.CONCRETE}, {Family.CONCRETE_SLAB, Family.CONCRETE},
				{Family.STAINED_GLASS_PANE, Family.STAINED_GLASS}};
		for (Family[] pair : shapes) {
			Block shape = block(pair[0]);
			BlockState expected = ((PolymerBlock) block(pair[1])).getPolymerBlockState(block(pair[1]).defaultBlockState(), null);
			BlockState sent = BlockExtBlockMapper.INSTANCE.toClientSideState(
					PolymerBlockUtils.getBlockBreakBlockStateSafely((PolymerBlock) shape, shape.defaultBlockState(), 32, null), null);
			helper.assertValueEqual(sent, expected, pair[0].id + " break particles");
		}
		helper.succeed();
	}

	/** Adding a candle updates the item display players already see, not just the server copy. */
	@GameTest
	public void candleDisplayUpdatesInPlace(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, block(Family.CANDLE));
		helper.setBlock(pos, block(Family.CANDLE).defaultBlockState().setValue(CandleBlock.CANDLES, 2));
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		helper.assertTrue(attachment != null, "no display holder");
		ItemDisplayElement display = (ItemDisplayElement) attachment.holder().getElements().getFirst();
		helper.assertTrue(String.valueOf(display.getItem().get(DataComponents.ITEM_MODEL)).endsWith("_two_candles"),
				"display model " + display.getItem().get(DataComponents.ITEM_MODEL));
		helper.assertFalse(display.getSyncedData().isDirty(),
				net.minecraft.network.chat.Component.literal("display change was never sent to watching players"));
		helper.succeed();
	}

	/** Our candle on a vanilla cake becomes our candle cake; breaking it gives the candle back. */
	@GameTest
	public void candleOnCake(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, Blocks.CAKE);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block(Family.CANDLE)));
		helper.useBlock(pos, player);
		helper.assertBlockPresent(block(Family.CANDLE_CAKE), pos);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "candle not consumed");
		breakWithDrops(helper, pos);
		helper.succeedWhen(() -> helper.assertItemEntityCountIs(block(Family.CANDLE).asItem(), pos, 2.0, 1));
	}

	/** Glass is translucent in the model data the client gets, and panes connect like vanilla panes. */
	@GameTest
	public void glassAndPanes(GameTestHelper helper) {
		floor(helper);
		Block glass = block(Family.STAINED_GLASS);
		Block pane = block(Family.STAINED_GLASS_PANE);
		helper.assertTrue(!glass.defaultBlockState().canOcclude(), "glass occludes");
		// setBlock skips placement logic, so connections come from neighbour-shape updates: place
		// ours first, then its neighbours (and re-set ours to update the vanilla pane).
		helper.setBlock(new BlockPos(3, 1, 2), pane);
		helper.setBlock(new BlockPos(2, 1, 2), glass);
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.GLASS_PANE);
		BlockState ours = helper.getBlockState(new BlockPos(3, 1, 2));
		helper.assertTrue(ours.getValue(IronBarsBlock.WEST) && ours.getValue(IronBarsBlock.EAST), "our pane did not connect: " + ours);
		helper.assertTrue(!ours.getValue(IronBarsBlock.NORTH), "our pane connected to air");
		helper.setBlock(new BlockPos(3, 1, 2), ours.setValue(IronBarsBlock.WATERLOGGED, true));
		BlockState vanilla = helper.getBlockState(new BlockPos(4, 1, 2));
		helper.assertTrue(vanilla.getValue(IronBarsBlock.WEST), "vanilla pane did not connect to ours: " + vanilla);
		BlockState client = ((PolymerBlock) pane).getPolymerBlockState(ours, null);
		helper.assertTrue(client.getBlock() instanceof IronBarsBlock && client.getValue(IronBarsBlock.WEST)
				&& client.getValue(IronBarsBlock.EAST) && !client.getValue(IronBarsBlock.NORTH), "pane donor shape mismatch: " + client);
		helper.succeed();
	}

	private static ItemStack craft(GameTestHelper helper, ItemStack... grid) {
		CraftingInput input = CraftingInput.of(3, 3, List.of(grid));
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	private static ItemStack[] grid(ItemStack... items) {
		ItemStack[] grid = new ItemStack[9];
		java.util.Arrays.fill(grid, ItemStack.EMPTY);
		System.arraycopy(items, 0, grid, 0, items.length);
		return grid;
	}

	private static Holder<BannerPattern> pattern(GameTestHelper helper, Identifier id) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)
				.getOrThrow(ResourceKey.create(Registries.BANNER_PATTERN, id));
	}

	private static List<BannerPatternLayers.Layer> layers(ItemStack stack) {
		return stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
	}

	/** A Cerise banner with a red bottom stripe on it, as a vanilla loom would make it. */
	private static ItemStack striped(GameTestHelper helper) {
		ItemStack banner = BannerPatterns.colouredBanner(first(), helper.getLevel().registryAccess());
		List<BannerPatternLayers.Layer> list = new ArrayList<>(layers(banner));
		list.add(new BannerPatternLayers.Layer(pattern(helper, Identifier.withDefaultNamespace("stripe_bottom")), DyeColor.RED));
		banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(list));
		return banner;
	}

	/**
	 * Six of our wool over a stick, through the real recipe manager: a white banner whose one layer
	 * is our full-cover pattern in white, named for the colour, in vanilla's banner group.
	 */
	@GameTest
	public void colouredBannerRecipe(GameTestHelper helper) {
		for (ModColor color : ModColors.all()) {
			ItemStack wool = new ItemStack(ModContent.block(color, Family.WOOL));
			ItemStack out = craft(helper, wool, wool, wool, wool, wool, wool, ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY);
			helper.assertTrue(out.is(Items.BANNER.white()), "no white banner from " + color.id() + " wool, got " + out);
			List<BannerPatternLayers.Layer> layers = layers(out);
			helper.assertValueEqual(layers.size(), 1, color.id() + " banner layers");
			helper.assertValueEqual(layers.getFirst().pattern().unwrapKey().map(ResourceKey::identifier).orElse(null),
					BannerPatterns.derivedId(color, BannerPatterns.BASE), color.id() + " banner base pattern");
			helper.assertValueEqual(layers.getFirst().color(), DyeColor.WHITE, color.id() + " banner layer colour");
			helper.assertTrue(BannerPatterns.isColouredBase(layers.getFirst()), "base layer not recognised as ours");
			var name = out.get(DataComponents.ITEM_NAME);
			helper.assertTrue(name != null && name.getContents() instanceof TranslatableContents t
					&& t.getKey().equals(BannerPatterns.bannerNameKey(color)), color.id() + " banner name: " + name);
			helper.assertTrue(ItemStack.isSameItemSameComponents(out, BannerPatterns.colouredBanner(color, helper.getLevel().registryAccess())),
					"recipe result differs from colouredBanner(): " + out.getComponentsPatch());
			var recipe = helper.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE,
					Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, color.id() + "_banner")));
			helper.assertTrue(recipe.isPresent() && recipe.get().value().group().equals("banner"), "recipe missing or not in the banner group");
		}
		helper.succeed();
	}

	/** Placed, the banner keeps its layer and name; broken, it drops the very same item. */
	@GameTest
	public void colouredBannerPlacesAndBreaks(GameTestHelper helper) {
		floor(helper);
		ItemStack banner = BannerPatterns.colouredBanner(first(), helper.getLevel().registryAccess());
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, banner.copy());
		helper.placeAt(player, banner.copy(), new BlockPos(2, 0, 2), Direction.UP);
		BlockPos pos = new BlockPos(2, 1, 2);
		BannerBlockEntity placed = helper.getBlockEntity(pos, BannerBlockEntity.class);
		helper.assertValueEqual(placed.getPatterns(), banner.get(DataComponents.BANNER_PATTERNS), "placed banner layers");
		helper.assertTrue(ItemStack.isSameItemSameComponents(placed.getItem(), banner), "picked banner differs: " + placed.getItem().getComponentsPatch());
		breakWithDrops(helper, pos);
		helper.succeedWhen(() -> {
			List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
			helper.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameComponents(drops.getFirst().getItem(), banner),
					"drop differs: " + drops.stream().map(e -> e.getItem().getComponentsPatch()).toList());
		});
	}

	/** Vanilla's loom adds a pattern on top; a shield takes the banner; a plain white banner copies it. */
	@GameTest
	public void colouredBannerLoomShieldCopy(GameTestHelper helper) {
		ItemStack banner = BannerPatterns.colouredBanner(first(), helper.getLevel().registryAccess());
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		LoomMenu loom = new LoomMenu(0, player.getInventory());
		loom.getBannerSlot().set(banner.copy());
		loom.getDyeSlot().set(new ItemStack(Items.DYE.red()));
		Holder<BannerPattern> stripe = pattern(helper, Identifier.withDefaultNamespace("stripe_bottom"));
		int index = loom.getSelectablePatterns().indexOf(stripe);
		helper.assertTrue(index >= 0, "the loom offers no bottom stripe");
		loom.clickMenuButton(player, index);
		ItemStack woven = loom.getResultSlot().getItem();
		helper.assertTrue(ItemStack.isSameItemSameComponents(woven, striped(helper)), "loom result: " + woven.getComponentsPatch());

		ItemStack shield = craft(helper, grid(new ItemStack(Items.SHIELD), banner.copy()));
		helper.assertTrue(shield.is(Items.SHIELD), "no shield, got " + shield);
		helper.assertValueEqual(shield.get(DataComponents.BASE_COLOR), DyeColor.WHITE, "shield base colour");
		helper.assertValueEqual(shield.get(DataComponents.BANNER_PATTERNS), banner.get(DataComponents.BANNER_PATTERNS), "shield layers");

		ItemStack copy = craft(helper, grid(striped(helper), new ItemStack(Items.BANNER.white())));
		helper.assertTrue(ItemStack.isSameItemSameComponents(copy, striped(helper)), "copy: " + copy.getComponentsPatch());
		helper.succeed();
	}

	/** A cauldron washes patterns off down to our colour, never the colour itself, as with a banner's own. */
	@GameTest
	public void colouredBannerWash(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
		ItemStack plain = BannerPatterns.colouredBanner(first(), helper.getLevel().registryAccess());

		player.setItemInHand(InteractionHand.MAIN_HAND, striped(helper));
		var washed = helper.getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(washed.consumesAction(), "the stripe did not wash off: " + washed);
		helper.assertTrue(ItemStack.isSameItemSameComponents(player.getMainHandItem(), plain), "washed: " + player.getMainHandItem().getComponentsPatch());
		helper.assertValueEqual(helper.getBlockState(pos).getValue(LayeredCauldronBlock.LEVEL), 2, "cauldron level after a wash");

		var again = helper.getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(!again.consumesAction(), "a plain coloured banner washed: " + again);
		helper.assertTrue(ItemStack.isSameItemSameComponents(player.getMainHandItem(), plain), "colour washed off: " + player.getMainHandItem().getComponentsPatch());
		helper.assertValueEqual(helper.getBlockState(pos).getValue(LayeredCauldronBlock.LEVEL), 2, "cauldron level after no wash");
		helper.succeed();
	}

	/**
	 * A Cerise banner with {@code patterns} vanilla patterns on top of its colour, in red, as a loom
	 * would make it (the colour is the first of the banner's layers).
	 */
	private static ItemStack withPatterns(GameTestHelper helper, int patterns) {
		ItemStack banner = BannerPatterns.colouredBanner(first(), helper.getLevel().registryAccess());
		List<BannerPatternLayers.Layer> list = new ArrayList<>(layers(banner));
		String[] sources = {"stripe_bottom", "stripe_top", "stripe_left", "stripe_right", "cross", "border", "circle"};
		for (int i = 0; i < patterns; i++) {
			list.add(new BannerPatternLayers.Layer(pattern(helper, Identifier.withDefaultNamespace(sources[i])), DyeColor.RED));
		}
		banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(list));
		return banner;
	}

	/** A plain banner of a vanilla colour with {@code layers} patterns. */
	private static ItemStack vanillaWithPatterns(GameTestHelper helper, int layers) {
		ItemStack banner = new ItemStack(Items.BANNER.red());
		List<BannerPatternLayers.Layer> list = new ArrayList<>();
		for (int i = 0; i < layers; i++) {
			list.add(new BannerPatternLayers.Layer(pattern(helper, Identifier.withDefaultNamespace(i % 2 == 0 ? "stripe_top" : "stripe_bottom")), DyeColor.WHITE));
		}
		banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(list));
		return banner;
	}

	private static int patternIndex(GameTestHelper helper, LoomMenu loom, String source) {
		int index = loom.getSelectablePatterns().indexOf(pattern(helper, Identifier.withDefaultNamespace(source)));
		helper.assertTrue(index >= 0, "the loom offers no " + source);
		return index;
	}

	/**
	 * Our dye in vanilla's loom: the dye slot takes it, and the pattern comes out in our colour (the
	 * derived pattern, applied with WHITE), on a white banner and on top of a Cerise one. Taking the
	 * result uses one banner and one dye. The client's copy of the dye says the nearest vanilla colour,
	 * so the client's loom screen takes it too; the server's copy has no dye component.
	 */
	@GameTest
	public void loomWeavesWithOurDye(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		LoomMenu loom = new LoomMenu(0, player.getInventory());
		ItemStack dye = new ItemStack(ModContent.dye(first()), 2);
		helper.assertTrue(loom.getDyeSlot().mayPlace(dye), "the loom's dye slot refuses our dye");
		loom.getBannerSlot().set(new ItemStack(Items.BANNER.white(), 2));
		loom.getDyeSlot().set(dye);
		loom.clickMenuButton(player, patternIndex(helper, loom, "stripe_bottom"));
		Holder<BannerPattern> ours = pattern(helper, BannerPatterns.derivedId(first(), Identifier.withDefaultNamespace("stripe_bottom")));
		ItemStack woven = loom.getResultSlot().getItem().copy();
		helper.assertTrue(woven.is(Items.BANNER.white()) && woven.getCount() == 1, "loom result: " + woven);
		helper.assertValueEqual(layers(woven), List.of(new BannerPatternLayers.Layer(ours, DyeColor.WHITE)), "loom result layers");

		loom.clicked(loom.getResultSlot().index, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(ItemStack.isSameItemSameComponents(loom.getCarried(), woven) && loom.getCarried().getCount() == 1,
				"taken: " + loom.getCarried());
		helper.assertValueEqual(loom.getBannerSlot().getItem().getCount(), 1, "banners left after a take");
		helper.assertValueEqual(loom.getDyeSlot().getItem().getCount(), 1, "dyes left after a take");

		loom.getBannerSlot().set(BannerPatterns.colouredBanner(first(), helper.getLevel().registryAccess()));
		loom.clickMenuButton(player, patternIndex(helper, loom, "stripe_bottom"));
		List<BannerPatternLayers.Layer> onColour = layers(loom.getResultSlot().getItem());
		helper.assertTrue(onColour.size() == 2 && BannerPatterns.isColouredBase(onColour.getFirst())
				&& onColour.getLast().equals(new BannerPatternLayers.Layer(ours, DyeColor.WHITE)), "on a Cerise banner: " + onColour);

		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		ItemStack client = PolymerItemUtils.getPolymerItemStack(new ItemStack(ModContent.dye(first())),
				viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
		helper.assertValueEqual(client.get(DataComponents.DYE), SignColors.nearestDye(first()), "the client's dye component");
		helper.assertFalse(new ItemStack(ModContent.dye(first())).has(DataComponents.DYE), "the server's dye has a dye component");
		helper.succeed();
	}

	/** Shift-clicked in a loom, our dye goes to the dye slot, as a vanilla dye does; a stick does not. */
	@GameTest
	public void loomQuickMovesOurDye(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(ModContent.dye(first()), 3));
		player.getInventory().setItem(1, new ItemStack(Items.STICK));
		LoomMenu loom = new LoomMenu(0, player.getInventory());
		loom.quickMoveStack(player, menuSlot(loom, player, 0));
		helper.assertTrue(loom.getDyeSlot().getItem().is(ModContent.dye(first())) && loom.getDyeSlot().getItem().getCount() == 3,
				"dye slot after a shift-click: " + loom.getDyeSlot().getItem());
		helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "the dye stayed in the hotbar");
		loom.quickMoveStack(player, menuSlot(loom, player, 1));
		helper.assertTrue(loom.getDyeSlot().getItem().is(ModContent.dye(first())), "a stick went to the dye slot");
		helper.assertFalse(loom.getDyeSlot().mayPlace(new ItemStack(Items.STICK)), "the dye slot takes a stick");
		helper.succeed();
	}

	private static int menuSlot(LoomMenu loom, Player player, int inventorySlot) {
		for (var slot : loom.slots) {
			if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) return slot.index;
		}
		throw new IllegalStateException("no menu slot for inventory slot " + inventorySlot);
	}

	/**
	 * The loom does not count our colour as a pattern: a Cerise banner takes six patterns on top (seven
	 * layers) and no seventh, with our dye or a vanilla one. A banner of a vanilla colour still stops at six
	 * layers.
	 */
	@GameTest
	public void loomTakesSixOnACeriseBanner(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		for (ItemStack dye : List.of(new ItemStack(ModContent.dye(first())), new ItemStack(Items.DYE.red()))) {
			LoomMenu loom = new LoomMenu(0, player.getInventory());
			loom.getBannerSlot().set(withPatterns(helper, 5));
			loom.getDyeSlot().set(dye.copy());
			loom.clickMenuButton(player, patternIndex(helper, loom, "stripe_middle"));
			List<BannerPatternLayers.Layer> seven = layers(loom.getResultSlot().getItem());
			helper.assertValueEqual(seven.size(), 7, "layers after the sixth pattern with " + dye);
			helper.assertTrue(BannerPatterns.isColouredBase(seven.getFirst()), "the colour is no longer the first layer");
			loom.getBannerSlot().set(withPatterns(helper, 6));
			helper.assertTrue(loom.getResultSlot().getItem().isEmpty(), "the loom wove a seventh pattern with " + dye + ": "
					+ layers(loom.getResultSlot().getItem()).size() + " layers");
			loom.getBannerSlot().set(vanillaWithPatterns(helper, 5));
			loom.clickMenuButton(player, patternIndex(helper, loom, "stripe_middle"));  // a full banner cleared the pick
			helper.assertValueEqual(layers(loom.getResultSlot().getItem()).size(), 6, "layers after the sixth on a red banner with " + dye);
			loom.getBannerSlot().set(vanillaWithPatterns(helper, 6));
			helper.assertTrue(loom.getResultSlot().getItem().isEmpty(), "the loom wove a seventh layer on a red banner with " + dye);
		}
		helper.succeed();
	}

	/**
	 * The vanilla client's loom screen counts the banner's layers itself and lists no patterns from six on.
	 * So a Cerise banner with five patterns on top is sent to the client as those five on a plain banner of
	 * the nearest vanilla colour, in the banner slot only: on opening, and again whenever the client says it
	 * holds the real banner (as it does once it has put it in). The menu keeps the real banner, and the
	 * result the client is sent is the real one, seven layers.
	 */
	@GameTest
	public void loomShowsTheClientRoomForASixth(GameTestHelper helper) {
		ItemStack five = withPatterns(helper, 5);
		ItemStack view = BannerPatterns.loomClientView(five);
		helper.assertTrue(view.is(Items.BANNER.pick(SignColors.nearestDye(first()))) && view.getCount() == five.getCount(), "the client's view: " + view);
		helper.assertValueEqual(layers(view), layers(five).subList(1, 6), "the client's view's layers");
		helper.assertValueEqual(view.get(DataComponents.ITEM_NAME), five.get(DataComponents.ITEM_NAME), "the client's view's name");
		for (ItemStack other : List.of(withPatterns(helper, 4), withPatterns(helper, 6), vanillaWithPatterns(helper, 6), new ItemStack(Items.BANNER.white()))) {
			helper.assertTrue(BannerPatterns.loomClientView(other) == other, "the client's view changes " + layers(other));
		}

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		LoomMenu loom = new LoomMenu(0, player.getInventory());
		int banner = loom.getBannerSlot().index;
		loom.getBannerSlot().set(five.copy());
		loom.getDyeSlot().set(new ItemStack(ModContent.dye(first())));
		Sent sent = new Sent();
		loom.setSynchronizer(sent);
		helper.assertTrue(ItemStack.matches(sent.slots.get(banner), view), "sent on opening: " + sent.slots.get(banner));
		sent.slots.clear();
		loom.setRemoteSlot(banner, five);
		loom.broadcastChanges();
		helper.assertTrue(ItemStack.matches(sent.slots.get(banner), view), "sent after the client held the real banner: " + sent.slots.get(banner));
		sent.slots.clear();
		loom.broadcastChanges();
		helper.assertFalse(sent.slots.containsKey(banner), "the view sent again with nothing changed");

		loom.clickMenuButton(player, patternIndex(helper, loom, "stripe_middle"));
		loom.broadcastChanges();
		List<BannerPatternLayers.Layer> result = layers(sent.slots.getOrDefault(loom.getResultSlot().index, ItemStack.EMPTY));
		helper.assertTrue(result.size() == 7 && BannerPatterns.isColouredBase(result.getFirst()), "the result sent: " + result);
		helper.assertTrue(ItemStack.matches(loom.getBannerSlot().getItem(), five), "the menu's banner: " + layers(loom.getBannerSlot().getItem()));
		helper.assertFalse(sent.slots.containsKey(banner), "the banner slot sent again on a pattern pick");
		helper.succeed();
	}

	/** Records what a menu sends its client, and remembers it as the client's slots. */
	private static final class Sent implements ContainerSynchronizer {
		final Map<Integer, ItemStack> slots = new HashMap<>();

		@Override
		public void sendInitialData(AbstractContainerMenu menu, List<ItemStack> items, ItemStack carried, int[] data) {
			for (int i = 0; i < items.size(); i++) slots.put(i, items.get(i));
		}

		@Override
		public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
			slots.put(slot, stack);
		}

		@Override
		public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {}

		@Override
		public void sendDataChange(AbstractContainerMenu menu, int id, int value) {}

		@Override
		public RemoteSlot createSlot() {
			return new RemoteSlot() {
				private ItemStack remote = ItemStack.EMPTY;

				@Override
				public void force(ItemStack stack) {
					remote = stack.copy();
				}

				@Override
				public void receive(HashedStack hash) {}

				@Override
				public boolean matches(ItemStack stack) {
					return ItemStack.matches(remote, stack);
				}
			};
		}
	}

	/**
	 * Our dye keeps the loom's rule for pattern items: the globe is listed only with the globe pattern item
	 * in the loom (as kultur's Pirkko needs its own), comes out in our colour, and the item stays.
	 */
	@GameTest
	public void loomGatesPatternItemsWithOurDye(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		LoomMenu loom = new LoomMenu(0, player.getInventory());
		loom.getBannerSlot().set(new ItemStack(Items.BANNER.white()));
		loom.getDyeSlot().set(new ItemStack(ModContent.dye(first())));
		Identifier globe = Identifier.withDefaultNamespace("globe");
		helper.assertFalse(loom.getSelectablePatterns().contains(pattern(helper, globe)), "the globe is listed without its pattern item");
		loom.getPatternSlot().set(new ItemStack(Items.GLOBE_BANNER_PATTERN));
		loom.clickMenuButton(player, patternIndex(helper, loom, "globe"));
		helper.assertValueEqual(layers(loom.getResultSlot().getItem()),
				List.of(new BannerPatternLayers.Layer(pattern(helper, BannerPatterns.derivedId(first(), globe)), DyeColor.WHITE)), "the globe in our colour");
		loom.clicked(loom.getResultSlot().index, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(layers(loom.getCarried()).size() == 1, "taken: " + loom.getCarried());
		helper.assertTrue(loom.getPatternSlot().getItem().is(Items.GLOBE_BANNER_PATTERN), "the pattern item was used up");
		helper.succeed();
	}

	/**
	 * Seven layers (our colour and six patterns) survive everything a banner goes through: copying
	 * onto a white banner, decorating a shield, a save and load of the placed block, and a wash, which
	 * takes the top pattern off. A banner of a vanilla colour with seven layers still does not copy.
	 */
	@GameTest
	public void sevenLayersCopyShieldWash(GameTestHelper helper) {
		ItemStack seven = withPatterns(helper, 6);
		ItemStack copy = craft(helper, grid(seven.copy(), new ItemStack(Items.BANNER.white())));
		helper.assertTrue(ItemStack.isSameItemSameComponents(copy, seven), "copy: " + copy.getComponentsPatch());
		helper.assertTrue(craft(helper, grid(vanillaWithPatterns(helper, 7), new ItemStack(Items.BANNER.red()))).isEmpty(),
				"a red banner with seven layers copied");
		helper.assertTrue(craft(helper, grid(withPatterns(helper, 7), new ItemStack(Items.BANNER.white()))).isEmpty(),
				"a Cerise banner with seven patterns copied");

		ItemStack shield = craft(helper, grid(new ItemStack(Items.SHIELD), seven.copy()));
		helper.assertValueEqual(shield.get(DataComponents.BANNER_PATTERNS), seven.get(DataComponents.BANNER_PATTERNS), "shield layers");

		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.BANNER.white());
		BannerBlockEntity placed = helper.getBlockEntity(new BlockPos(2, 1, 2), BannerBlockEntity.class);
		placed.applyComponentsFromItemStack(seven);
		var saved = placed.saveWithFullMetadata(helper.getLevel().registryAccess());
		var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(at, placed.getBlockState(), saved, helper.getLevel().registryAccess());
		helper.assertTrue(loaded instanceof BannerBlockEntity b && b.getPatterns().equals(seven.get(DataComponents.BANNER_PATTERNS)),
				"loaded banner: " + (loaded instanceof BannerBlockEntity b ? b.getPatterns() : loaded));

		BlockPos cauldron = new BlockPos(4, 1, 2);
		helper.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, seven.copy());
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(cauldron)), Direction.UP, helper.absolutePos(cauldron), false);
		helper.getBlockState(cauldron).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(ItemStack.isSameItemSameComponents(player.getMainHandItem(), withPatterns(helper, 5)),
				"washed: " + layers(player.getMainHandItem()));
		helper.succeed();
	}

	/** Placed by a player, a seven-layer banner keeps every layer; broken, it drops the very same item. */
	@GameTest
	public void sevenLayersPlaceAndBreak(GameTestHelper helper) {
		floor(helper);
		ItemStack seven = withPatterns(helper, 6);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, seven.copy());
		helper.placeAt(player, seven.copy(), new BlockPos(2, 0, 2), Direction.UP);
		BlockPos pos = new BlockPos(2, 1, 2);
		BannerBlockEntity placed = helper.getBlockEntity(pos, BannerBlockEntity.class);
		helper.assertValueEqual(placed.getPatterns(), seven.get(DataComponents.BANNER_PATTERNS), "placed banner layers");
		breakWithDrops(helper, pos);
		helper.succeedWhen(() -> {
			List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
			helper.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameComponents(drops.getFirst().getItem(), seven),
					"drop differs: " + drops.stream().map(e -> layers(e.getItem()).size()).toList());
		});
	}

	/** Leather armour takes our dye (exact RGB), and mixes with a vanilla dye by vanilla's rule. */
	@GameTest
	public void armorDye(GameTestHelper helper) {
		ItemStack dye = new ItemStack(ModContent.dye(first()));
		ItemStack out = craft(helper, grid(new ItemStack(Items.LEATHER_CHESTPLATE), dye));
		helper.assertTrue(out.is(Items.LEATHER_CHESTPLATE), "no dyed chestplate, got " + out);
		DyedItemColor color = out.get(DataComponents.DYED_COLOR);
		helper.assertTrue(color != null && (color.rgb() & 0xFFFFFF) == (first().rgb() & 0xFFFFFF),
				"expected " + Integer.toHexString(first().rgb() & 0xFFFFFF) + ", got " + color);

		ItemStack mixed = craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), dye, new ItemStack(Items.DYE.white())));
		DyedItemColor mixedColor = mixed.get(DataComponents.DYED_COLOR);
		helper.assertTrue(mixedColor != null && (mixedColor.rgb() & 0xFFFFFF) != first().rgb(), "vanilla dye ignored in mix: " + mixedColor);

		ItemStack wolf = craft(helper, grid(new ItemStack(Items.WOLF_ARMOR), dye));
		helper.assertTrue(wolf.is(Items.WOLF_ARMOR) && wolf.has(DataComponents.DYED_COLOR), "wolf armour not dyed: " + wolf);
		helper.succeed();
	}

	/** Firework stars take our dye as a colour and as a fade colour; shape and trail still work. */
	@GameTest
	public void fireworkStar(GameTestHelper helper) {
		ItemStack dye = new ItemStack(ModContent.dye(first()));
		ItemStack star = craft(helper, grid(new ItemStack(Items.GUNPOWDER), dye, new ItemStack(Items.FIRE_CHARGE), new ItemStack(Items.DIAMOND)));
		helper.assertTrue(star.is(Items.FIREWORK_STAR), "no star, got " + star);
		FireworkExplosion explosion = star.get(DataComponents.FIREWORK_EXPLOSION);
		helper.assertTrue(explosion != null && explosion.colors().equals(IntList.of(first().rgb())), "colours: " + explosion);
		helper.assertTrue(explosion.shape() == FireworkExplosion.Shape.LARGE_BALL && explosion.hasTrail(), "shape/trail: " + explosion);

		ItemStack faded = craft(helper, grid(star, dye, new ItemStack(Items.DYE.red())));
		FireworkExplosion fade = faded.get(DataComponents.FIREWORK_EXPLOSION);
		helper.assertTrue(fade != null && fade.fadeColors().size() == 2 && fade.fadeColors().getInt(0) == first().rgb(), "fade: " + fade);
		helper.assertTrue(fade.colors().equals(explosion.colors()), "fade recipe lost colours");
		helper.succeed();
	}

	/** A bundle re-dyed with our dye keeps its contents and is a bundle for vanilla's tag recipes. */
	@GameTest
	public void bundleDye(GameTestHelper helper) {
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		BundleContents.Mutable contents = BundleContents.EMPTY.asMutable();
		contents.tryInsert(new ItemStack(Items.DIAMOND, 4));
		bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
		ItemStack ours = craft(helper, grid(bundle, new ItemStack(ModContent.dye(first()))));
		helper.assertTrue(ours.is(ModContent.bundle(first())), "no coloured bundle, got " + ours);
		BundleContents kept = ours.get(DataComponents.BUNDLE_CONTENTS);
		helper.assertTrue(kept != null && !kept.isEmpty(), "contents lost");
		helper.assertTrue(ours.is(ItemTags.BUNDLES), "not in #minecraft:bundles");
		ItemStack back = craft(helper, grid(ours, new ItemStack(Items.DYE.white())));
		helper.assertTrue(back.is(Items.DYED_BUNDLE.white()), "vanilla dye did not take our bundle back: " + back);
		helper.succeed();
	}

	/** config/moredyes.json5: a family's look can be forced; one the file doesn't name, or the default, is auto. */
	@GameTest
	public void configSetsAFamilysLook(GameTestHelper helper) {
		var json = com.google.gson.JsonParser.parseString("{\"looks\": {\"stained_glass_pane\": \"display\"}}");
		var forced = MoreDyesConfig.CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, json)
				.getOrThrow(message -> new IllegalStateException("config does not read: " + message));
		if (forced.look(Family.STAINED_GLASS_PANE) != MoreDyesConfig.LookChoice.DISPLAY) {
			helper.fail("\"display\" in the file does not force the display look");
		}
		var empty = MoreDyesConfig.CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonObject())
				.getOrThrow(message -> new IllegalStateException("an empty file does not read: " + message));
		if (empty.look(Family.STAINED_GLASS_PANE) != MoreDyesConfig.LookChoice.AUTO) helper.fail("an empty file is not auto");
		if (MoreDyesConfig.get().look(Family.STAINED_GLASS_PANE) != MoreDyesConfig.LookChoice.AUTO) {
			helper.fail("the default config is not auto");
		}
		helper.succeed();
	}

	/**
	 * Each copper-bars pool holds four waxed states and Polymer keeps one back for the invisible donor, so
	 * three colours fit and panes stay in the donor look. (Looks once subtracted that reserved state a
	 * second time, which tipped panes into the display look at three colours.)
	 */
	@GameTest
	public void panesStayDonorWithThreeColours(GameTestHelper helper) {
		if (MoreDyesConfig.get().look(Family.STAINED_GLASS_PANE) != MoreDyesConfig.LookChoice.AUTO
				|| System.getProperty("moredyes.look." + Family.STAINED_GLASS_PANE.id) != null) {
			helper.succeed(); // forced by config or -D: the planner's own decision is not what is shown
			return;
		}
		int colours = ModColors.all().size();
		if (colours <= 3 && Looks.of(Family.STAINED_GLASS_PANE) != Looks.Look.DONOR) {
			helper.fail("panes are " + Looks.of(Family.STAINED_GLASS_PANE) + " with " + colours + " colours; each bars pool fits three");
		}
		helper.succeed();
	}

	@GameTest
	public void animationParsesAndBlends(GameTestHelper helper) {
		var json = JsonParser.parseString("""
				{"id":"t","name":"T","rgb":"#FF0000","animation":{"frametime":10,"interpolate":true,
				"frames":["#FF0000",{"rgb":"#0000FF","ramp":["#000040","#8080FF"]}]}}""");
		ModColor c = ModColor.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		Animation a = c.animation().orElseThrow();
		helper.assertValueEqual(a.cycleTicks(), 20, "cycle");
		helper.assertValueEqual(a.rgbAt(0), 0xFF0000, "frame 0");
		helper.assertValueEqual(a.rgbAt(5), 0x7F007F, "halfway red->blue");   // truncated, like vanilla's (int) blend
		helper.assertValueEqual(a.rgbAt(10), 0x0000FF, "frame 1");
		helper.assertValueEqual(a.rgbAt(20), 0xFF0000, "wraps");
		helper.assertValueEqual(a.frames().get(1).rampDark(), 0x000040, "explicit ramp kept");
		helper.assertTrue(ModColors.get("cerise").animation().isEmpty(), "static colour has no animation");
		helper.assertValueEqual(a.stripes().size(), 2, "stripes default to the keyframes when there are fewer than three");
		var withStripes = Animation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"frametime\":10,\"frames\":[\"#FF0000\",\"#00FF00\",\"#0000FF\",\"#FFFFFF\"],\"stripes\":[\"#123456\"]}")).getOrThrow();
		helper.assertValueEqual(withStripes.stripes().size(), 1, "explicit stripes");
		helper.assertValueEqual(withStripes.stripes().get(0).rgb(), 0x123456, "explicit stripe colour");
		helper.succeed();
	}

	@GameTest
	public void animationRejectsBadInput(GameTestHelper helper) {
		for (String bad : List.of(
				"{\"frametime\":10,\"frames\":[\"#FF0000\"]}",
				"{\"frametime\":0,\"frames\":[\"#FF0000\",\"#00FF00\"]}")) {
			helper.assertTrue(Animation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(bad)).isError(), "accepted " + bad);
		}
		helper.succeed();
	}

	@GameTest
	public void animationStepsWithoutInterpolate(GameTestHelper helper) {
		var a = Animation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"frametime\":10,\"interpolate\":false,\"frames\":[\"#FF0000\",\"#0000FF\"]}")).getOrThrow();
		helper.assertValueEqual(a.rgbAt(9), 0xFF0000, "still frame 0");
		helper.succeed();
	}

	/**
	 * An animated colour's frames keep their source's grain, as vanilla's dyed blocks do (wool varies
	 * by about a tenth of its brightness, concrete by about a hundredth), and a bed's pillow, wooden
	 * frame and legs are vanilla's in every frame.
	 */
	@GameTest
	public void animatedTexturesKeepTheirGrain(GameTestHelper helper) {
		ModColor animated = animated();
		int frames = animated.animation().orElseThrow().frames().size();
		Map<String, Double> limit = Map.of("block/{c}_wool", 0.15, "block/{c}_concrete", 0.05);
		limit.forEach((p, max) -> {
			java.awt.image.BufferedImage strip = image(p.replace("{c}", animated.id()));
			int size = strip.getWidth();
			for (int frame = 0; frame < frames; frame += frames / 3) {
				double sum = 0, sq = 0;
				for (int y = 0; y < size; y++) {
					for (int x = 0; x < size; x++) {
						int c = strip.getRGB(x, frame * size + y);
						double l = 0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF);
						sum += l;
						sq += l * l;
					}
				}
				double mean = sum / (size * size), sd = Math.sqrt(Math.max(0, sq / (size * size) - mean * mean));
				helper.assertTrue(sd / mean < max, p + " frame " + frame + " varies by " + String.format("%.3f", sd / mean) + " of its brightness, over " + max);
			}
		});
		java.awt.image.BufferedImage bed = image("block/" + animated.id() + "_bed_foot_south");
		int size = bed.getWidth(), half = frames / 2;
		boolean woolMoved = false;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int a = bed.getRGB(x, y), b = bed.getRGB(x, half * size + y);
				if (y == size - 1) helper.assertValueEqual(b, a, "the bed's leg row at x=" + x + " changes between frames");
				if (a != b) woolMoved = true;
			}
		}
		helper.assertTrue(woolMoved, "the bed's wool does not animate");
		// the head end: the pillow (white in every vanilla bed) and the frame hold still, the blanket moves
		java.awt.image.BufferedImage head = image("block/" + animated.id() + "_bed_head_east");
		int still = 0, moving = 0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int a = head.getRGB(x, y), b = head.getRGB(x, half * size + y);
				if ((a >>> 24) == 0) continue;
				if (a == b) still++; else moving++;
			}
		}
		helper.assertTrue(moving > 0 && still > moving, "bed head side: " + still + " texels hold still, " + moving + " move; the pillow should hold still");
		// items keep what vanilla's dyed ones keep: the dye's outline, the bundle's string
		for (String item : List.of("item/" + animated.id() + "_dye", "item/" + animated.id() + "_bundle")) {
			java.awt.image.BufferedImage strip = image(item);
			int side = strip.getWidth(), held = 0, changed = 0;
			for (int y = 0; y < side; y++) {
				for (int x = 0; x < side; x++) {
					int a = strip.getRGB(x, y), b = strip.getRGB(x, half * side + y);
					if ((a >>> 24) == 0) continue;
					if (a == b) held++; else changed++;
				}
			}
			helper.assertTrue(held > 0 && changed > 0, item + ": " + held + " texels hold still, " + changed + " move");
		}
		helper.succeed();
	}

	/** Every generated texture of an animated colour ships as a strip of frames with its mcmeta; static and hand-drawn ones don't. */
	@GameTest
	public void animatedTexturesShipAsStrips(GameTestHelper helper) {
		ModColor rgb = animated();
		int frames = rgb.animation().orElseThrow().frames().size();
		for (String p : List.of("block/{c}_wool", "block/{c}_concrete", "block/{c}_stained_glass", "block/{c}_bed_head_up",
				"block/sheep/{c}_wool", "block/sheep/{c}_wool_hurt", "block/sheep/{c}_body_sheared", "block/{c}_terracotta",
				"entity/shulker/{c}", "item/{c}_dye")) {
			java.awt.image.BufferedImage still = image(p.replace("{c}", first().id()));
			java.awt.image.BufferedImage strip = image(p.replace("{c}", rgb.id()));
			helper.assertValueEqual(strip.getWidth(), still.getWidth(), p + " width");
			helper.assertValueEqual(strip.getHeight(), still.getHeight() * frames, p + " height");
			helper.assertTrue(resource(p.replace("{c}", rgb.id()) + ".png.mcmeta").contains("\"frametime\":150"), p + " mcmeta");
			helper.assertTrue(resourceOrNull(p.replace("{c}", first().id()) + ".png.mcmeta") == null, p + " static has mcmeta");
		}
		// a hand-drawn texture holds still, animated colour or not: the RGBouquet
		helper.assertValueEqual(image("block/" + rgb.id() + "_flower").getHeight(), 16, "the hand-drawn RGBouquet's height");
		helper.assertTrue(resourceOrNull("block/" + rgb.id() + "_flower.png.mcmeta") == null, "the hand-drawn RGBouquet has mcmeta");
		// frames that aren't square have to say so, or vanilla cuts the strip into squares
		String sheep = resource("block/sheep/" + rgb.id() + "_wool.png.mcmeta");
		helper.assertTrue(sheep.contains("\"width\":64") && sheep.contains("\"height\":32"), "sheep mcmeta frame size: " + sheep);
		// equipment textures aren't atlas sprites: they stay one static frame, in red, green and blue bands
		var stripes = rgb.animation().orElseThrow().stripes();
		helper.assertValueEqual(stripes.size(), 3, "stripes");
		for (String p : List.of("entity/equipment/llama_body/{c}", "entity/equipment/happy_ghast_body/{c}_harness")) {
			java.awt.image.BufferedImage tex = image(p.replace("{c}", rgb.id()));
			helper.assertValueEqual(tex.getHeight(), image(p.replace("{c}", first().id())).getHeight(), p + " height");
			helper.assertTrue(resourceOrNull(p.replace("{c}", rgb.id()) + ".png.mcmeta") == null, p + " has mcmeta");
			// each stripe colour shows up (bright and saturated, so not the leather), top to bottom in order
			double lastRow = -1;
			for (var stripe : stripes) {
				float hue = java.awt.Color.RGBtoHSB(stripe.rgb() >> 16 & 0xFF, stripe.rgb() >> 8 & 0xFF, stripe.rgb() & 0xFF, null)[0];
				double rows = 0;
				int count = 0;
				for (int py = 0; py < tex.getHeight(); py++) {
					for (int px = 0; px < tex.getWidth(); px++) {
						int c = tex.getRGB(px, py);
						if ((c >>> 24) < 128) continue;
						float[] hsb = java.awt.Color.RGBtoHSB(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, null);
						float off = Math.abs(hsb[0] - hue);
						if (Math.min(off, 1 - off) < 0.04f && hsb[1] > 0.5f && hsb[2] > 0.6f) {
							rows += py;
							count++;
						}
					}
				}
				helper.assertTrue(count > 0, p + " has no texels in stripe colour #" + Integer.toHexString(stripe.rgb()));
				helper.assertTrue(rows / count > lastRow, p + " stripes out of order at #" + Integer.toHexString(stripe.rgb()));
				lastRow = rows / count;
			}
		}
		helper.succeed();
	}

	/** Mean hue (0..1, as a point on the colour circle) of the visible pixels in one of {@code bands} equal bands of the visible rows. */
	/** An animated colour's banner mask is one tinted copy per keyframe, stacked. */
	@GameTest
	public void bannerMasksStackPerFrame(GameTestHelper helper) throws java.io.IOException {
		var white = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) white.setRGB(x, y, 0xFFFFFFFF);
		var in = new java.io.ByteArrayOutputStream();
		javax.imageio.ImageIO.write(white, "png", in);
		var out = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(BannerPatterns.multiplyFrames(in.toByteArray(), new int[]{0xFF0000, 0x00FF00})));
		helper.assertValueEqual(out.getWidth(), 2, "width");
		helper.assertValueEqual(out.getHeight(), 4, "height");
		for (int x = 0; x < 2; x++) {
			helper.assertValueEqual(out.getRGB(x, 0), 0xFFFF0000, "top frame");
			helper.assertValueEqual(out.getRGB(x, 1), 0xFFFF0000, "top frame");
			helper.assertValueEqual(out.getRGB(x, 2), 0xFF00FF00, "bottom frame");
			helper.assertValueEqual(out.getRGB(x, 3), 0xFF00FF00, "bottom frame");
		}
		helper.succeed();
	}

	private static int rgb(ItemStack stack) {
		return stack.get(DataComponents.DYED_COLOR).rgb() & 0xFFFFFF;
	}

	/** RGB dye turns armour RGB whatever colour it had. */
	@GameTest
	public void rgbDyeOverwritesArmour(GameTestHelper helper) {
		ItemStack blue = new ItemStack(Items.LEATHER_CHESTPLATE);
		blue.set(DataComponents.DYED_COLOR, new DyedItemColor(0x0000FF));
		ItemStack out = craft(helper, grid(blue, new ItemStack(ModContent.dye(animated()))));
		helper.assertValueEqual(rgb(out), animated().rgb() & 0xFFFFFF, "overwritten colour");
		helper.assertValueEqual(AnimatedArmor.animatedColour(out), Optional.of(animated()), "marked");
		helper.succeed();
	}

	/** Nothing else goes in the grid with RGB dye. */
	@GameTest
	public void rgbDyeDoesNotMix(GameTestHelper helper) {
		ItemStack rgb = new ItemStack(ModContent.dye(animated()));
		helper.assertTrue(craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), rgb.copy(), new ItemStack(Items.DYE.red()))).isEmpty(), "mixed with vanilla red");
		helper.assertTrue(craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), rgb.copy(), new ItemStack(ModContent.dye(first())))).isEmpty(), "mixed with ours");
		helper.assertTrue(craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), rgb.copy(), rgb.copy())).isEmpty(), "two RGB dyes");
		helper.succeed();
	}

	/** Dyeing RGB armour with ordinary dyes gives those dyes' colour alone, and ends the animation. */
	@GameTest
	public void normalDyeEndsTheAnimation(GameTestHelper helper) {
		ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_HELMET), new ItemStack(ModContent.dye(animated()))));
		for (ItemStack dye : List.of(new ItemStack(Items.DYE.red()), new ItemStack(ModContent.dye(first())))) {
			ItemStack out = craft(helper, grid(rb.copy(), dye));
			helper.assertValueEqual(out.get(DataComponents.DYED_COLOR), DyeStacks.mix(null, List.of(DyeStacks.armorRgb(dye))), "plain colour from " + dye);
			helper.assertTrue(AnimatedArmor.animatedColour(out).isEmpty(), "still animated after " + dye);
			helper.assertFalse(out.has(AnimatedArmor.ANIMATED_DYE), "marker left behind after " + dye);
		}
		helper.succeed();
	}

	/** A marker that no longer matches the armour's colour, or names nothing animated, is just plain armour. */
	@GameTest
	public void staleMarkersArePlain(GameTestHelper helper) {
		ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(ModContent.dye(animated()))));
		ItemStack washed = rb.copy();
		washed.remove(DataComponents.DYED_COLOR);
		ItemStack unknown = rb.copy();
		unknown.set(AnimatedArmor.ANIMATED_DYE, "no_such_colour");
		ItemStack staticId = rb.copy();
		staticId.set(AnimatedArmor.ANIMATED_DYE, first().id());
		ItemStack recoloured = rb.copy();
		recoloured.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
		for (ItemStack s : List.of(washed, unknown, staticId, recoloured)) {
			helper.assertTrue(AnimatedArmor.animatedColour(s).isEmpty(), "stale marker counted: " + s);
		}
		helper.assertTrue(AnimatedArmor.animatedColour(new ItemStack(Items.LEATHER_BOOTS)).isEmpty(), "unmarked armour counted");
		helper.succeed();
	}

	private static int clientRgb(GameTestHelper helper, ServerPlayer viewer, ItemStack stack) {
		ItemStack client = PolymerItemUtils.getPolymerItemStack(stack, viewer.connection.getPacketContext(), helper.getLevel().registryAccess());
		return rgb(client);
	}

	private static boolean near(int a, int b) {
		return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) <= 3 && Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) <= 3
				&& Math.abs((a & 0xFF) - (b & 0xFF)) <= 3;
	}

	private static String hex(int rgb) {
		return String.format("#%06X", rgb);
	}

	/** What a viewer is sent for RGB armour follows the clock of their own pack load; the server's stack never changes. */
	@GameTest
	public void armourFollowsTheViewersClock(GameTestHelper helper) {
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_CHESTPLATE), new ItemStack(ModContent.dye(animated()))));
		Animation a = animated().animation().orElseThrow();
		long now = System.currentTimeMillis();
		PackClock.loaded(viewer.getUUID(), now - 75 * 50);          // mid-keyframe 0
		int early = clientRgb(helper, viewer, rb);
		PackClock.loaded(viewer.getUUID(), now - 1875 * 50);        // a later load replaces the earlier one; mid-keyframe 12
		int late = clientRgb(helper, viewer, rb);
		helper.assertTrue(near(early, a.rgbAt(75)) && near(late, a.rgbAt(1875)), "phases " + hex(early) + " " + hex(late));
		helper.assertValueEqual(rgb(rb), animated().rgb() & 0xFFFFFF, "server stack untouched");
		helper.succeed();
	}

	/** A viewer whose pack load was never seen (declined, or the packet has not come yet) is sent the first keyframe. */
	@GameTest
	public void viewerWithoutPackSeesTheFirstKeyframe(GameTestHelper helper) {
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		PackClock.forget(viewer.getUUID());
		ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), new ItemStack(ModContent.dye(animated()))));
		helper.assertValueEqual(clientRgb(helper, viewer, rb), animated().animation().orElseThrow().rgbAt(0), "no clock");
		helper.succeed();
	}

	/** Armour that is no longer RGB (a stale marker) is sent as it is. */
	@GameTest
	public void staleMarkerIsSentAsItIs(GameTestHelper helper) {
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		PackClock.loaded(viewer.getUUID(), System.currentTimeMillis() - 1875 * 50);
		ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), new ItemStack(ModContent.dye(animated()))));
		rb.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
		helper.assertValueEqual(clientRgb(helper, viewer, rb), 0x123456, "recoloured armour");
		rb.set(AnimatedArmor.ANIMATED_DYE, "no_such_colour");
		helper.assertValueEqual(clientRgb(helper, viewer, rb), 0x123456, "unknown colour");
		helper.succeed();
	}

	/** Armour stands (and by the same route horses and wolves) are refreshed while they wear RGB, and not after. */
	@GameTest
	public void armourStandWearersComeAndGo(GameTestHelper helper) {
		var stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(1, 2, 1));
		ItemStack rb = craft(helper, grid(new ItemStack(Items.LEATHER_CHESTPLATE), new ItemStack(ModContent.dye(animated()))));
		stand.setItemSlot(EquipmentSlot.CHEST, rb);
		// EQUIPMENT_CHANGE fires from the entity's tick, not from setItemSlot: give it a couple of ticks
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(AnimatedArmor.wearers().contains(stand), "stand not tracked");
			stand.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			helper.runAfterDelay(2, () -> {
				helper.assertFalse(AnimatedArmor.wearers().contains(stand), "stand still tracked");
				helper.succeed();
			});
		});
	}
}
