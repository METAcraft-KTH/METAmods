package metacraft.moredyes.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.level.Level;

/** Vanilla's {@code crafting_special_firework_star_fade} (star + dyes = fade colours) with our dyes. */
public final class ModFireworkStarFadeRecipe extends CustomRecipe {
    public static final MapCodec<ModFireworkStarFadeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("target").forGetter(r -> r.target),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result)
    ).apply(i, ModFireworkStarFadeRecipe::new));
    public static final RecipeSerializer<ModFireworkStarFadeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC,
            ByteBufCodecs.fromCodecWithRegistries(MAP_CODEC.codec()));

    private final Ingredient target;
    private final ItemStackTemplate result;

    public ModFireworkStarFadeRecipe(Ingredient target, ItemStackTemplate result) {
        this.target = target;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() < 2) return false;
        boolean hasTarget = false, hasOurs = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (target.test(stack)) {
                if (hasTarget) return false;
                hasTarget = true;
            } else if (DyeStacks.isOurs(stack)) {
                hasOurs = true;
            } else if (!DyeStacks.isVanilla(stack)) {
                return false;
            }
        }
        return hasTarget && hasOurs;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack star = ItemStack.EMPTY;
        IntList fade = new IntArrayList();
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (target.test(stack)) {
                star = stack;
            } else if (DyeStacks.isAnyDye(stack)) {
                fade.add(DyeStacks.fireworkRgb(stack));
            }
        }
        FireworkExplosion explosion = star.get(DataComponents.FIREWORK_EXPLOSION);
        if (star.isEmpty() || explosion == null) return ItemStack.EMPTY;
        ItemStack out = TransmuteRecipe.createWithOriginalComponents(result, star);
        out.set(DataComponents.FIREWORK_EXPLOSION, explosion.withFadeColors(fade));
        return out;
    }

    @Override
    public RecipeSerializer<ModFireworkStarFadeRecipe> getSerializer() {
        return SERIALIZER;
    }
}
