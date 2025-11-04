package nu.metacraft.lib.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
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
	HolderLookup.Provider val$wrapperLookup;

	@Inject(
		method = "accept",
		at = @At(
				value = "INVOKE",
				ordinal = 0,
				target = "Lnet/minecraft/data/DataProvider;saveStable(Lnet/minecraft/data/CachedOutput;Lcom/google/gson/JsonElement;Ljava/nio/file/Path;)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	public void accept(
			ResourceKey<Recipe<?>> recipeKey, Recipe<?> recipe,
			@Nullable AdvancementHolder advancement, CallbackInfo ci,
			@Local LocalRef<JsonObject> recipeJson
	) {
		recipeJson.set(
				RecipeDataGen.EVENT.invoker().modify(
						recipeKey, recipeJson.get(), recipe, val$wrapperLookup
				)
		);
	}

}
