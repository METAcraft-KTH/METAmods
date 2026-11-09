package se.metacraft.skins.compat.moredyes;

import se.metacraft.moredyes.armor.AnimatedArmor;
import se.metacraft.moredyes.color.ModColor;
import se.metacraft.moredyes.recipe.DyeStacks;
import se.metacraft.skins.DyeSkinnedItemRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * moredyes' colours on a Dyeable Hat on any helmet, combined exactly as moredyes' own recipe
 * ({@code ModDyeRecipe}) combines them on leather: its dyes mix freely with vanilla's
 * ({@link DyeStacks#mix}), and an animated colour stands alone in the grid, overwrites the old
 * colour and marks the helmet with {@link AnimatedArmor#ANIMATED_DYE}. The only class that names
 * moredyes' types; reached only when {@link DyeSkinnedItemRecipe#MOREDYES_COLOURS} says it is
 * installed.
 */
public final class MoreDyesCompat implements DyeSkinnedItemRecipe.Dyes {
	public static final DyeSkinnedItemRecipe.Dyes INSTANCE = new MoreDyesCompat();

	private MoreDyesCompat() {}

	@Override
	public boolean isDye(ItemStack stack) {
		return DyeStacks.isAnyDye(stack);
	}

	/** An animated colour only alone. */
	@Override
	public boolean combines(List<ItemStack> dyes) {
		return dyes.size() == 1 || dyes.stream().noneMatch(dye -> DyeStacks.animatedColour(dye).isPresent());
	}

	@Override
	public void dye(ItemStack helmet, List<ItemStack> dyes) {
		Optional<ModColor> animated = DyeStacks.animatedColour(dyes.getFirst());
		if (animated.isPresent()) {
			// overwrites: whatever colour the helmet had does not weigh in
			helmet.set(DataComponents.DYED_COLOR, AnimatedArmor.dyedColor(animated.get()));
			helmet.set(AnimatedArmor.ANIMATED_DYE, animated.get().id());
			return;
		}
		boolean marked = AnimatedArmor.animatedColour(helmet).isPresent();
		if (!marked && dyes.stream().noneMatch(DyeStacks::isOurs)) {
			// vanilla's dyes alone: on leather, vanilla's own recipe dyes it
			DyeSkinnedItemRecipe.Dyes.VANILLA.dye(helmet, dyes);
			return;
		}
		// ordinary dyes on an animated colour start from the dyes alone; the mark goes either way
		helmet.set(DataComponents.DYED_COLOR, DyeStacks.mix(marked ? null : helmet.get(DataComponents.DYED_COLOR),
				dyes.stream().map(DyeStacks::armorRgb).toList()));
		helmet.remove(AnimatedArmor.ANIMATED_DYE);
	}

	@Override
	public Optional<String> animated(ItemStack helmet) {
		return Optional.ofNullable(helmet.get(AnimatedArmor.ANIMATED_DYE));
	}

	@Override
	public void animated(ItemStack helmet, Optional<String> colour) {
		colour.ifPresentOrElse(id -> helmet.set(AnimatedArmor.ANIMATED_DYE, id), () -> helmet.remove(AnimatedArmor.ANIMATED_DYE));
	}
}
