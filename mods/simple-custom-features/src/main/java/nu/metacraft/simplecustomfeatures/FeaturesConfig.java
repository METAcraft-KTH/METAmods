package nu.metacraft.simplecustomfeatures;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.DamageResistant;
import nu.metacraft.lib.config.container.MultiFileConfigContainer;
import nu.metacraft.lib.config.container.ReloadCause;
import nu.metacraft.lib.config.container.ServerAware;
import nu.metacraft.lib.config.extensions.ReloadAware;
import nu.metacraft.lib.config.extensions.ServerLoadAware;
import nu.metacraft.lib.config.extensions.ServerUnloadAware;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;
import nu.metacraft.simplecustomfeatures.objects.items.BaseItem;
import nu.metacraft.simplecustomfeatures.objects.items.simple.SimpleItem;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record FeaturesConfig(
		Multimap<ResourceKey<? extends Registry<?>>, ObjectContainer> objects) implements ReloadAware {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("simple-custom-features");

	public static <T> DataResult<List<T>> unwrapDataResults(Stream<DataResult<T>> stream) {
		List<Supplier<String>> errors = new ArrayList<>();
		var list = stream.flatMap(element -> {
			if (element.error().isPresent()) {
				errors.add(element.error().get().messageSupplier());
			}
			return element.resultOrPartial().stream();
		}).toList();
		if (errors.isEmpty()) {
			return DataResult.success(list);
		} else {
			return DataResult.error(
					() -> errors.stream().map(Supplier::get).collect(Collectors.joining(DataResult.appendMessages("", ""))),
					list
			);
		}
	}

	public static <K, V> Multimap<K, V> createMultimap() {
		return MultimapBuilder.hashKeys().arrayListValues().build();
	}

	public static final Codec<FeaturesConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ObjectContainer.CODEC.listOf().flatXmap(
					objects -> unwrapDataResults(objects.stream().map(
							object -> object.getType().map(type -> Pair.of(type, object))
					)).map(
							result -> result.stream().collect(
									Multimaps.<
											Pair<? extends ObjectType<?, ?>, ? extends ObjectContainer>,
											ResourceKey<? extends Registry<?>>, ObjectContainer,
											Multimap<ResourceKey<? extends Registry<?>>, ObjectContainer>
											>toMultimap(
											object -> object.getFirst().getRegistry().key(),
											Pair::getSecond,
											FeaturesConfig::createMultimap
									)
							)
					),
					objects -> DataResult.success(objects.values().stream().toList())
			).fieldOf("objects").forGetter(FeaturesConfig::objects)
	).apply(instance, FeaturesConfig::new));

	//This config should not be reloaded on the /reload command as doing so will modify the registries.
	private static final ServerAware<MultiFileConfigContainer.Mergable<FeaturesConfig, FeaturesConfig>, WorldSpecificEntries> config = ServerAware.wrap(
			MultiFileConfigContainer.Builder.create(CODEC).addDefaultSetting(
					"default", () -> {
						var config = new FeaturesConfig();
						config.addObjects(new ObjectContainer.Loaded<>(
								Features.getID("test"), new SimpleItem(
								new BaseItem.ItemSettingsWithBaseItem(
										Items.BRICK.builtInRegistryHolder(),
										DataComponentPatch.builder().set(
												DataComponents.MAX_STACK_SIZE, 32
										).set(
												DataComponents.DAMAGE_RESISTANT, new DamageResistant(DamageTypeTags.IS_FIRE)
										).set(
												DataComponents.RARITY, Rarity.UNCOMMON
										).set(
												DataComponents.FOOD, Foods.ENCHANTED_GOLDEN_APPLE
										).set(
												DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK
										).build(),
										Optional.empty()
								),
								Optional.empty(), List.of()
						)));
						return config;
					}
			).build(
					configPath,
					results -> results.reduce(
							new FeaturesConfig(),
							(main, toAdd) -> {
								main.objects.putAll(toAdd.objects);
								return main;
							}
					)
			),
			(config, server) -> new WorldSpecificEntries(config.get().objects, server.registryAccess()),
			(oldConfig, newConfig, cause) -> oldConfig
	);

	public FeaturesConfig(
			Multimap<ResourceKey<? extends Registry<?>>, ObjectContainer> objects
	) {
		this.objects = objects;
		ObjectContainer.register(getLoadedObjects(objects), null);
	}

	public FeaturesConfig() {
		this(MultimapBuilder.hashKeys().arrayListValues().build());
	}

	@Override
	public Multimap<ResourceKey<? extends Registry<?>>, ObjectContainer> objects() {
		return Multimaps.unmodifiableMultimap(objects);
	}

	private static Stream<ObjectContainer.Loaded<?>> getLoadedObjects(
			Multimap<ResourceKey<? extends Registry<?>>, ObjectContainer> objects
	) {
		return getLoadedObjects(objects.values().stream());
	}

	private static Stream<ObjectContainer.Loaded<?>> getLoadedObjects(Stream<ObjectContainer> containers) {
		return containers.filter(
				object -> (object instanceof ObjectContainer.Loaded<?>) ||
						(object instanceof ObjectContainer.Deferred d && d.getPartial().isPresent())
		).map(
				object -> {
					if (object instanceof ObjectContainer.Loaded<?> loaded) {
						return loaded;
					} else if (object instanceof ObjectContainer.Deferred deferred) {
						return deferred.getPartial().orElseThrow();
					} else {
						throw new IllegalStateException("Something other than loaded or deferred was in the config!");
					}
				}
		);
	}

	public Multimap<ResourceKey<? extends Registry<?>>, ObjectContainer.Loaded<?>> getObjectsInWorld(MinecraftServer server) {
		return Multimaps.unmodifiableMultimap(config.get(server).loadedObjects);
	}

	private void addObjects(ObjectContainer... containers) {
		for (var container : containers) {
			container.getType().resultOrPartial(Features.LOGGER::error).ifPresent(
					type -> objects.put(type.getRegistry().key(), container)
			);
		}
		ObjectContainer.register(getLoadedObjects(Arrays.stream(containers)), null);
	}

	/**
	 * Returns the config.
	 * DO NOT CACHE THIS IN VARIABLES FOR LONGER PERIODS OF TIME!
	 *
	 * @return The config.
	 */
	public static FeaturesConfig getConfig() {
		return config.getContainer().get();
	}

	@Override
	public void beforeReload(ReloadCause cause) {
		ObjectContainer.unregister(getLoadedObjects(objects));
	}

	public static class WorldSpecificEntries implements ServerUnloadAware, ServerLoadAware {

		private final Multimap<ResourceKey<? extends Registry<?>>, ObjectContainer.Loaded<?>> loadedObjects;
		private final List<ObjectContainer.Loaded<?>> objectsRegistered;

		protected WorldSpecificEntries(
				Multimap<ResourceKey<? extends Registry<?>>, ObjectContainer> objectMap,
				HolderLookup.Provider lookup
		) {
			this.objectsRegistered = new ArrayList<>();
			List<ObjectContainer.Deferred> partialsToRemove = new ArrayList<>();
			this.loadedObjects = objectMap.entries().stream().flatMap(
					entry -> {
						var res = switch (entry.getValue()) {
							case ObjectContainer.Loaded<?> loaded -> Stream.of(loaded);
							case ObjectContainer.Deferred deferred -> deferred.load(lookup).resultOrPartial(
									Features.LOGGER::error
							).map(e -> {
								if (deferred.getPartial().isPresent()) {
									partialsToRemove.add(deferred);
								}
								objectsRegistered.add(e);
								return e;
							}).stream();
						};
						return res.map(e -> Pair.of(entry.getKey(), e));
					}
			).collect(Multimaps.toMultimap(Pair::getFirst, Pair::getSecond, FeaturesConfig::createMultimap));
			if (!partialsToRemove.isEmpty()) {
				ObjectContainer.Deferred.removePartials(partialsToRemove.stream());
			}
			ObjectContainer.register(objectsRegistered.stream(), lookup);
		}

		@Override
		public void beforeUnload(MinecraftServer server, Optional<ReloadCause> cause) {
			if (cause.isEmpty() || cause.get() != ReloadCause.AFTER_SERVER_RELOAD) {
				ObjectContainer.unregister(objectsRegistered.stream());
				ObjectCache.getInstance(server).unload();
			}
		}

		@Override
		public void afterLoad(MinecraftServer server, Optional<ReloadCause> cause) {
			ObjectCache.getInstance(server).onLoad();
		}
	}

}
