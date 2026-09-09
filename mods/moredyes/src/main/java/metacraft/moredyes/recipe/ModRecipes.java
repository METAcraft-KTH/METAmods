package metacraft.moredyes.recipe;

import metacraft.moredyes.MoreDyes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Recipe types that carry an RGB where vanilla's carry a {@code DyeColor}. The recipe JSONs
 * themselves are emitted by gen_assets.py from vanilla's (same targets, our type).
 */
public final class ModRecipes {
    private ModRecipes() {}

    public static void init() {
        register("crafting_dye", ModDyeRecipe.SERIALIZER);
        register("crafting_special_firework_star", ModFireworkStarRecipe.SERIALIZER);
        register("crafting_special_firework_star_fade", ModFireworkStarFadeRecipe.SERIALIZER);
    }

    private static void register(String path, RecipeSerializer<?> serializer) {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, path), serializer);
    }
}
