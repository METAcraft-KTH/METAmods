package se.datasektionen.mc.simplecustomfeatures;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import se.datasektionen.mc.metacraft_lib.config.JsonHelper;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;

import java.nio.file.Path;
import java.util.*;

public class ObjectCache {

	public static final Codec<ObjectCache> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ObjectContainer.Loaded.LOADED_CODEC.listOf().fieldOf("objects").forGetter(
						cache -> cache.objects.values().stream().toList()
					)
			).apply(instance, ObjectCache::new)
	);

	private static final String stateKey = "simple-custom-objects-cache";

	private static ObjectCache cache = null;

	private static Path getPath(MinecraftServer server) {
		return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(stateKey + ".json");
	}

	public static ObjectCache getInstance(MinecraftServer server) {
		if (cache == null || cache.server != server) {
			cache = JsonHelper.load(
					getPath(server), CODEC, server.getRegistryManager()
			).orElse(new ObjectCache());
			cache.setServer(server);
		}
		return cache;
	}

	private MinecraftServer server;

	private final List<ObjectContainer.Loaded<?>> reRegistered = new ArrayList<>();
	private boolean loaded = false;

	private final Table<RegistryKey<?>, Identifier, ObjectContainer.Loaded<?>> objects = HashBasedTable.create();

	private void setServer(MinecraftServer server) {
		this.server = server;
	}

	protected ObjectCache() {}

	protected ObjectCache(List<ObjectContainer.Loaded<?>> objects) {
		objects.forEach(object -> {
			this.objects.put(object.getObject().getType().getRegistry().getKey(), object.getID(), object);
		});
	}

	public void unload() {
		if (!loaded) return;
		if (!reRegistered.isEmpty()) {
			ObjectContainer.unregister(reRegistered.stream());
			reRegistered.clear();
		}
		loaded = false;
	}

	public void onLoad() {
		if (loaded) {
			return;
		}
		for (var o : FeaturesConfig.getConfig().getObjectsInWorld(server).entries()) {
			objects.put(o.getKey(), o.getValue().getID(), o.getValue());
		}
		for (var object : objects.values()) {
			if (!object.getObject().getType().getRegistry().containsId(object.getID())) {
				reRegistered.add(object);
				Features.LOGGER.warn(
						object.getID() + " of type " +
						Optional.ofNullable(ObjectRegistry.REGISTRY.getId(object.getObject().getType())).map(
								Identifier::toString
						).orElse("error not registered") + " was removed from config. " +
						"To avoid data loss, the latest version of the item will be re-registered."
				);
			}
		}
		if (!reRegistered.isEmpty()) {
			ObjectContainer.register(reRegistered.stream());
		}
		loaded = true;
		save();
	}

	public void save() {
		JsonHelper.save(getPath(server), CODEC, this, server.getRegistryManager());
	}
}
