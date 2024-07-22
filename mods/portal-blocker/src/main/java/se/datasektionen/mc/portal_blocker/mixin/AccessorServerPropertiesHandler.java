package se.datasektionen.mc.portal_blocker.mixin;

import net.minecraft.server.dedicated.ServerPropertiesHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPropertiesHandler.class)
public interface AccessorServerPropertiesHandler {

	@Accessor
	@Mutable
	void setAllowNether(boolean allowNether);

}
