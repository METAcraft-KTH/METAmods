package se.metacraft.moredyes.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.moredyes.armor.AnimatedArmor;
import se.metacraft.moredyes.color.ModColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.level.Level;
import se.metacraft.moredyes.mixin.DyeRecipeMixin;

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

	/**
	 * Armour takes any mix of dyes with at least one of ours, except an animated one: that overwrites
	 * whatever colour the armour had, so it is the only dye in the grid. Armour already dyed with an
	 * animated colour is ours to dye even with vanilla dyes alone (vanilla's recipe steps aside, see
	 * {@link DyeRecipeMixin}).
	 */
	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (input.ingredientCount() < 2) return false;
		boolean hasTarget = false, hasOurs = false, marked = false;
		int dyes = 0, animated = 0;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) continue;
			if (target.test(stack)) {
				if (hasTarget) return false;
				hasTarget = true;
				marked = AnimatedArmor.animatedColour(stack).isPresent();
			} else if (DyeStacks.isOurs(stack)) {
				hasOurs = true;
				dyes++;
				if (DyeStacks.animatedColour(stack).isPresent()) animated++;
			} else if (DyeStacks.isVanilla(stack)) {
				dyes++;
			} else {
				return false;
			}
		}
		if (animated > 0) return hasTarget && animated == 1 && dyes == 1;
		return hasTarget && (hasOurs || marked && dyes > 0);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack targetStack = ItemStack.EMPTY;
		ModColor animated = null;
		List<Integer> rgbs = new ArrayList<>();
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) continue;
			if (target.test(stack)) {
				targetStack = stack;
			} else if (DyeStacks.isAnyDye(stack)) {
				ModColor colour = DyeStacks.animatedColour(stack).orElse(null);
				if (colour != null) animated = colour;
				rgbs.add(DyeStacks.armorRgb(stack));
			} else {
				return ItemStack.EMPTY;
			}
		}
		if (targetStack.isEmpty() || rgbs.isEmpty()) return ItemStack.EMPTY;
		ItemStack out = TransmuteRecipe.createWithOriginalComponents(result, targetStack);
		if (animated != null) {
			// overwrites: whatever colour the armour had does not weigh in
			if (rgbs.size() != 1) return ItemStack.EMPTY;
			out.set(DataComponents.DYED_COLOR, AnimatedArmor.dyedColor(animated));
			out.set(AnimatedArmor.ANIMATED_DYE, animated.id());
			return out;
		}
		// ordinary dyes on animated armour start from the dyes alone; the marker goes either way
		boolean marked = AnimatedArmor.animatedColour(targetStack).isPresent();
		out.set(DataComponents.DYED_COLOR, DyeStacks.mix(marked ? null : targetStack.get(DataComponents.DYED_COLOR), rgbs));
		out.remove(AnimatedArmor.ANIMATED_DYE);
		return out;
	}

	@Override
	public RecipeSerializer<ModDyeRecipe> getSerializer() {
		return SERIALIZER;
	}
}
