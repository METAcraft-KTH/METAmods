package nu.metacraft.lib.mixin;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.ServerDynamicRegistryType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerDynamicRegistryType.class)
public interface AccessorServerDynamicRegistryType {

	@Accessor("STATIC_REGISTRY_MANAGER")
	static DynamicRegistryManager.Immutable getStaticRegistryManager() {
		throw new IllegalStateException("Mixin Error");
	}

}
