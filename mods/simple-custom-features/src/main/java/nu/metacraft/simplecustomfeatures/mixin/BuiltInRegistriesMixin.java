package nu.metacraft.simplecustomfeatures.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import nu.metacraft.simplecustomfeatures.RegistryExtensions;

@Mixin(BuiltInRegistries.class)
public class BuiltInRegistriesMixin {

	@Inject(method = "acquireBootstrapRegistrationLookup", at = @At("HEAD"), cancellable = true)
	private static <T> void fixUnnecessaryCrash(
			Registry<T> registry, CallbackInfoReturnable<HolderGetter<T>> cir
	) {
		if (((RegistryExtensions<T>) registry).simpleCustomFeatures$isFrozen()) {
			cir.setReturnValue(registry);
		}
	}

}
