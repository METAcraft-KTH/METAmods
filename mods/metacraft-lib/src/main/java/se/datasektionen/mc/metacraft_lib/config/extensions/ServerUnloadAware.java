package se.datasektionen.mc.metacraft_lib.config.extensions;

import net.minecraft.server.MinecraftServer;
import se.datasektionen.mc.metacraft_lib.config.container.ReloadCause;
import se.datasektionen.mc.metacraft_lib.config.container.ServerAware;

import java.util.Optional;

/**
 * Unlike the other interfaces, this is meant for the sub-instances obtained via
 * {@link ServerAware#get(MinecraftServer)},
 * not the configs themselves!
 */
public interface ServerUnloadAware {

	void beforeUnload(MinecraftServer server, Optional<ReloadCause> cause);

}
