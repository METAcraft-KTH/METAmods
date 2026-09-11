package nu.metacraft.lib.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.event.RecipeDataGen;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Mixin(FabricRecipeProvider.class)
public class FabricRecipeProviderMixin {

	@Unique
	private static final ThreadLocal<HolderLookup.Provider> REGISTRIES = ThreadLocal.withInitial(() -> null);

	@Inject(
			method = "lambda$run$0",
			at = @At("HEAD")
	)
	private void grabRegistries(
			CachedOutput output, HolderLookup.Provider registries,
			CallbackInfoReturnable<CompletionStage<?>> cir
	) {
		REGISTRIES.set(registries);
	}

	@Inject(
			method = "lambda$run$0",
			at = @At("RETURN")
	)
	private void resetRegistries(
			CachedOutput output, HolderLookup.Provider registries,
			CallbackInfoReturnable<CompletionStage<?>> cir
	) {
		REGISTRIES.remove();
	}

	@Inject(
		method = "lambda$run$1",
		at = @At(
				value = "INVOKE",
				ordinal = 0,
				target = "Lnet/minecraft/data/DataProvider;saveStable(Lnet/minecraft/data/CachedOutput;Lcom/google/gson/JsonElement;Ljava/nio/file/Path;)Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private static void accept(
			RegistryOps<?> registryOps, List<?> list,
			CachedOutput output, PackOutput.PathProvider recipesPathResolver,
			ResourceKey<Recipe<?>> recipeKey, Recipe<?> recipe, CallbackInfo ci,
			@Local(name = "recipeJson") LocalRef<JsonObject> recipeJson
	) {
		recipeJson.set(
				RecipeDataGen.EVENT.invoker().modify(
						recipeKey, recipeJson.get(), recipe, REGISTRIES.get()
				)
		);
	}

}
