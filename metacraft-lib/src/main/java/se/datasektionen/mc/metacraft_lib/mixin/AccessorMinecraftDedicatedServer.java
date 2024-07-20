package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.dedicated.ServerPropertiesLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftDedicatedServer.class)
public interface AccessorMinecraftDedicatedServer {

	@Accessor
	ServerPropertiesLoader getPropertiesLoader();

}
