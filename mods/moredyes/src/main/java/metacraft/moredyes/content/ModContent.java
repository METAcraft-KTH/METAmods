package metacraft.moredyes.content;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import eu.pb4.polymer.soundpatcher.api.SoundPatcher;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.color.ModColors;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers every colour × family. Nothing here knows a colour id; it loops over
 * {@link ModColors#all()} and {@link Family#values()}, which is what keeps a new colour a data change.
 */
public final class ModContent {
    private static final Map<ModColor, ModDyeItem> DYES = new LinkedHashMap<>();
    private static final Map<ModColor, ModBundleItem> BUNDLES = new LinkedHashMap<>();
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

    /** Every item of a colour, in creative-tab order. */
    public static List<Item> items(ModColor color) {
        List<Item> items = new ArrayList<>();
        items.add(DYES.get(color));
        items.add(BUNDLES.get(color));
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

        // Blocks. Concrete must exist before its powder.
        Map<Family, Block> blocks = new EnumMap<>(Family.class);
        BLOCKS.put(color, blocks);
        for (Family family : Family.values()) {
            Identifier blockId = id(color.id() + "_" + family.id);
            BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(family.template)
                    .mapColor(color.mapColor())
                    .setId(ResourceKey.create(Registries.BLOCK, blockId));
            Block block = switch (family.kind) {
                case SIMPLE -> new ColoredBlocks.Simple(props, blockId);
                case POWDER -> new ColoredBlocks.Powder(blocks.get(Family.CONCRETE), props, blockId);
                case GLAZED -> new ColoredBlocks.Glazed(props, blockId);
                case CARPET -> new ColoredBlocks.Carpet(props, blockId);
                case CANDLE -> new ColoredBlocks.Candle(props, blockId);
                case STAIRS -> new ShapedBlocks.Stairs(blocks.get(family.materialFamily()), props, blockId);
                case SLAB -> new ShapedBlocks.Slab(blocks.get(family.materialFamily()), props, blockId);
                case BED -> new ContainerBlocks.Bed(props, blockId);
                case SHULKER_BOX -> new ContainerBlocks.ShulkerBox(props, blockId);
                case GLASS -> new GlassBlocks.Glass(props, blockId);
                case PANE -> new GlassBlocks.Pane(blocks.get(family.materialFamily()), props, blockId);
                case CANDLE_CAKE -> new ColoredBlocks.CandleCake((ColoredBlocks.Candle) blocks.get(family.materialFamily()),
                        props, id(color.id() + "_" + family.materialFamily().id));
            };
            collectDonorSounds(block);
            Registry.register(BuiltInRegistries.BLOCK, blockId, block);
            if (family.hasItem()) {
                requireAsset("items/" + blockId.getPath() + ".json", blockId);
                Item.Properties itemProps = new Item.Properties().useBlockDescriptionPrefix()
                        .setId(ResourceKey.create(Registries.ITEM, blockId));
                if (family.kind == Family.Kind.SHULKER_BOX) itemProps.stacksTo(1);
                Registry.register(BuiltInRegistries.ITEM, blockId, new ColoredBlockItem(block, itemProps, blockId, family.clientItem));
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
                    }
                })
                .build();
        PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(tabId, tab);
    }

    private static final Set<SoundType> DONOR_SOUNDS = new HashSet<>();

    /**
     * Step, mining-hit and fall sounds are predicted client-side from the donor block (tripwire,
     * sculk sensor, an invisible copper stair), which no per-packet override can reach. Collect the
     * donors whose sounds differ from ours so polymer-sound-patcher can silence the client's guess
     * and have the server send the real sound. Blast radius: every vanilla block sharing that donor
     * sound type becomes server-driven for those three sounds (same sound, just from the server).
     */
    private static void collectDonorSounds(Block block) {
        if (!(block instanceof PolymerTexturedBlock textured)) return;
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            BlockState donor = textured.getPolymerBlockState(state, null);
            if (donor != null && donor.getSoundType() != state.getSoundType()) {
                if (DONOR_SOUNDS.add(donor.getSoundType())) {
                    MoreDyes.LOGGER.info("[{}] {} uses donor {} whose sounds differ", MoreDyes.MOD_ID, block, donor);
                }
            }
        }
    }

    private static void patchDonorSounds() {
        for (SoundType donor : DONOR_SOUNDS) {
            SoundPatcher.convertIntoServerSound(donor.getStepSound());
            SoundPatcher.convertIntoServerSound(donor.getHitSound());
            SoundPatcher.convertIntoServerSound(donor.getFallSound());
            MoreDyes.LOGGER.info("[{}] donor sound '{}' made server-authoritative for step/hit/fall",
                    MoreDyes.MOD_ID, donor.getStepSound().location());
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
