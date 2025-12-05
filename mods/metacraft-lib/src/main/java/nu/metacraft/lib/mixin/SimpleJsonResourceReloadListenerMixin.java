package nu.metacraft.lib.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.event.RecipeLoad;

import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.world.item.crafting.Recipe;

@Mixin(SimpleJsonResourceReloadListener.class)
public class SimpleJsonResourceReloadListenerMixin {

	@Unique
	private static final ThreadLocal<HolderLookup.Provider> lookup = ThreadLocal.withInitial(() -> null);
	@Unique
	private static final ThreadLocal<JsonElement> element = ThreadLocal.withInitial(() -> null);

	@Inject(
			method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
			at = @At("HEAD")
	)
	private static <T> void load(
			ResourceManager manager, FileToIdConverter finder, DynamicOps<JsonElement> ops, Codec<T> codec,
			Map<Identifier, T> results, CallbackInfo ci
	) {
		if (ops instanceof RegistryOpsAccessor r) {
			var getter = r.getLookupProvider();
			if (getter instanceof RegistryOpsAccessor.HolderLookupAdapter g) {
				lookup.set(g.getLookupProvider());
			}
		}
	}

	@ModifyExpressionValue(
		method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
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
					ResourceKey.create(Registries.RECIPE, (Identifier) key),
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
		method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
		at = @At("RETURN")
	)
	private static <T> void cleanup(
			ResourceManager manager, FileToIdConverter finder, DynamicOps<JsonElement> ops,
            Codec<T> codec, Map<Identifier, T> results, CallbackInfo ci
	) {
		lookup.remove();
		element.remove();
	}

}
