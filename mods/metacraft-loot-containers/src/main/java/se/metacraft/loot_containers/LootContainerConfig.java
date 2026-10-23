package se.metacraft.loot_containers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.lib.util.METACodecs;
import se.metacraft.loot_containers.containers.events.LootContainerEvent;
import se.metacraft.loot_containers.containers.events.LootContainerEventRegistry;
import se.metacraft.loot_containers.containers.events.LootContainerEventType;
import se.metacraft.loot_containers.containers.LootContainer;
import se.metacraft.loot_containers.containers.LootContainerRegistry;
import se.metacraft.loot_containers.containers.LootContainerType;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import se.metacraft.config.container.ConfigContainer;

public class LootContainerConfig {

	//Lazy-initialized because otherwise LootContainerRegistry attempts to access config too early, resulting in null pointer exception (because the initConfig event applies while code is in static block).
	public static final MapCodec<LootContainerConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.lazyInitialized(
					() -> METACodecs.wrapPMapCodec(
						Codec.<LootContainerType<?>, LootContainer>dispatchedMap(
							LootContainerRegistry.REGISTRY.byNameCodec(), key -> key.codec().codec()
						),
						HashTreePMap.empty()
					)
			).fieldOf("defaultContainerData").forGetter(config -> config.defaultContainerData),
			Codec.lazyInitialized(
					() -> METACodecs.wrapPMapCodec(
						Codec.<LootContainerEventType<?>, LootContainerEvent>dispatchedMap(
							LootContainerEventRegistry.REGISTRY.byNameCodec(), key -> key.codec().codec()
						),
						HashTreePMap.empty()
					)
			).fieldOf("defaultEventData").forGetter(config -> config.defaultEventData)
	).apply(instance, LootContainerConfig::new));

	private static final ConfigContainer<LootContainerConfig> config = ConfigContainer.Builder.create(
			CODEC, LootContainerConfig::new
	).reloadAfterServer().build(METAcraftLootContainers.MODID);

	private final PMap<LootContainerType<?>, LootContainer> defaultContainerData;
	private final PMap<LootContainerEventType<?>, LootContainerEvent> defaultEventData;

	public LootContainerConfig(
			PMap<LootContainerType<?>, LootContainer> defaultContainerData,
			PMap<LootContainerEventType<?>, LootContainerEvent> defaultEventData
	) {
		this.defaultContainerData = defaultContainerData;
		this.defaultEventData = defaultEventData;

	}

	public LootContainerConfig() {
		this(HashTreePMap.empty(), HashTreePMap.empty());
	}

	public LootContainer getDefaultContainer(LootContainerType<?> key) {
		return defaultContainerData.get(key);
	}

	public LootContainerConfig withContainers(PMap<LootContainerType<?>, LootContainer> defaultContainerData) {
		return new LootContainerConfig(defaultContainerData, defaultEventData);
	}

	public LootContainerConfig withEvents(PMap<LootContainerEventType<?>, LootContainerEvent> defaultEventData) {
		return new LootContainerConfig(defaultContainerData, defaultEventData);
	}

	public static void putDefaultContainer(LootContainerType<?> key, LootContainer defaultContainer) {
		config.modify(config -> {
			if (config.defaultContainerData.containsKey(key)) return config;
			return config.withContainers(config.defaultContainerData.plus(key, defaultContainer));
		});
	}

	public LootContainerEvent getDefaultEvent(LootContainerEventType<?> key) {
		return defaultEventData.get(key);
	}

	public static void putDefaultEvent(LootContainerEventType<?> key, LootContainerEvent defaultEvent) {
		config.modify(config -> {
			if (config.defaultEventData.containsKey(key)) return config;
			return config.withEvents(config.defaultEventData.plus(key, defaultEvent));
		});
	}

	/**
	 * Returns the config.
	 * DO NOT CACHE THIS IN VARIABLES FOR LONGER PERIODS OF TIME!
	 * @return The config.
	 */
	public static LootContainerConfig getConfig() {
		return LootContainerConfig.config.get();
	}

}
