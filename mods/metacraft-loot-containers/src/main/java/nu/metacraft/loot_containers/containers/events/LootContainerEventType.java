package nu.metacraft.loot_containers.containers.events;

import com.mojang.serialization.MapCodec;
import nu.metacraft.loot_containers.LootContainerConfig;

import java.util.Optional;

public class LootContainerEventType<T extends LootContainerEvent> {

	private final MapCodec<T> codec;
	private final T defaults;

	public LootContainerEventType(MapCodec<T> codec, T defaults) {
		this.codec = codec;
		this.defaults = defaults;
	}

	public MapCodec<T> codec() {
		return codec;
	}

	public void initConfig() {
		LootContainerConfig.putDefaultEvent(this, defaults);
	}

	public T createDefault() {
		return (T) Optional.ofNullable(
				LootContainerConfig.getConfig().getDefaultEvent(this).copy()
		).orElse(defaults.copy());
	}


}
