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
 * own. Fabric Loader runs mod initialisers in mod-id order, not dependency order, and
 * metacraft-bundles registers the component in a static initialiser of its component class, so
 * that class is initialised here explicitly before the ovve is registered — the order the
 * initialisers happen to run in then makes no difference (it differed between the server and
 * data generation, which is how CI caught it).
 */
final class Pockets {
    private Pockets() {}

    /** In vanilla bundles: 2 = 128 stackable items, or two stacks of 64. */
    static final Fraction SIZE = Fraction.getFraction(2, 1);
    private static final String BUNDLES_MOD = "metacraft-bundles";
    private static final String COMPONENTS_CLASS = "nu.metacraft.bundles.BundleComponents";
    private static final Identifier SIZE_FACTOR = Identifier.fromNamespaceAndPath("metacraft", "bundle_size_factor");

    @SuppressWarnings("unchecked")
    static Item.Properties apply(Item.Properties properties) {
        if (!FabricLoader.getInstance().isModLoaded(BUNDLES_MOD)) {
            Ovvar.LOGGER.warn("[{}] {} is not present: ovve pockets are vanilla-sized (dev only; METAmods always has it)", Ovvar.MOD_ID, BUNDLES_MOD);
            return properties;
        }
        try {
            Class.forName(COMPONENTS_CLASS, true, Pockets.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("[" + Ovvar.MOD_ID + "] " + BUNDLES_MOD + " is loaded but " + COMPONENTS_CLASS
                    + " could not be initialised — has the mod moved its component registration?", e);
        }
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(SIZE_FACTOR);
        if (type == null) {
            throw new IllegalStateException("[" + Ovvar.MOD_ID + "] " + BUNDLES_MOD + " is loaded but " + SIZE_FACTOR
                    + " is not registered by " + COMPONENTS_CLASS + " any more");
        }
        return properties.component((DataComponentType<Fraction>) type, SIZE);
    }
}
