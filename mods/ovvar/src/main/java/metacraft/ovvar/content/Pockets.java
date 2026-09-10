package metacraft.ovvar.content;

import metacraft.ovvar.Ovvar;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.math.Fraction;

/**
 * The ovve's pockets are bigger than a bundle, through METAcraft's own bundle mod: its
 * {@code metacraft:bundle_size_factor} component scales a bundle's capacity and shows the real
 * fill level to vanilla clients (a lore line drawn with a custom font), which is what makes a
 * larger bundle usable at all — the vanilla client otherwise predicts "full" at 64.
 *
 * The component is {@link Bundles} — a direct reference to the mod's class, in its own class so
 * that it is only loaded when the mod is present. In METAmods it always is (a hard dependency);
 * the standalone dev repo compiles against its jar in {@code libs/} and runs without it.
 */
final class Pockets {
    private Pockets() {}

    /** In vanilla bundles: 2 = 128 stackable items, or two stacks of 64. */
    static final Fraction SIZE = Fraction.getFraction(2, 1);
    private static final String BUNDLES_MOD = "metacraft-bundles";
    private static boolean warned;

    static Item.Properties apply(Item.Properties properties) {
        if (!FabricLoader.getInstance().isModLoaded(BUNDLES_MOD)) {
            if (!warned) Ovvar.LOGGER.warn("[{}] {} is not present: ovve pockets are vanilla-sized (dev only; METAmods always has it)", Ovvar.MOD_ID, BUNDLES_MOD);
            warned = true;
            return properties;
        }
        return Bundles.apply(properties);
    }

    /**
     * Touching {@code BundleComponents} initialises it, which registers the component: the ovve
     * gets it whichever order Fabric Loader runs the two mods' initialisers in (it is mod-id
     * order, not dependency order, and it differed between the server and data generation).
     */
    private static final class Bundles {
        static Item.Properties apply(Item.Properties properties) {
            return properties.component(nu.metacraft.bundles.BundleComponents.BUNDLE_SIZE_FACTOR, SIZE);
        }
    }
}
