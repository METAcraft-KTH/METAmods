package se.metacraft.resource_packs;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.Optional;

/**
 * Lets a mod serve a pack that is not in the config from the resource pack server, e.g. one it
 * builds at runtime. Runs on the server's HTTP threads, only for requests that passed the same
 * checks as configured packs. {@code path} is the request path without the leading '/'; the
 * first listener to return bytes answers, otherwise the path is looked up as a configured pack.
 * Build the push packet with {@link ResourcePackConfig#getBaseUrl()}.
 */
public interface ServePackCallback {

	Event<ServePackCallback> EVENT = EventFactory.createArrayBacked(
			ServePackCallback.class,
			events -> (path, verified) -> {
				for (var event : events) {
					var data = event.serve(path, verified);
					if (data.isPresent()) return data;
				}
				return Optional.empty();
			}
	);

	Optional<byte[]> serve(String path, boolean verified);

}
