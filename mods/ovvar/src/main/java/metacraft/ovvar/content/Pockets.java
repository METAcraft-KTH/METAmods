package metacraft.ovvar.content;

import metacraft.ovvar.Ovvar;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.math.Fraction;

/**
 * The ovve's pockets are bigger than a bundle, through METAcraft's own bundle mod: its
 * {@code metacraft:bundle_size_factor} component scales a bundle's capacity and shows the real
 * fill level to vanilla clients (a lore line drawn with a custom font), which is what makes a
 * larger bundle usable at all — the vanilla client otherwise predicts "full" at 64.
 *
 * The component type is looked up by id rather than imported so this module compiles on its
 * own; in METAmods the mod is a hard dependency and load order is guaranteed by fabric.mod.json.
 */
final class Pockets {
    private Pockets() {}

    /** In vanilla bundles: 2 = 128 stackable items, or two stacks of 64. */
    static final Fraction SIZE = Fraction.getFraction(2, 1);
    private static final Identifier SIZE_FACTOR = Identifier.fromNamespaceAndPath("metacraft", "bundle_size_factor");

    @SuppressWarnings("unchecked")
    static Item.Properties apply(Item.Properties properties) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(SIZE_FACTOR);
        if (type == null) {
            if (FabricLoader.getInstance().isModLoaded("metacraft-bundles")) {
                throw new IllegalStateException("[" + Ovvar.MOD_ID + "] metacraft-bundles is loaded but " + SIZE_FACTOR
                        + " is not registered yet — ovvar must initialise after it (check fabric.mod.json depends)");
            }
            Ovvar.LOGGER.warn("[{}] metacraft-bundles is not present: ovve pockets are vanilla-sized (dev only; METAmods always has it)", Ovvar.MOD_ID);
            return properties;
        }
        return properties.component((DataComponentType<Fraction>) type, SIZE);
    }
}
