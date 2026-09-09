package metacraft.ovvar.recipe;

import com.mojang.serialization.MapCodec;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Layout;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sewing pinned patches at the smithing table: an ovve in the base slot, a patch that is some
 * field's pinned patch in the addition slot, nothing in the template slot → the ovve with it on.
 * Refused (no result) when it is on already. Menu patches are placed on an armour stand instead
 * ({@link metacraft.ovvar.sewing.StandSewing}). One JSON, {@code data/ovvar/recipe/sew.json}, of
 * type {@code ovvar:sew}; the menu is vanilla's, and vanilla clients see the result because the
 * server fills the result slot.
 */
public final class SewRecipe implements SmithingRecipe {
    public static final MapCodec<SewRecipe> MAP_CODEC = MapCodec.unit(SewRecipe::new);
    public static final RecipeSerializer<SewRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, StreamCodec.unit(new SewRecipe()));

    public static void init() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "sew"), SERIALIZER);
    }

    /** The pinned field {@code patch} belongs to and that is still free on {@code ovve}, or null. */
    public static Layout.Field pinnedFieldFor(ItemStack ovve, Patches.Patch patch) {
        if (!(ovve.getItem() instanceof OvveItem)) return null;
        Map<String, String> sewn = Looks.sewn(ovve);
        for (Layout.Field f : Layout.all()) {
            if (f.kind() == Layout.Kind.PINNED && f.accepts(patch.id()) && !sewn.containsKey(f.id())) return f;
        }
        return null;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return input.template().isEmpty() && input.addition().getItem() instanceof PatchItem patch
                && pinnedFieldFor(input.base(), patch.patch) != null;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        ItemStack out = input.base().copyWithCount(1);
        Patches.Patch patch = ((PatchItem) input.addition().getItem()).patch;
        Layout.Field field = pinnedFieldFor(out, patch);
        Map<String, String> sewn = Looks.sewn(out);
        sewn.put(field.id(), patch.id());
        Looks.setSewn(out, sewn);
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
