package se.datasektionen.mc.metacraft_lib.config.container;

import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.function.BiFunction;

/**
 * In modern versions, more and more codecs are dependent on {@link net.minecraft.registry.RegistryOps}
 * which requires a valid {@link RegistryWrapper.WrapperLookup}.
 * This poses a problem since our config is loaded before the server starts.
 *
 * To fix this, we have a separate sub-config which is loaded later with the server as a context.
 * This is created from our config by using {@link ConfigContainer.Builder#buildRegistryAware(Path, BiFunction)} where the
 * provided function takes the config file and the Minecraft Server to produce an object of type S.
 * You get to decide for yourself how this object should look like, but it would typically be a record
 * containing any properties that cannot exist without a valid {@link RegistryWrapper.WrapperLookup}.
 *
 * This container will take care of caching these properties, so they are not loaded again every time it's accessed,
 * but will also make sure the settings are refreshed when the server is restarted or reloaded.
 *
 * @param <T> The config instance type.
 * @param <S> The sub-config instance containing loaded registry-dependent properties.
 */
public interface ServerAwareConfigContainer<T, S> extends ConfigContainer<T> {

	/**
	 * Get the server-specific properties.
	 *
	 * @param server The server we are currently on.
	 * @return An object containing various properties.
	 */
	S get(MinecraftServer server);
}
