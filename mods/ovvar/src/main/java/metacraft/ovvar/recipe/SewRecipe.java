package metacraft.ovvar.recipe;

import com.mojang.serialization.MapCodec;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.PatchItem;
import metacraft.ovvar.content.Patches;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sewing at the smithing table: an ovve in the base slot, a patch in the addition slot, nothing
 * in the template slot → the ovve with that patch on. Refused (no result) when the patch is
 * already on or its spot is taken. One JSON, {@code data/ovvar/recipe/sew.json}, of type
 * {@code ovvar:sew}; the menu is vanilla's, and vanilla clients see the result because the server
 * fills the result slot.
 */
public final class SewRecipe implements SmithingRecipe {
    public static final MapCodec<SewRecipe> MAP_CODEC = MapCodec.unit(SewRecipe::new);
    public static final RecipeSerializer<SewRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, StreamCodec.unit(new SewRecipe()));

    public static void init() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "sew"), SERIALIZER);
    }

    /** Whether {@code patch} can go on {@code ovve} right now: not sewn yet, spot free. */
    public static boolean fits(ItemStack ovve, Patches.Patch patch) {
        if (!(ovve.getItem() instanceof OvveItem)) return false;
        for (String id : Looks.patches(ovve)) {
            Patches.Patch sewn = Patches.get(id);
            if (sewn.id().equals(patch.id()) || sewn.spot() == patch.spot()) return false;
        }
        return true;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return input.template().isEmpty() && input.addition().getItem() instanceof PatchItem patch && fits(input.base(), patch.patch);
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        ItemStack out = input.base().copyWithCount(1);
        List<String> patches = new ArrayList<>(Looks.patches(out));
        patches.add(((PatchItem) input.addition().getItem()).patch.id());
        Looks.setPatches(out, patches);
        return out;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return Optional.empty();
    }

    @Override
    public Ingredient baseIngredient() {
        return Ingredient.of(ModContent.items().stream().filter(i -> i instanceof OvveItem).map(i -> (Item) i));
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return Optional.of(Ingredient.of(ModContent.items().stream().filter(i -> i instanceof PatchItem).map(i -> (Item) i)));
    }

    @Override
    public RecipeSerializer<? extends SmithingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of();
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.SMITHING;
    }
}
