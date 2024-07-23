package se.datasektionen.mc.loot_containers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import se.datasektionen.mc.loot_containers.containers.events.LootContainerEvent;
import se.datasektionen.mc.loot_containers.containers.events.LootContainerEventRegistry;
import se.datasektionen.mc.loot_containers.containers.events.LootContainerEventType;
import se.datasektionen.mc.metacraft_lib.config.container.ConfigContainer;
import se.datasektionen.mc.metacraft_lib.config.extensions.Modifiable;
import se.datasektionen.mc.loot_containers.containers.LootContainer;
import se.datasektionen.mc.loot_containers.containers.LootContainerRegistry;
import se.datasektionen.mc.loot_containers.containers.LootContainerType;

import java.nio.file.Path;
import java.util.*;

public class LootContainerConfig implements Modifiable {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve(METAcraftLootContainers.MODID + ".json");

	//Lazy-initialized because otherwise LootContainerRegistry attempts to access config too early, resulting in null pointer exception (because the initConfig event applies while code is in static block).
	public static final Codec<LootContainerConfig> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance -> instance.group(
			Codec.<LootContainerType<?>, LootContainer>dispatchedMap(
					LootContainerRegistry.REGISTRY.getCodec(), key -> key.codec().codec()
			).fieldOf("defaultContainerData").forGetter(config -> config.defaultContainerData),
			Codec.<LootContainerEventType<?>, LootContainerEvent>dispatchedMap(
					LootContainerEventRegistry.REGISTRY.getCodec(), key -> key.codec().codec()
			).fieldOf("defaultEventData").forGetter(config -> config.defaultEventData)
	).apply(instance, LootContainerConfig::new)));

	private static final ConfigContainer<LootContainerConfig> config = ConfigContainer.Builder.create(
			CODEC, configPath, LootContainerConfig::new
	).reloadAfterServer().build();

	private final Map<LootContainerType<?>, LootContainer> defaultContainerData;
	private final Map<LootContainerEventType<?>, LootContainerEvent> defaultEventData;

	private boolean isModified = false;

	public LootContainerConfig(
			Map<LootContainerType<?>, LootContainer> defaultContainerData,
			Map<LootContainerEventType<?>, LootContainerEvent> defaultEventData
	) {
		this.defaultContainerData = defaultContainerData instanceof HashMap<
					LootContainerType<?>, LootContainer
				> ? defaultContainerData : new HashMap<>(defaultContainerData);
		this.defaultEventData = defaultEventData instanceof HashMap<
				LootContainerEventType<?>, LootContainerEvent
				> ? defaultEventData : new HashMap<>(defaultEventData);

	}

	public LootContainerConfig() {
		this(new HashMap<>(), new HashMap<>());
	}

	public LootContainer getDefaultContainer(LootContainerType<?> key) {
		return defaultContainerData.get(key);
	}

	public static void putDefaultContainer(LootContainerType<?> key, LootContainer defaultContainer) {
		config.modify(config -> {
			if (config.defaultContainerData.containsKey(key)) return false;
			config.defaultContainerData.put(key, defaultContainer);
			return true;
		});
	}

	public LootContainerEvent getDefaultEvent(LootContainerEventType<?> key) {
		return defaultEventData.get(key);
	}

	public static void putDefaultEvent(LootContainerEventType<?> key, LootContainerEvent defaultEvent) {
		config.modify(config -> {
			if (config.defaultEventData.containsKey(key)) return false;
			config.defaultEventData.put(key, defaultEvent);
			return true;
		});
	}

	@Override
	public void setModified(boolean modified) {
		isModified = modified;
	}

	@Override
	public boolean isModified() {
		return isModified;
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
