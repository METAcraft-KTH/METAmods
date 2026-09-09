package metacraft.moredyes.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Vanilla's {@code crafting_special_firework_star} with our dyes allowed among the colours. Same
 * data shape as vanilla's recipe minus the {@code dye} ingredient (any dye, ours or vanilla, counts;
 * at least one must be ours, otherwise vanilla's recipe already matched).
 */
public final class ModFireworkStarRecipe extends CustomRecipe {
    private static final Codec<Map<FireworkExplosion.Shape, Ingredient>> SHAPES_CODEC = Codec.simpleMap(
            FireworkExplosion.Shape.CODEC, Ingredient.CODEC, StringRepresentable.keys(FireworkExplosion.Shape.values())).codec();
    public static final MapCodec<ModFireworkStarRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SHAPES_CODEC.fieldOf("shapes").forGetter(r -> r.shapes),
            Ingredient.CODEC.fieldOf("trail").forGetter(r -> r.trail),
            Ingredient.CODEC.fieldOf("twinkle").forGetter(r -> r.twinkle),
            Ingredient.CODEC.fieldOf("fuel").forGetter(r -> r.fuel),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result)
    ).apply(i, ModFireworkStarRecipe::new));
    public static final RecipeSerializer<ModFireworkStarRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC,
            ByteBufCodecs.fromCodecWithRegistries(MAP_CODEC.codec()));

    private final Map<FireworkExplosion.Shape, Ingredient> shapes;
    private final Ingredient trail, twinkle, fuel;
    private final ItemStackTemplate result;

    public ModFireworkStarRecipe(Map<FireworkExplosion.Shape, Ingredient> shapes, Ingredient trail, Ingredient twinkle,
                                 Ingredient fuel, ItemStackTemplate result) {
        this.shapes = shapes;
        this.trail = trail;
        this.twinkle = twinkle;
        this.fuel = fuel;
        this.result = result;
    }

    private FireworkExplosion.@Nullable Shape findShape(ItemStack stack) {
        for (Map.Entry<FireworkExplosion.Shape, Ingredient> e : shapes.entrySet()) {
            if (e.getValue().test(stack)) return e.getKey();
        }
        return null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() < 2) return false;
        boolean hasTwinkle = false, hasTrail = false, hasFuel = false, hasShape = false, hasOurs = false, hasDye = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (twinkle.test(stack)) {
                if (hasTwinkle) return false;
                hasTwinkle = true;
            } else if (trail.test(stack)) {
                if (hasTrail) return false;
                hasTrail = true;
            } else if (fuel.test(stack)) {
                if (hasFuel) return false;
                hasFuel = true;
            } else if (DyeStacks.isOurs(stack)) {
                hasOurs = true;
                hasDye = true;
            } else if (DyeStacks.isVanilla(stack)) {
                hasDye = true;
            } else if (findShape(stack) != null) {
                if (hasShape) return false;
                hasShape = true;
            } else {
                return false;
            }
        }
        return hasFuel && hasDye && hasOurs;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        FireworkExplosion.Shape shape = FireworkExplosion.Shape.SMALL_BALL;
        IntList colors = new IntArrayList();
        boolean hasTrail = false, hasTwinkle = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            FireworkExplosion.Shape s = findShape(stack);
            if (s != null) {
                shape = s;
            } else if (DyeStacks.isAnyDye(stack)) {
                colors.add(DyeStacks.fireworkRgb(stack));
            } else if (trail.test(stack)) {
                hasTrail = true;
            } else if (twinkle.test(stack)) {
                hasTwinkle = true;
            }
        }
        ItemStack out = result.create();
        out.set(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(shape, colors, IntList.of(), hasTrail, hasTwinkle));
        return out;
    }

    @Override
    public RecipeSerializer<ModFireworkStarRecipe> getSerializer() {
        return SERIALIZER;
    }
}
