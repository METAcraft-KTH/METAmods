package se.metacraft.portable_jukebox.upgrade;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.metacraft.portable_jukebox.PortableJukebox;
import se.metacraft.portable_jukebox.item.Items;
import se.metacraft.portable_jukebox.item.components.Components;

/**
 * Upgrading a Portable Jukebox: the jukebox and the upgrade of the next tier, anywhere in the grid,
 * nothing else. The result is the same jukebox (disc, stored discs and settings kept) one tier up.
 * Tiers go in order, so a tier 0 jukebox only takes the tier 1 upgrade, and so on. A special recipe:
 * never in the recipe book; the server decides every crafting result. The recipe JSON
 * {@code data/portable_jukebox/recipe/jukebox_upgrade.json} names it.
 */
public final class JukeboxUpgradeRecipe extends CustomRecipe {

	private static final JukeboxUpgradeRecipe INSTANCE = new JukeboxUpgradeRecipe();
	public static final RecipeSerializer<JukeboxUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

	private JukeboxUpgradeRecipe() {}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, PortableJukebox.getID("jukebox_upgrade"), SERIALIZER);
	}

	@Override
	public boolean matches(@NonNull CraftingInput input, @NonNull Level level) {
		return jukebox(input) != null;
	}

	@Override
	public @NonNull ItemStack assemble(@NonNull CraftingInput input) {
		var jukebox = jukebox(input);
		if (jukebox == null) return ItemStack.EMPTY;
		var result = jukebox.copyWithCount(1);
		result.set(Components.PORTABLE_JUKEBOX_TIER, JukeboxUpgrades.getTier(jukebox) + 1);
		// The crafted copy is a new jukebox: it must not claim the old one's sound source.
		result.remove(Components.PORTABLE_JUKEBOX_ENTITY);
		return result;
	}

	/** The jukebox, if the grid holds exactly one jukebox and the upgrade for its next tier, and nothing else. */
	private static @Nullable ItemStack jukebox(CraftingInput input) {
		if (input.ingredientCount() != 2) return null;
		ItemStack jukebox = null;
		JukeboxUpgradeItem upgrade = null;
		for (var stack : input.items()) {
			if (stack.isEmpty()) continue;
			if (stack.is(Items.PORTABLE_JUKEBOX) && jukebox == null) {
				jukebox = stack;
			} else if (stack.getItem() instanceof JukeboxUpgradeItem u && upgrade == null) {
				upgrade = u;
			} else {
				return null;
			}
		}
		if (jukebox == null || upgrade == null) return null;
		return upgrade.getTier() == JukeboxUpgrades.getTier(jukebox) + 1 ? jukebox : null;
	}

	@Override
	public @NonNull RecipeSerializer<JukeboxUpgradeRecipe> getSerializer() {
		return SERIALIZER;
	}
}
