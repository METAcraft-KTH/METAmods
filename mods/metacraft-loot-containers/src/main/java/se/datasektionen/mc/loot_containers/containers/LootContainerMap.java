package se.datasektionen.mc.loot_containers.containers;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LootContainerMap<T> {

	private final Table<RegistryKey<World>, T, LootContainer> containers = HashBasedTable.create();
	private final Table<RegistryKey<World>, T, Runnable> tickers = HashBasedTable.create();
	private final Table<RegistryKey<World>, T, Runnable> tickerTemp = HashBasedTable.create();

	private boolean isIteratingTickers = false;

	private final Codec<T> codec;

	private final TriConsumer<RegistryKey<World>, T, LootContainer> lootContainerInitializer;

	public LootContainerMap(
			Codec<T> codec, TriConsumer<RegistryKey<World>, T, LootContainer> lootContainerInitializer
	) {
		this.codec = codec;
		this.lootContainerInitializer = lootContainerInitializer;
	}

	public void runAllTickers() {
		isIteratingTickers = true;
		for (var ticker : tickers.values()) {
			ticker.run();
		}
		isIteratingTickers = false;
	}

	public void removeLootContainer(
			RegistryKey<World> dim, T pos
	) {
		containers.remove(dim, pos);
		removeTicker(dim, pos);
	}

	public void putLootContainer(
			RegistryKey<World> dim, T pos, LootContainer container
	) {
		lootContainerInitializer.accept(dim, pos, container);
		containers.put(dim, pos, container);
		var ticker = container.getTicker();
		if (ticker != null) {
			addTicker(dim, pos, ticker);
		} else if (tickers.contains(dim, pos)) {
			removeTicker(dim, pos);
		}
	}

	public LootContainer getLootContainer(RegistryKey<World> dim, T pos) {
		return containers.get(dim, pos);
	}

	private void addTicker(RegistryKey<World> dim, T pos, Runnable ticker) {
		if (!isIteratingTickers) {
			tickers.put(dim, pos, ticker);
		} else {
			tickerTemp.put(dim, pos, ticker);
		}
	}

	private void removeTicker(RegistryKey<World> dim, T pos) {
		if (!isIteratingTickers) {
			tickers.remove(dim, pos);
		} else {
			tickerTemp.put(dim, pos, null);
		}
	}

	public SerializedMap<T> serialize() {
		return new SerializedMap<>(
				containers.rowMap().entrySet().stream().map(
						entry -> Pair.of(
								entry.getKey(),
								entry.getValue().entrySet().stream().map(
										e -> new SerializedMap.Container<>(e.getKey(), e.getValue())
								).toList()
						)
				).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond))
		);
	}

	public void deserializeMap(SerializedMap<T> map) {
		map.map.forEach((dim, containers) -> {
			for (var container : containers) {
				putLootContainer(dim, container.pos, container.container);
			}
		});
	}

	public Collection<LootContainer> getLootContainers() {
		return Collections.unmodifiableCollection(containers.values());
	}

	public boolean isEmpty() {
		return containers.isEmpty();
	}

	public record SerializedMap<T>(Map<RegistryKey<World>, List<Container<T>>> map) {

		public static <T> Codec<SerializedMap<T>> createCodec(Codec<T> posCodec) {
			return Codec.unboundedMap(
					World.CODEC, Container.createCodec(posCodec).listOf()
			).xmap(SerializedMap::new, SerializedMap::map);
		}

		public record Container<T>(T pos, LootContainer container) {
			public static <T> Codec<Container<T>> createCodec(Codec<T> posCodec) {
				return RecordCodecBuilder.create(
						instance -> instance.group(
								posCodec.fieldOf("Pos").forGetter(d -> d.pos),
								LootContainer.REGISTRY_CODEC.fieldOf("Data").forGetter(d -> d.container)
						).apply(instance, Container<T>::new)
				);
			}
		}
	}

}
