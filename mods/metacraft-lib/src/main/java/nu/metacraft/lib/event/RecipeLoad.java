package nu.metacraft.lib.event;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.lib.METAcraftLib;

import java.util.Arrays;

public interface RecipeLoad {

	/**
	 * Runs before everything else. Useful for "rescuing" removed recipes.
	 * Please don't remove recipes in this phase unless you have a very good reason.
	 */
	Identifier PRE = METAcraftLib.getID("pre");

	/**
	 * Runs after everything else, in case you want to take the modifications of other mods into account.
	 */
	Identifier POST = METAcraftLib.getID("post");

	/**
	 * Allows you to modify or downright replace recipes as they are loaded.
	 * Make sure to return the recipe in your listener!
	 * You can even remove recipes by returning null (removed recipes will be ignored by following listeners).
	 * Feel free to use the phases {@link RecipeLoad#PRE} and {@link RecipeLoad#POST} as needed,
	 * but unless you know they will be strictly necessary I recommend just registering the event normally
	 * (using {@link Event#DEFAULT_PHASE}).
	 * <br/>
	 * If you want to fire this event yourself, keep in mind that your might get a null result which you need to handle accordingly.
	 */
	Event<RecipeLoad> EVENT = EventFactory.createWithPhases(
			RecipeLoad.class, callbacks -> Arrays.stream(callbacks).reduce(
					(id, serialized, recipe, registryLookup) -> recipe,
					(lhs, rhs) -> (id, json, original, registryLookup) -> {
						var recipe = original;
						if (recipe != null) {
							recipe = lhs.modify(id, json, recipe, registryLookup);
							if (recipe != null) {
								recipe = rhs.modify(id, json, recipe, registryLookup);
							}
						}
						return recipe;
					}
			), PRE, Event.DEFAULT_PHASE, POST
	);

	@Nullable
	Recipe<?> modify(RegistryKey<Recipe<?>> key, JsonObject json, Recipe<?> recipe, RegistryWrapper.WrapperLookup registryLookup);

}
