package nu.metacraft.loot_containers.containers;

import com.mojang.serialization.MapCodec;
import nu.metacraft.loot_containers.LootContainerConfig;

import java.util.Optional;

public class LootContainerType<T extends LootContainer> {

	private final MapCodec<T> codec;
	private final T defaults;

	public LootContainerType(MapCodec<T> codec, T defaults) {
		this.codec = codec;
		this.defaults = defaults;
	}

	public MapCodec<T> codec() {
		return codec;
	}

	public void initConfig() {
		LootContainerConfig.putDefaultContainer(this, defaults);
	}

	public T createDefault() {
		return (T) Optional.ofNullable(
				LootContainerConfig.getConfig().getDefaultContainer(this).copy()
		).orElse(defaults.copy());
	}
}
