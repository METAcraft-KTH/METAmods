package se.datasektionen.mc.simplecustomfeatures;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponents;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;
import se.datasektionen.mc.metacraft_lib.config.container.ConfigContainer;
import se.datasektionen.mc.metacraft_lib.config.container.ReloadCause;
import se.datasektionen.mc.metacraft_lib.config.container.ReloadFunction;
import se.datasektionen.mc.metacraft_lib.config.container.ServerAwareConfigContainer;
import se.datasektionen.mc.metacraft_lib.config.extensions.ReloadAware;
import se.datasektionen.mc.metacraft_lib.config.extensions.ServerLoadAware;
import se.datasektionen.mc.metacraft_lib.config.extensions.ServerUnloadAware;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;
import se.datasektionen.mc.simplecustomfeatures.objects.items.BaseItem;
import se.datasektionen.mc.simplecustomfeatures.objects.items.simple.SimpleItem;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FeaturesConfig implements ReloadAware {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("simple-custom-features.json");

	private static <T> DataResult<Stream<T>> unwrapDataResults(Stream<DataResult<T>> stream) {
		List<Supplier<String>> errors = new ArrayList<>();
		var newStream = stream.flatMap(element -> {
			if (element.error().isPresent()) {
				errors.add(element.error().get().messageSupplier());
			}
			return element.resultOrPartial().stream();
		});
		if (errors.isEmpty()) {
			return DataResult.success(newStream);
		} else {
			return DataResult.error(
					() -> errors.stream().map(Supplier::get).collect(Collectors.joining(DataResult.appendMessages("", ""))),
					newStream
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
						result -> result.collect(
								Multimaps.<
										Pair<? extends ObjectType<?, ?>, ? extends ObjectContainer>,
									RegistryKey<? extends Registry<?>>, ObjectContainer,
									Multimap<RegistryKey<? extends Registry<?>>, ObjectContainer>
								>toMultimap(
										object -> object.getFirst().getRegistry().getKey(),
										Pair::getSecond,
										FeaturesConfig::createMultimap
								)
						)
					),
					objects -> DataResult.success(objects.values().stream().toList())
			).fieldOf("objects").forGetter(FeaturesConfig::getObjects)
	).apply(instance, FeaturesConfig::new));

	//This config should not be reloaded on the /reload command as doing so will modify the registries.
	private static final ServerAwareConfigContainer<FeaturesConfig, WorldSpecificEntries> config = ConfigContainer.Builder.create(
			CODEC, configPath, () -> {
				var config = new FeaturesConfig();
				config.addObjects(new ObjectContainer.Loaded<>(
						Features.getID("test"), new SimpleItem(
						new BaseItem.ItemSettingsWithBaseItem(
								Items.BRICK.getRegistryEntry(),
								ComponentChanges.builder().add(
										DataComponentTypes.MAX_STACK_SIZE, 32
								).add(
										DataComponentTypes.FIRE_RESISTANT, Unit.INSTANCE
								).add(
										DataComponentTypes.RARITY, Rarity.UNCOMMON
								).add(
										DataComponentTypes.FOOD, FoodComponents.ENCHANTED_GOLDEN_APPLE
								).build(),
								Optional.empty()
						),
						Optional.empty(),
						new SimpleItem.ExtendedSettings(
								true,
								ImmutableList.of(new RegistryPair<>(StatusEffects.WITHER)),
								Optional.of(Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_GENERIC_DRINK))
						)
				)));
				return config;
			}
	).buildRegistryAware(
			(config, server) -> new WorldSpecificEntries(config.objects, server.getRegistryManager()),
			(oldConfig, newConfig, cause) -> cause == ReloadCause.AFTER_SERVER_RELOAD ? oldConfig :
				ReloadFunction.<WorldSpecificEntries>getDefault().reload(
						oldConfig, newConfig, cause
				)
	);

	private final Multimap<RegistryKey<? extends Registry<?>>, ObjectContainer> objects;

	public FeaturesConfig(
			Multimap<RegistryKey<? extends Registry<?>>, ObjectContainer> objects
	) {
		this.objects = objects;
		ObjectContainer.register(getLoadedObjects(objects));
	}

	public FeaturesConfig() {
		this(MultimapBuilder.hashKeys().arrayListValues().build());
	}

	public Multimap<RegistryKey<? extends Registry<?>>, ObjectContainer> getObjects() {
		return Multimaps.unmodifiableMultimap(objects);
	}

	private static Stream<ObjectContainer.Loaded<?>> getLoadedObjects(
			Multimap<RegistryKey<? extends Registry<?>>, ObjectContainer> objects
	) {
		return objects.values().stream().filter(
				object -> object instanceof ObjectContainer.Loaded<?>
		).map(object -> (ObjectContainer.Loaded<?>) object);
	}

	public Multimap<RegistryKey<? extends Registry<?>>, ObjectContainer.Loaded<?>> getObjectsInWorld(MinecraftServer server) {
		return Multimaps.unmodifiableMultimap(config.get(server).loadedObjects);
	}

	private void addObjects(ObjectContainer... containers) {
		for (var container : containers) {
			container.getType().resultOrPartial(Features.LOGGER::error).ifPresent(
					type -> objects.put(type.getRegistry().getKey(), container)
			);
		}
		ObjectContainer.register(Arrays.stream(containers).filter(object -> object instanceof ObjectContainer.Loaded<?>).map(
				object -> (ObjectContainer.Loaded<?>) object
		));
	}

	/**
	 * Returns the config.
	 * DO NOT CACHE THIS IN VARIABLES FOR LONGER PERIODS OF TIME!
	 * @return The config.
	 */
	public static FeaturesConfig getConfig() {
		return config.get();
	}

	@Override
	public void beforeReload(ReloadCause cause) {
		ObjectContainer.unregister(getLoadedObjects(objects));
	}

	public static class WorldSpecificEntries implements ServerUnloadAware, ServerLoadAware {

		private final Multimap<RegistryKey<? extends Registry<?>>, ObjectContainer.Loaded<?>> loadedObjects;
		private final List<ObjectContainer.Loaded<?>> objectsRegistered;

		protected WorldSpecificEntries(
				Multimap<RegistryKey<? extends Registry<?>>, ObjectContainer> objectMap,
				RegistryWrapper.WrapperLookup lookup
		) {
			this.objectsRegistered = new ArrayList<>();
			this.loadedObjects = objectMap.entries().stream().flatMap(
					entry -> {
						var res = switch (entry.getValue()) {
							case ObjectContainer.Loaded<?> loaded -> Stream.of(loaded);
							case ObjectContainer.Deferred deferred -> deferred.load(lookup).resultOrPartial(
									Features.LOGGER::error
							).map(e -> {
								objectsRegistered.add(e);
								return e;
							}).stream();
						};
						return res.map(e -> Pair.of(entry.getKey(), e));
					}
			).collect(Multimaps.toMultimap(Pair::getFirst, Pair::getSecond, FeaturesConfig::createMultimap));
			ObjectContainer.register(objectsRegistered.stream());
		}

		@Override
		public void beforeUnload(MinecraftServer server, Optional<ReloadCause> cause) {
			ObjectContainer.unregister(objectsRegistered.stream());
			ObjectCache.getInstance(server).unload();
		}

		@Override
		public void afterLoad(MinecraftServer server, Optional<ReloadCause> cause) {
			ObjectCache.getInstance(server).onLoad();
		}
	}

}
