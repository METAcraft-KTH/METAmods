package se.datasektionen.mc.loot_containers.containers;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.apache.logging.log4j.util.TriConsumer;
import se.datasektionen.mc.loot_containers.METAcraftLootContainers;

import java.util.Collection;
import java.util.Collections;

public class LootContainerMap<T> {

	private static final String POS = "Pos";
	private static final String DATA = "Data";

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

	public void readNBT(NbtCompound nbt) {
		this.containers.clear();
		for (var dim : nbt.getKeys()) {
			Identifier dimensionID = Identifier.tryParse(dim);
			if (dimensionID == null) {
				METAcraftLootContainers.LOGGER.error("Dimension " + dim + " could not be parsed!");
				continue;
			}
			var key = RegistryKey.of(RegistryKeys.WORLD, dimensionID);
			NbtList containers = nbt.getList(dim, NbtElement.COMPOUND_TYPE);
			for (var c : containers) {
				NbtCompound container = (NbtCompound) c;
				codec.parse(NbtOps.INSTANCE, container.get(POS)).resultOrPartial(
						METAcraftLootContainers.LOGGER::error
				).ifPresent(pos -> {
					LootContainer.REGISTRY_CODEC.parse(NbtOps.INSTANCE, container.getCompound(DATA)).resultOrPartial(
							METAcraftLootContainers.LOGGER::error
					).ifPresent(lootContainer -> {
						putLootContainer(key, pos, lootContainer);
					});
				});
			}
		}
	}

	public NbtCompound writeNbt(NbtCompound nbt) {
		for (var dim : containers.rowMap().entrySet()) {
			NbtList containers = new NbtList();
			for (var container : dim.getValue().entrySet()) {
				LootContainer.REGISTRY_CODEC.encodeStart(NbtOps.INSTANCE, container.getValue()).resultOrPartial(
						METAcraftLootContainers.LOGGER::error
				).ifPresent(lootContainer -> {
					codec.encodeStart(NbtOps.INSTANCE, container.getKey()).resultOrPartial(
							METAcraftLootContainers.LOGGER::error
					).ifPresent(pos -> {
						NbtCompound data = new NbtCompound();
						data.put(DATA, lootContainer);
						data.put(POS, pos);
						containers.add(data);
					});
				});
			}
			nbt.put(dim.getKey().getValue().toString(), containers);
		}
		return nbt;
	}

	public Collection<LootContainer> getLootContainers() {
		return Collections.unmodifiableCollection(containers.values());
	}

	public boolean isEmpty() {
		return containers.isEmpty();
	}

}
