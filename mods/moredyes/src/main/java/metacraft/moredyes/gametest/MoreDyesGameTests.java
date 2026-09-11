package metacraft.moredyes.gametest;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.banner.BannerPatterns;
import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.color.ModColors;
import metacraft.moredyes.content.ClientStates;
import metacraft.moredyes.content.Family;
import metacraft.moredyes.content.ModContent;
import metacraft.moredyes.sheep.SheepColors;
import metacraft.moredyes.sheep.SheepOverlay;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side game tests (Fabric GameTest API, vanilla test framework underneath).
 *
 * <p>Run headless: {@code ./gradlew runGametest} — starts a server, runs every test in an empty
 * 8×8×8 structure, writes {@code build/test-results/gametest.xml}, and fails the build on any
 * failure. Or in a normal world as an op: {@code /test runall} — the lectern/beacon feedback is
 * vanilla's test framework.
 *
 * <p>What these can and can't cover: everything a vanilla client would be sent (client states,
 * registry contents, tracked-data overrides), drops, and interactions. Not pixels: whether a
 * model looks right is still a visual check with a vanilla client.
 */
public final class MoreDyesGameTests {
	private static final BlockPos FLOOR = new BlockPos(0, 0, 0);

	private static ModColor first() {
		return ModColors.all().getFirst();
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

	/** Leather armour takes our dye (exact RGB), and mixes with a vanilla dye by vanilla's rule. */
	@GameTest
	public void armorDye(GameTestHelper helper) {
		ItemStack dye = new ItemStack(ModContent.dye(first()));
		ItemStack out = craft(helper, grid(new ItemStack(Items.LEATHER_CHESTPLATE), dye));
		helper.assertTrue(out.is(Items.LEATHER_CHESTPLATE), "no dyed chestplate, got " + out);
		DyedItemColor color = out.get(DataComponents.DYED_COLOR);
		helper.assertTrue(color != null && (color.rgb() & 0xFFFFFF) == first().rgb(),
				"expected " + Integer.toHexString(first().rgb()) + ", got " + color);

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
		BundleContents.Mutable contents = new BundleContents.Mutable(BundleContents.EMPTY);
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
}
