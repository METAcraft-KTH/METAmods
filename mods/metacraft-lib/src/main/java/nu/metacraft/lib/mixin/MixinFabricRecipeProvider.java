package nu.metacraft.lib.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.event.RecipeDataGen;

@Mixin(targets = "net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider$2")
public class MixinFabricRecipeProvider {

	@Shadow @Final
	RegistryWrapper.WrapperLookup val$wrapperLookup;

	@Inject(
		method = "accept",
		at = @At(
				value = "INVOKE",
				ordinal = 0,
				target = "Lnet/minecraft/data/DataProvider;writeToPath(Lnet/minecraft/data/DataWriter;Lcom/google/gson/JsonElement;Ljava/nio/file/Path;)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	public void accept(
			RegistryKey<Recipe<?>> recipeKey, Recipe<?> recipe,
			@Nullable AdvancementEntry advancement, CallbackInfo ci,
			@Local LocalRef<JsonObject> recipeJson
	) {
		recipeJson.set(
				RecipeDataGen.EVENT.invoker().modify(
						recipeKey, recipeJson.get(), recipe, val$wrapperLookup
				)
		);
	}

}
