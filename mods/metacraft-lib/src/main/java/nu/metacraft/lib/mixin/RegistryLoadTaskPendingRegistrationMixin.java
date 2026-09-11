package nu.metacraft.lib.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Decoder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import nu.metacraft.lib.event.RecipeLoad;
import nu.metacraft.lib.util.helper.LookupHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
public class RegistryLoadTaskPendingRegistrationMixin {

	@ModifyExpressionValue(
			method = "loadFromResource",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/serialization/DataResult;getOrThrow()Ljava/lang/Object;"
			)
	)
	private static <R, T> R loadFromResource(
			R original, final Decoder<T> elementDecoder, final RegistryOps<JsonElement> ops,
			final ResourceKey<T> elementKey, @Local(name = "json") JsonElement json
	) {
		if (original instanceof Recipe<?> recipe && json.isJsonObject()) {
			var provider = LookupHelper.getProvider(ops);
			if (provider.isPresent()) {
				//noinspection unchecked
				return (R) RecipeLoad.EVENT.invoker().modify(
						(ResourceKey<Recipe<?>>) elementKey, json.getAsJsonObject(), recipe, provider.get()
				);
			}

		}
		return original;
	}

}
