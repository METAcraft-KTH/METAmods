package nu.metacraft.lib.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryOps.class)
public interface RegistryOpsAccessor {

	@Accessor
	RegistryOps.RegistryInfoLookup getLookupProvider();


	@Mixin(targets = "net.minecraft.resources.RegistryOps$HolderLookupAdapter")
	interface HolderLookupAdapter {
		@Accessor
		HolderLookup.Provider getLookupProvider();
	}
}
