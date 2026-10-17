package metacraft.kultur.compat.brewery;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every namespace's {@code brewery_effects.json}, the way Brewery means to list them (see {@code BreweryEffectsMixin}). */
public final class BreweryEffectsFiles {
	private BreweryEffectsFiles() {}

	public static Map<Identifier, Resource> find(ResourceManager manager) {
		Map<Identifier, Resource> out = new LinkedHashMap<>();
		for (String namespace : manager.getNamespaces()) {
			Identifier id = Identifier.fromNamespaceAndPath(namespace, "brewery_effects.json");
			manager.getResource(id).ifPresent(resource -> out.put(id, resource));
		}
		return out;
	}
}
