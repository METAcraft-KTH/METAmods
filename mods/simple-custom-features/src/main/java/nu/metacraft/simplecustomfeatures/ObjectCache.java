package nu.metacraft.simplecustomfeatures;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import nu.metacraft.lib.config.JsonHelper;
import nu.metacraft.lib.util.TaskScheduler;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;

import java.nio.file.Path;
import java.util.*;

public class ObjectCache {

	private boolean hasSecondaryReloaded = false;

	public static final Codec<ObjectCache> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ObjectContainer.CODEC.listOf().fieldOf("objects").forGetter(
						cache -> cache.objects.values().stream().toList()
					)
			).apply(instance, ObjectCache::new)
	);

	private static final String stateKey = "simple-custom-objects-cache";

	private static ObjectCache cache = null;

	private static Path getPath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(stateKey + ".json");
	}

	public static ObjectCache getInstance(MinecraftServer server) {
		if (cache == null || cache.server != server) {
			cache = JsonHelper.load(
					getPath(server), CODEC, server.registryAccess()
			).orElse(new ObjectCache());
			cache.setServer(server);
		}
		return cache;
	}

	private MinecraftServer server;

	private final List<ObjectContainer.Loaded<?>> reRegistered = new ArrayList<>();
	private boolean loaded = false;

	//HashBasedTable uses linked hashmap in backend, which should preserve insertion order.
	private final Table<ResourceKey<?>, ResourceLocation, ObjectContainer> objects = HashBasedTable.create();

	private void setServer(MinecraftServer server) {
		this.server = server;
	}

	protected ObjectCache() {}

	protected ObjectCache(List<ObjectContainer> objects) {
		objects.forEach(object -> {
			object.getType().resultOrPartial(
					Features.LOGGER::error
			).ifPresent(type -> {
				this.objects.put(type.getRegistry().key(), object.getID(), object);
			});
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
		loaded = true;
		for (var o : FeaturesConfig.getConfig().getObjectsInWorld(server).entries()) {
			objects.put(o.getKey(), o.getValue().getID(), o.getValue());
		}
		for (var object : objects.values()) {
			var validObject = switch (object) {
				case ObjectContainer.Deferred deferred -> deferred.load(server.registryAccess()).resultOrPartial(
						Features.LOGGER::error
				);
				case ObjectContainer.Loaded<?> l -> Optional.of(l);
			};
			if (validObject.isPresent() && !validObject.get().getObject().getType().getRegistry().containsKey(object.getID())) {
				reRegistered.add(validObject.get());
				Features.LOGGER.warn(
						object.getID() + " of type " +
						Optional.ofNullable(ObjectRegistry.REGISTRY.getKey(validObject.get().getObject().getType())).map(
								ResourceLocation::toString
						).orElse("error not registered") + " was removed from config. " +
						"To avoid data loss, the latest version of the item will be re-registered."
				);
			}
		}
		if (!reRegistered.isEmpty()) {
			ObjectContainer.register(reRegistered.stream(), server.registryAccess());
		}
		if (!hasSecondaryReloaded) {
			hasSecondaryReloaded = true;
			TaskScheduler.scheduleImmediately(server, () -> {
				server.reloadResources(server.getPackRepository().getSelectedIds());
			});
		}
		save();
	}

	public void save() {
		JsonHelper.save(getPath(server), CODEC, this, server.registryAccess());
	}
}
