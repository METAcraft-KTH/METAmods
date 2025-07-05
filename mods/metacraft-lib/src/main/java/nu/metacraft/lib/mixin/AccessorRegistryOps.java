package nu.metacraft.lib.mixin;

import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryOps.class)
public interface AccessorRegistryOps {

	@Accessor
	RegistryOps.RegistryInfoGetter getRegistryInfoGetter();


	@Mixin(targets = "net.minecraft.registry.RegistryOps$CachedRegistryInfoGetter")
	interface AccessorCachedRegistryInfoGetter {
		@Accessor
		RegistryWrapper.WrapperLookup getRegistries();
	}
}
