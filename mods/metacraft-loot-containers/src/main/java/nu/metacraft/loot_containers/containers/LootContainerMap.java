package nu.metacraft.loot_containers.containers;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class LootContainerMap<T> {

	private final Table<ResourceKey<Level>, T, LootContainer> containers = HashBasedTable.create();
	private final Table<ResourceKey<Level>, T, Runnable> tickers = HashBasedTable.create();
	private final Table<ResourceKey<Level>, T, Runnable> tickerTemp = HashBasedTable.create();

	private boolean isIteratingTickers = false;

	private final Codec<T> codec;

	private final TriConsumer<ResourceKey<Level>, T, LootContainer> lootContainerInitializer;

	public LootContainerMap(
			Codec<T> codec, TriConsumer<ResourceKey<Level>, T, LootContainer> lootContainerInitializer
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
			ResourceKey<Level> dim, T pos
	) {
		containers.remove(dim, pos);
		removeTicker(dim, pos);
	}

	public void putLootContainer(
			ResourceKey<Level> dim, T pos, LootContainer container
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

	public LootContainer getLootContainer(ResourceKey<Level> dim, T pos) {
		return containers.get(dim, pos);
	}

	private void addTicker(ResourceKey<Level> dim, T pos, Runnable ticker) {
		if (!isIteratingTickers) {
			tickers.put(dim, pos, ticker);
		} else {
			tickerTemp.put(dim, pos, ticker);
		}
	}

	private void removeTicker(ResourceKey<Level> dim, T pos) {
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

	public record SerializedMap<T>(Map<ResourceKey<Level>, List<Container<T>>> map) {

		public static <T> Codec<SerializedMap<T>> createCodec(Codec<T> posCodec) {
			return Codec.unboundedMap(
					Level.RESOURCE_KEY_CODEC, Container.createCodec(posCodec).listOf()
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
