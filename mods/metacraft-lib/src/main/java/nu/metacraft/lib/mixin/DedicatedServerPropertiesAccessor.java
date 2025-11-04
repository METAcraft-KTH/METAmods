package nu.metacraft.lib.mixin;

import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DedicatedServerProperties.class)
public interface DedicatedServerPropertiesAccessor {

	@Accessor
	@Mutable
	void setHardcore(boolean hardcore);

}
