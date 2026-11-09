package se.metacraft.skins;

import com.mojang.serialization.MapCodec;
import se.metacraft.skins.compat.moredyes.MoreDyesCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dyeing a Dyeable Hat on any helmet but leather, in the crafting grid, as vanilla dyes leather
 * (vanilla's own recipe does leather, and keeps the skin). The grid holds exactly one helmet carrying
 * a live Dyeable Hat and one or more dyes, and nothing else; the result is that helmet with all its
 * components, the skin's marker included, in the mixed colour. A plain helmet, a skin that isn't
 * dyeable and a Dormant hat refuse the dye. With moredyes installed, its colours dye too, combined as
 * moredyes combines them on leather. It's a special recipe: never in the recipe book, always
 * allowed under {@code limitedCrafting}, and no client needs to know of it (the server decides every
 * crafting result). The recipe JSON {@code data/metacraft-skins/recipe/dye_skinned_item.json} names it.
 * Who is crafting is unknown here, so anyone can dye a live hat, an Away one included.
 */
public final class DyeSkinnedItemRecipe extends CustomRecipe {
	/** Do moredyes' colours dye too? Only with moredyes installed; otherwise only vanilla's dyes do. */
	public static final boolean MOREDYES_COLOURS = FabricLoader.getInstance().isModLoaded("moredyes");

	/** The dyes in use: moredyes' with moredyes installed, vanilla's otherwise. */
	// MoreDyesCompat is only loaded (and moredyes' types only linked) when moredyes is installed.
	public static final Dyes DYES = MOREDYES_COLOURS ? MoreDyesCompat.INSTANCE : Dyes.VANILLA;
	private static final DyeSkinnedItemRecipe INSTANCE = new DyeSkinnedItemRecipe(DYES);
	public static final RecipeSerializer<DyeSkinnedItemRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

	/**
	 * Which items in the grid dye the helmet, and the colour they give it. Vanilla's dyes, mixed as on
	 * leather; another dye mod's can stand in, as long as it dyes leather the same way.
	 */
	public interface Dyes {
		Dyes VANILLA = new Dyes() {
			@Override
			public boolean isDye(ItemStack stack) {
				return stack.has(DataComponents.DYE);
			}

			@Override
			public void dye(ItemStack helmet, List<ItemStack> dyes) {
				List<DyeColor> colours = dyes.stream().map(d -> d.getOrDefault(DataComponents.DYE, DyeColor.WHITE)).toList();
				helmet.set(DataComponents.DYED_COLOR, DyedItemColor.applyDyes(helmet.get(DataComponents.DYED_COLOR), colours));
			}
		};

		/** Is this stack a dye? */
		boolean isDye(ItemStack stack);

		/** Do these dyes (one or more) go together in one grid? */
		default boolean combines(List<ItemStack> dyes) {
			return true;
		}

		/** Colours {@code helmet} (the result, a copy, with its old colour if it had one) with these dyes. */
		void dye(ItemStack helmet, List<ItemStack> dyes);

		/** The animated colour a dyed helmet is marked with, if this dye mod has such colours ({@link Tint}). */
		default Optional<String> animated(ItemStack helmet) {
			return Optional.empty();
		}

		/** Marks a dyed helmet with an animated colour, or (empty) takes the mark off. */
		default void animated(ItemStack helmet, Optional<String> colour) {}
	}

	private final Dyes dyes;

	private DyeSkinnedItemRecipe(Dyes dyes) {
		this.dyes = dyes;
	}

	/** Registers the recipe's type, at mod initialisation. */
	static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "dye_skinned_item"), SERIALIZER);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return grid(input) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		Grid grid = grid(input);
		if (grid == null) return ItemStack.EMPTY;
		ItemStack result = grid.helmet().copyWithCount(1);
		dyes.dye(result, grid.dyes());
		return result;
	}

	private record Grid(ItemStack helmet, List<ItemStack> dyes) {}

	/** The helmet and the dyes, if the grid holds exactly one dyeable helmet, one or more dyes that go together, and nothing else. */
	private @Nullable Grid grid(CraftingInput input) {
		if (input.ingredientCount() < 2) return null;
		Skins skins = MetacraftSkins.skins();
		if (skins == null) return null;
		ItemStack helmet = null;
		List<ItemStack> found = new ArrayList<>();
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) continue;
			if (dyes.isDye(stack)) {
				found.add(stack);
			} else if (helmet == null && dyeable(skins, stack)) {
				helmet = stack;
			} else {
				return null;
			}
		}
		return helmet != null && !found.isEmpty() && dyes.combines(found) ? new Grid(helmet, found) : null;
	}

	/** A helmet that isn't leather, carrying a live Dyeable Hat. */
	private static boolean dyeable(Skins skins, ItemStack stack) {
		return !Material.leather(stack) && stack.has(SkinMarker.TYPE) && skins.liveSkin(stack).filter(LiveSkin::dyeable).isPresent();
	}

	@Override
	public RecipeSerializer<DyeSkinnedItemRecipe> getSerializer() {
		return SERIALIZER;
	}
}
