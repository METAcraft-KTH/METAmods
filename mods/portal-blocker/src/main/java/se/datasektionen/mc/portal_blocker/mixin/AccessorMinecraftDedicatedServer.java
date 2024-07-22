package se.datasektionen.mc.portal_blocker.mixin;

import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.dedicated.ServerPropertiesLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftDedicatedServer.class)
public interface AccessorMinecraftDedicatedServer {

	@Accessor
	ServerPropertiesLoader getPropertiesLoader();

}
