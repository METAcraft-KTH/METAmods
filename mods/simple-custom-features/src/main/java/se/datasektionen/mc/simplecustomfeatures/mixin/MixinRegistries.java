package se.datasektionen.mc.simplecustomfeatures.mixin;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryEntryLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.simplecustomfeatures.RegistryExtensions;

@Mixin(Registries.class)
public class MixinRegistries {

	@Inject(method = "createEntryLookup", at = @At("HEAD"), cancellable = true)
	private static <T> void fixUnnecessaryCrash(
			Registry<T> registry, CallbackInfoReturnable<RegistryEntryLookup<T>> cir
	) {
		if (((RegistryExtensions<T>) registry).simpleCustomFeatures$isFrozen()) {
			cir.setReturnValue(registry);
		}
	}

}
