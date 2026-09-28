package metacraft.moredyes.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's {@code crafting_dye} (leather armour, horse armour, wolf armour) accepting our dyes,
 * mixed freely with vanilla ones. One JSON per target, mirroring vanilla's files:
 * {@code {"type": "moredyes:crafting_dye", "target": "minecraft:leather_boots", "result": {...}}}.
 */
public final class ModDyeRecipe extends CustomRecipe {
	public static final MapCodec<ModDyeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Ingredient.CODEC.fieldOf("target").forGetter(r -> r.target),
			ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result)
	).apply(i, ModDyeRecipe::new));
	public static final RecipeSerializer<ModDyeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC,
			ByteBufCodecs.fromCodecWithRegistries(MAP_CODEC.codec()));

	private final Ingredient target;
	private final ItemStackTemplate result;

	public ModDyeRecipe(Ingredient target, ItemStackTemplate result) {
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
		ItemStack targetStack = ItemStack.EMPTY;
		List<Integer> rgbs = new ArrayList<>();
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) continue;
			if (target.test(stack)) {
				targetStack = stack;
			} else if (DyeStacks.isAnyDye(stack)) {
				rgbs.add(DyeStacks.armorRgb(stack));
			} else {
				return ItemStack.EMPTY;
			}
		}
		if (targetStack.isEmpty() || rgbs.isEmpty()) return ItemStack.EMPTY;
		DyedItemColor mixed = DyeStacks.mix(targetStack.get(DataComponents.DYED_COLOR), rgbs);
		ItemStack out = TransmuteRecipe.createWithOriginalComponents(result, targetStack);
		out.set(DataComponents.DYED_COLOR, mixed);
		return out;
	}

	@Override
	public RecipeSerializer<ModDyeRecipe> getSerializer() {
		return SERIALIZER;
	}
}
