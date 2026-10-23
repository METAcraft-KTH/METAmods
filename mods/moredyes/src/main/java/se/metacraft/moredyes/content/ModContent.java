package se.metacraft.moredyes.content;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import eu.pb4.polymer.soundpatcher.api.SoundPatcher;
import se.metacraft.moredyes.MoreDyes;
import se.metacraft.moredyes.banner.BannerPatterns;
import se.metacraft.moredyes.color.ModColor;
import se.metacraft.moredyes.color.ModColors;
import se.metacraft.moredyes.content.block.ColoredBlocks;
import se.metacraft.moredyes.content.block.ContainerBlocks;
import se.metacraft.moredyes.content.block.GlassBlocks;
import se.metacraft.moredyes.content.block.ModFlower;
import se.metacraft.moredyes.content.block.ShapedBlocks;
import se.metacraft.moredyes.content.item.ColoredBlockItem;
import se.metacraft.moredyes.content.item.ModBundleItem;
import se.metacraft.moredyes.content.item.ModCushionItem;
import se.metacraft.moredyes.content.item.ModDyeItem;
import se.metacraft.moredyes.content.item.ModHarnessItem;
import se.metacraft.moredyes.sign.SignColors;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import se.metacraft.moredyes.cushion.Cushions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registers every colour × family. Nothing here knows a colour id; it loops over
 * {@link ModColors#all()} and {@link Family#values()}, which is what keeps a new colour a data change.
 */
public final class ModContent {
	private static final Map<ModColor, ModDyeItem> DYES = new LinkedHashMap<>();
	private static final Map<ModColor, ModBundleItem> BUNDLES = new LinkedHashMap<>();
	private static final Map<ModColor, ModHarnessItem> HARNESSES = new LinkedHashMap<>();
	private static final Map<ModColor, Item> FLOWERS = new LinkedHashMap<>();
	private static final Map<ModColor, Item> CUSHIONS = new LinkedHashMap<>();
	private static final Map<ModColor, Map<Family, Block>> BLOCKS = new LinkedHashMap<>();

	private ModContent() {}

	public static ModDyeItem dye(ModColor color) {
		return DYES.get(color);
	}

	public static Block block(ModColor color, Family family) {
		Map<Family, Block> m = BLOCKS.get(color);
		return m == null ? null : m.get(family);
	}

	public static Map<ModColor, ModDyeItem> dyes() {
		return Collections.unmodifiableMap(DYES);
	}

	public static ModBundleItem bundle(ModColor color) {
		return BUNDLES.get(color);
	}

	public static ModHarnessItem harness(ModColor color) {
		return HARNESSES.get(color);
	}

	public static Item cushion(ModColor color) {
		return CUSHIONS.get(color);
	}

	public static Item flower(ModColor color) {
		return FLOWERS.get(color);
	}

	public static Collection<Item> flowers() {
		return Collections.unmodifiableCollection(FLOWERS.values());
	}

	/**
	 * Vanilla's {@code equippable} for the white variant of something, worn as our equipment asset
	 * {@code moredyes:<id>}: same slot, sounds and wearers, our look.
	 */
	static Equippable wornAs(Equippable white, Identifier asset) {
		return new Equippable(white.slot(), white.equipSound(), Optional.of(ResourceKey.create(EquipmentAssets.ROOT_ID, asset)),
				white.cameraOverlay(), white.allowedEntities(), white.dispensable(), white.swappable(), white.damageOnHurt(),
				white.equipOnInteract(), white.canBeSheared(), white.shearingSound());
	}

	/** Every item of a colour, in creative-tab order. */
	public static List<Item> items(ModColor color) {
		List<Item> items = new ArrayList<>();
		items.add(DYES.get(color));
		items.add(BUNDLES.get(color));
		items.add(HARNESSES.get(color));
		items.add(FLOWERS.get(color));
		items.add(CUSHIONS.get(color));
		for (Family family : Family.values()) {
			if (family.hasItem()) items.add(BLOCKS.get(color).get(family).asItem());
		}
		return items;
	}

	public static void register() {
		Looks.plan(ModColors.all().size());
		for (ModColor color : ModColors.all()) {
			registerColor(color);
		}
		registerCreativeTab();
		patchDonorSounds();
		for (BlockModelType type : new BlockModelType[]{BlockModelType.FULL_BLOCK, BlockModelType.TRIPWIRE_FLAT,
				BlockModelType.LEAVES, BlockModelType.BARS_CENTER, BlockModelType.BARS_NORTH_EAST_SOUTH_WEST_WATERLOGGED}) {
			MoreDyes.LOGGER.info("[{}] Polymer pool {}: {} states left after registration",
					MoreDyes.MOD_ID, type, PolymerBlockResourceUtils.getBlocksLeft(type));
		}
	}

	private static void registerColor(ModColor color) {
		// Dye
		Identifier dyeId = id(color.id() + "_dye");
		requireAsset("items/" + dyeId.getPath() + ".json", dyeId);
		ModDyeItem dye = Registry.register(BuiltInRegistries.ITEM, dyeId,
				new ModDyeItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, dyeId)), color, dyeId));
		DYES.put(color, dye);

		// Bundle: vanilla behaviour, our look; contents live in a component so nothing else is needed.
		Identifier bundleId = id(color.id() + "_bundle");
		requireAsset("items/" + bundleId.getPath() + ".json", bundleId);
		BUNDLES.put(color, Registry.register(BuiltInRegistries.ITEM, bundleId, new ModBundleItem(new Item.Properties()
				.stacksTo(1).component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
				.setId(ResourceKey.create(Registries.ITEM, bundleId)), bundleId)));

		// Harness: vanilla's, on our equipment asset (the ghast wears what its equippable names).
		Identifier harnessId = id(color.id() + "_harness");
		requireAsset("items/" + harnessId.getPath() + ".json", harnessId);
		HARNESSES.put(color, Registry.register(BuiltInRegistries.ITEM, harnessId, new ModHarnessItem(new Item.Properties()
				.stacksTo(1).component(DataComponents.EQUIPPABLE, wornAs(Equippable.harness(DyeColor.WHITE), harnessId))
				.setId(ResourceKey.create(Registries.ITEM, harnessId)), harnessId)));

		// Flower: a sniffer's find, crafted into the dye; a torchflower but for its look.
		Identifier flowerId = id(color.id() + "_flower");
		requireAsset("items/" + flowerId.getPath() + ".json", flowerId);
		Block flower = Registry.register(BuiltInRegistries.BLOCK, flowerId, new ModFlower(
				BlockBehaviour.Properties.ofFullCopy(Blocks.TORCHFLOWER).mapColor(color.mapColor())
						.setId(ResourceKey.create(Registries.BLOCK, flowerId)), flowerId));
		collectDonorSounds(flower);
		FLOWERS.put(color, Registry.register(BuiltInRegistries.ITEM, flowerId, new ColoredBlockItem(flower,
				new Item.Properties().useBlockDescriptionPrefix().setId(ResourceKey.create(Registries.ITEM, flowerId)),
				flowerId, Items.TORCHFLOWER)));
		// and potted: an empty flower pot takes it once this is registered
		Identifier pottedId = id("potted_" + flowerId.getPath());
		requireAsset("items/" + pottedId.getPath() + ".json", pottedId);
		collectDonorSounds(Registry.register(BuiltInRegistries.BLOCK, pottedId, new ModFlower.Potted(flower,
				BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_TORCHFLOWER).setId(ResourceKey.create(Registries.BLOCK, pottedId)), pottedId)));

		// Cushion: vanilla's white cushion item and entity, carrying our colour (see cushion.Cushions).
		Identifier cushionId = id(color.id() + "_cushion");
		requireAsset("items/" + cushionId.getPath() + ".json", cushionId);
		CUSHIONS.put(color, Registry.register(BuiltInRegistries.ITEM, cushionId, new ModCushionItem(new Item.Properties()
				.component(DataComponents.CUSHION_COLOR, DyeColor.WHITE)
				.component(Cushions.ITEM_COLOR, color.id())
				.setId(ResourceKey.create(Registries.ITEM, cushionId)), cushionId)));

		// Blocks. Concrete must exist before its powder.
		Map<Family, Block> blocks = new EnumMap<>(Family.class);
		BLOCKS.put(color, blocks);
		DyeColor nearest = SignColors.nearestDye(color);
		for (Family family : Family.values()) {
			Identifier blockId = id(color.id() + "_" + family.id);
			// Stairs and slabs are shown, and held, as vanilla's in the nearest colour (ShapedBlocks).
			boolean shaped = family.kind == Family.Kind.STAIRS || family.kind == Family.Kind.SLAB;
			Block shown = shaped ? ShapedBlocks.vanillaShape(family, nearest) : null;
			BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(family.template)
					.mapColor(color.mapColor())
					.setId(ResourceKey.create(Registries.BLOCK, blockId));
			Block block = switch (family.kind) {
				case SIMPLE -> new ColoredBlocks.Simple(props, blockId);
				case POWDER -> new ColoredBlocks.Powder(blocks.get(Family.CONCRETE), props, blockId);
				case GLAZED -> new ColoredBlocks.Glazed(props, blockId);
				case CARPET -> new ColoredBlocks.Carpet(props, blockId);
				case CANDLE -> new ColoredBlocks.Candle(props, blockId);
				case STAIRS -> new ShapedBlocks.Stairs(blocks.get(family.materialFamily()), shown, props, blockId);
				case SLAB -> new ShapedBlocks.Slab(blocks.get(family.materialFamily()), shown, props, blockId);
				case BED -> new ContainerBlocks.Bed(props, blockId);
				case SHULKER_BOX -> new ContainerBlocks.ShulkerBox(props, blockId);
				case GLASS -> new GlassBlocks.Glass(props, blockId);
				case PANE -> new GlassBlocks.Pane(blocks.get(family.materialFamily()), props, blockId);
				case CANDLE_CAKE -> new ColoredBlocks.CandleCake((ColoredBlocks.Candle) blocks.get(family.materialFamily()),
						props, id(color.id() + "_" + family.materialFamily().id));
			};
			Registry.register(BuiltInRegistries.BLOCK, blockId, block);
			collectDonorSounds(block);
			if (family.hasItem()) {
				requireAsset("items/" + blockId.getPath() + ".json", blockId);
				Item.Properties itemProps = new Item.Properties().useBlockDescriptionPrefix()
						.setId(ResourceKey.create(Registries.ITEM, blockId));
				if (family.kind == Family.Kind.SHULKER_BOX) itemProps.stacksTo(1);
				// a llama wears a carpet as its decor, drawn from the equippable's asset
				if (family.kind == Family.Kind.CARPET) itemProps.component(DataComponents.EQUIPPABLE, wornAs(Equippable.llamaSwag(DyeColor.WHITE), blockId));
				Registry.register(BuiltInRegistries.ITEM, blockId, new ColoredBlockItem(block, itemProps, blockId,
						shown != null ? shown.asItem() : family.clientItem));
			}
			blocks.put(family, block);
		}
	}

	private static void registerCreativeTab() {
		Identifier tabId = id("main");
		CreativeModeTab tab = PolymerCreativeModeTabUtils.builder()
				.title(Component.translatable("itemGroup." + MoreDyes.MOD_ID))
				.icon(() -> new ItemStack(DYES.values().iterator().next()))
				.displayItems((params, output) -> {
					for (ModColor color : ModColors.all()) {
						for (Item item : items(color)) output.accept(item);
						output.accept(BannerPatterns.colouredBanner(color, params.holders()));
					}
				})
				.build();
		PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(tabId, tab);
	}

	private static final Set<SoundType> SERVER_SOUNDS = new LinkedHashSet<>();

	/**
	 * The client plays step, mining, fall, break and place sounds from the vanilla block it is shown
	 * (a note block for wool, a tripwire for carpet, leaves for glass), which no per-packet override
	 * can reach. Wherever that differs from our block's own sounds, both sound types go server-driven:
	 * polymer-sound-patcher silences the client's guess and has the server send ours, on every break
	 * and mining hit (it watches levelEvent), step, fall and placement. Ours goes too because polymer
	 * 0.18.4 sends a break sound only when the client's block state sounds converted, not the break
	 * event's state, and because the placer's client predicts ours from the client item. Blast radius: every vanilla block sharing one of those sound types
	 * gets it from the server as well (the same sound, a tick later).
	 */
	private static void collectDonorSounds(Block block) {
		if (!(block instanceof PolymerBlock polymer)) return;
		for (BlockState state : block.getStateDefinition().getPossibleStates()) {
			SoundType ours = state.getSoundType();
			boolean differs = false;
			for (BlockState shown : List.of(clientState(state), breakEventClientState(polymer, state))) {
				if (!sameSounds(shown.getSoundType(), ours)) {
					if (SERVER_SOUNDS.add(shown.getSoundType())) {
						MoreDyes.LOGGER.info("[{}] {} is shown as {} whose sounds differ", MoreDyes.MOD_ID, state, shown);
					}
					differs = true;
				}
			}
			if (differs) SERVER_SOUNDS.add(ours);
		}
	}

	/** The vanilla state the client sees for {@code state}. */
	public static BlockState clientState(BlockState state) {
		return PolymerBlockUtils.getPolymerBlockState(state, null);
	}

	/** The vanilla state whose break sound and particles the client shows when {@code state} breaks. */
	public static BlockState breakEventClientState(PolymerBlock polymer, BlockState state) {
		BlockState sent = PolymerBlockUtils.getBlockBreakBlockStateSafely(polymer, state, PolymerBlockUtils.NESTED_DEFAULT_DISTANCE, null);
		return PolymerBlockUtils.getPolymerBlockState(sent, null);
	}

	public static boolean sameSounds(SoundType a, SoundType b) {
		return a == b || soundEvents(a).stream().map(SoundEvent::location).toList()
				.equals(soundEvents(b).stream().map(SoundEvent::location).toList());
	}

	public static List<SoundEvent> soundEvents(SoundType type) {
		return List.of(type.getStepSound(), type.getHitSound(), type.getFallSound(), type.getBreakSound(), type.getPlaceSound());
	}

	private static void patchDonorSounds() {
		for (SoundType type : SERVER_SOUNDS) {
			for (SoundEvent event : soundEvents(type)) {
				// the patcher only moves vanilla sounds; a modded one is never predicted by the client anyway
				if (event.location().getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
					SoundPatcher.convertIntoServerSound(event);
				}
			}
			MoreDyes.LOGGER.info("[{}] sound type '{}' made server-driven", MoreDyes.MOD_ID, type.getBreakSound().location());
		}
	}

	/**
	 * The jar must carry the generated asset for everything it registers; a missing one means the
	 * build skipped {@code genAssets} and the pack would show missing models. Fail at startup instead.
	 */
	private static void requireAsset(String path, Identifier what) {
		if (System.getProperty("fabric-api.datagen") != null) return; // the run that creates them
		if (MoreDyes.class.getResource("/assets/" + MoreDyes.MOD_ID + "/" + path) == null) {
			throw new IllegalStateException("[" + MoreDyes.MOD_ID + "] generated asset assets/" + MoreDyes.MOD_ID + "/"
					+ path + " is missing for " + what + " — run ./gradlew runDatagen before building");
		}
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, path);
	}
}
