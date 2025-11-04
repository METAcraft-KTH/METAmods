package nu.metacraft.lib.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.RegistryLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryLayer.class)
public interface AccessorServerDynamicRegistryType {

	@Accessor("STATIC_ACCESS")
	static RegistryAccess.Frozen getStaticRegistryManager() {
		throw new IllegalStateException("Mixin Error");
	}

}
