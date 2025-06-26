package se.datasektionen.mc.metacraft_lib.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_lib.event.RecipeLoad;

import java.util.Map;

@Mixin(JsonDataLoader.class)
public class MixinJsonDataLoader {

	@Unique
	private static final ThreadLocal<RegistryWrapper.WrapperLookup> lookup = ThreadLocal.withInitial(() -> null);
	@Unique
	private static final ThreadLocal<JsonElement> element = ThreadLocal.withInitial(() -> null);

	@Inject(
			method = "load(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/resource/ResourceFinder;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
			at = @At("HEAD")
	)
	private static <T> void load(
			ResourceManager manager, ResourceFinder finder, DynamicOps<JsonElement> ops, Codec<T> codec,
			Map<Identifier, T> results, CallbackInfo ci
	) {
		if (ops instanceof AccessorRegistryOps r) {
			var getter = r.getRegistryInfoGetter();
			if (getter instanceof AccessorRegistryOps.AccessorCachedRegistryInfoGetter g) {
				lookup.set(g.getRegistries());
			}
		}
	}

	@ModifyExpressionValue(
		method = "load(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/resource/ResourceFinder;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"
		)
	)
	private static JsonElement load(JsonElement original) {
		element.set(original);
		return original;
	}

	@WrapOperation(
			method = "method_63568", //in load
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/Map;putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
			)
	)
	private static Object load(
			Map<Identifier, ?> instance, Object key, Object value, Operation<Object> original
	) {
		if (lookup.get() != null && value instanceof Recipe<?> r && element.get().isJsonObject()) {
			var result = RecipeLoad.EVENT.invoker().modify(
					RegistryKey.of(RegistryKeys.RECIPE, (Identifier) key),
					element.get().getAsJsonObject(), r, lookup.get()
			);
			if (result != null) {
				return original.call(instance, key, result);
			} else {
				return null;
			}
		} else {
			return original.call(instance, key, value);
		}
	}

	@Inject(
		method = "load(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/resource/ResourceFinder;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
		at = @At("RETURN")
	)
	private static <T> void cleanup(
			ResourceManager manager, ResourceFinder finder, DynamicOps<JsonElement> ops,
            Codec<T> codec, Map<Identifier, T> results, CallbackInfo ci
	) {
		lookup.remove();
		element.remove();
	}

}
