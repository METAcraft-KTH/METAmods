package metacraft.ovvar.content;

import metacraft.ovvar.pack.EquipmentJson;
import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import metacraft.ovvar.Ovvar;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of everything the mod adds: an ovve per chapter (plus its hidden companion top), a
 * patch item per catalogue entry, and a creative tab.
 */
public final class ModContent {
    private ModContent() {}

    private static final Map<Chapter, OvveItem> OVVAR = new EnumMap<>(Chapter.class);
    private static final Map<Chapter, OvveTopItem> TOPS = new EnumMap<>(Chapter.class);
    private static final Map<Chapter, OvveFeetItem> FEET = new EnumMap<>(Chapter.class);
    private static final Map<String, PatchItem> PATCH_ITEMS = new LinkedHashMap<>();
    /** What the creative tab and give commands hand out: ovvar and patches, never tops. */
    private static final List<Item> ALL = new ArrayList<>();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, path);
    }

    public static Identifier ovveId(Chapter chapter) {
        return id(chapter.itemName());
    }

    public static Identifier topId(Chapter chapter) {
        return id(chapter.itemName() + "_top");
    }

    public static Identifier patchId(Patches.Patch patch) {
        return id("patch_" + patch.id());
    }

    public static OvveItem ovve(Chapter chapter) {
        return OVVAR.get(chapter);
    }

    public static OvveTopItem top(Chapter chapter) {
        return TOPS.get(chapter);
    }

    public static Identifier feetId(Chapter chapter) {
        return id(chapter.itemName() + "_feet");
    }

    public static OvveFeetItem feet(Chapter chapter) {
        return FEET.get(chapter);
    }

    public static PatchItem patchItem(Patches.Patch patch) {
        return PATCH_ITEMS.get(patch.id());
    }

    public static List<Item> items() {
        return Collections.unmodifiableList(ALL);
    }

    public static void register() {
        ModComponents.init();
        for (Chapter chapter : Chapter.values()) {
            Identifier ovveId = ovveId(chapter);
            requireAsset("items/" + ovveId.getPath() + ".json", ovveId);
            requireAsset("equipment/" + Looks.assetPath(chapter, Piece.BOTTOM, false, "") + ".json", ovveId);
            OvveItem ovve = Registry.register(BuiltInRegistries.ITEM, ovveId, new OvveItem(Pockets.apply(clothing(chapter, Piece.BOTTOM, ArmorType.LEGGINGS)
                    .component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY))
                    .setId(ResourceKey.create(Registries.ITEM, ovveId)), chapter, ovveId));
            OVVAR.put(chapter, ovve);
            ALL.add(ovve);

            Identifier topId = topId(chapter);
            requireAsset("items/" + topId.getPath() + ".json", topId);
            requireAsset("equipment/" + Looks.assetPath(chapter, Piece.TOP, false, "") + ".json", topId);
            TOPS.put(chapter, Registry.register(BuiltInRegistries.ITEM, topId, new OvveTopItem(clothing(chapter, Piece.TOP, ArmorType.CHESTPLATE)
                    .setId(ResourceKey.create(Registries.ITEM, topId)), chapter, topId)));

            Identifier feetId = feetId(chapter);
            requireAsset("items/" + feetId.getPath() + ".json", feetId);
            requireAsset("equipment/feet/" + OvveFeet.NONE + ".json", feetId);
            FEET.put(chapter, Registry.register(BuiltInRegistries.ITEM, feetId, new OvveFeetItem(cuffs(chapter)
                    .setId(ResourceKey.create(Registries.ITEM, feetId)), chapter, feetId)));
        }
        for (Patches.Patch patch : Patches.all()) {
            Identifier itemId = patchId(patch);
            requireAsset("items/" + itemId.getPath() + ".json", itemId);
            PatchItem item = Registry.register(BuiltInRegistries.ITEM, itemId,
                    new PatchItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, itemId)), patch, itemId));
            PATCH_ITEMS.put(patch.id(), item);
            ALL.add(item);
        }
        registerCreativeTab();
    }

    /**
     * Leather's defence and equip sound, no durability: clothing, not gear that wears out (and an
     * ovve breaking would spill its pockets). The equippable carries the plain look; the stack's real
     * look is chosen per stack when it is sent to a client.
     */
    private static Item.Properties clothing(Chapter chapter, Piece piece, ArmorType type) {
        ArmorMaterial leather = ArmorMaterials.LEATHER;
        ArmorMaterial material = new ArmorMaterial(leather.durability(), leather.defense(), leather.enchantmentValue(),
                leather.equipSound(), leather.toughness(), leather.knockbackResistance(), leather.repairIngredient(),
                Looks.asset(chapter, piece, false, ""));
        return new Item.Properties()
                .stacksTo(1)
                .attributes(material.createAttributes(type))
                .component(DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot())
                        .setEquipSound(material.equipSound())
                        .setAsset(material.assetId())
                        .build());
    }

    /** The companion cuffs: no defence, no durability, just something equippable in the feet slot for the boots pass. */
    private static Item.Properties cuffs(Chapter chapter) {
        return new Item.Properties()
                .stacksTo(1)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET)
                        .setEquipSound(ArmorMaterials.LEATHER.equipSound())
                        .setAsset(EquipmentJson.feetAsset(OvveFeet.NONE))
                        .setSwappable(false)
                        .setDispensable(false)
                        .build());
    }

    private static void registerCreativeTab() {
        CreativeModeTab tab = PolymerCreativeModeTabUtils.builder()
                .title(Component.translatable("itemGroup." + Ovvar.MOD_ID))
                .icon(() -> new ItemStack(ovve(Chapter.DATA)))
                .displayItems((params, output) -> ALL.forEach(output::accept))
                .build();
        PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(id("main"), tab);
    }

    /**
     * The jar must carry the generated asset for everything it registers; a missing one means the
     * build skipped {@code runDatagen} and the pack would show missing models. Fail at startup instead.
     */
    private static void requireAsset(String path, Identifier what) {
        if (System.getProperty("fabric-api.datagen") != null) return; // the run that creates them
        if (Ovvar.class.getResource("/assets/" + Ovvar.MOD_ID + "/" + path) == null) {
            throw new IllegalStateException("[" + Ovvar.MOD_ID + "] generated asset assets/" + Ovvar.MOD_ID + "/"
                    + path + " is missing for " + what + " — run ./gradlew runDatagen before building");
        }
    }
}
