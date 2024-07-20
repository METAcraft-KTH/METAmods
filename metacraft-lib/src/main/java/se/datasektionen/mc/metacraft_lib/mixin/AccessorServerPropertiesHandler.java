package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.server.dedicated.ServerPropertiesHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPropertiesHandler.class)
public interface AccessorServerPropertiesHandler {

	@Accessor
	@Mutable
	void setHardcore(boolean hardcore);

}
