package nu.metacraft.lib.event;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public interface RecipeDataGen {

	/**
	 * Allows you to modify the saved data for recipes during datagen.
	 * Meant to serve as a companion to {@link RecipeLoad}.
	 * Make sure to return the json in your listener!
	 */
	Event<RecipeDataGen> EVENT = EventFactory.createArrayBacked(
			RecipeDataGen.class, callbacks -> Arrays.stream(callbacks).reduce(
					(id, serialized, recipe, registryLookup) -> serialized,
					(lhs, rhs) -> (id, json, recipe, registryLookup) -> {
						if (json != null) {
							json = lhs.modify(id, json, recipe, registryLookup);
							if (json != null) {
								json = rhs.modify(id, json, recipe, registryLookup);
							}
						}
						return json;
					}
			)
	);

	@Nullable
	JsonObject modify(RegistryKey<Recipe<?>> key, JsonObject json, Recipe<?> recipe, RegistryWrapper.WrapperLookup registryLookup);

}
