package nu.metacraft.loot_containers.containers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;

public class RefillingContainer extends LootContainer {

	public static final MapCodec<RefillingContainer> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.INT.fieldOf("refillDelay").forGetter(container -> container.refillDelay),
			Codec.INT.fieldOf("timer").orElse(0).forGetter(container -> container.timer),
			ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("autoUpdatingLootTable").forGetter(container -> container.autoUpdatingLootTable),
			Codec.unboundedMap(
					ResourceKey.codec(Registries.LOOT_TABLE), LootTableEntry.CODEC
			).fieldOf("lootTableCounts").orElse(new HashMap<>()).forGetter(container -> container.lootTableCounts),
			Codec.INT.fieldOf("maxLootTableCount").orElse(64).forGetter(container -> container.maxLootTableCount)
		).apply(instance, RefillingContainer::new)
	);

	private final int refillDelay;
	private int timer;
	private final ResourceKey<LootTable> autoUpdatingLootTable;
	private final Map<ResourceKey<LootTable>, LootTableEntry> lootTableCounts;
	private final int maxLootTableCount;

	public RefillingContainer(int refillDelay, ResourceKey<LootTable> autoUpdatingLootTable) {
		this(refillDelay, 0, autoUpdatingLootTable, new HashMap<>(), 64);
	}

	public RefillingContainer(
			int refillDelay, int timer, ResourceKey<LootTable> autoUpdatingLootTable,
			Map<ResourceKey<LootTable>, LootTableEntry> lootTableCounts, int maxLootTableCount
	) {
		this.refillDelay = refillDelay;
		this.timer = timer;
		this.autoUpdatingLootTable = autoUpdatingLootTable;
		this.lootTableCounts = lootTableCounts instanceof HashMap<ResourceKey<LootTable>, LootTableEntry> ? lootTableCounts : new HashMap<>(lootTableCounts);
		this.maxLootTableCount = maxLootTableCount;
	}

	public void addLootTable(ResourceKey<LootTable> lootTable, int priority) {
		var entry = lootTableCounts.computeIfAbsent(lootTable, key -> new LootTableEntry(0, priority));
		entry.increment();
		if (entry.getPriority() != priority) {
			entry.setPriority(priority);
		}
		markDirty();
	}


	@Override
	public void onOpen(@Nullable ServerPlayer player) {
		access.get().ifPresent(access -> {
			lootTableCounts.entrySet().stream().sorted(
					Comparator.comparingInt(entry -> -entry.getValue().getPriority())
			).forEach(entry -> {
				for (int i = 0; i < entry.getValue().getAmount(); i++) {
					access.generateLootTable(entry.getKey(), player);
				}
			});
			lootTableCounts.clear();
			markDirty();
		});
	}

	@Override
	public Runnable getTicker() {
		return () -> {
			if (timer > 0) {
				timer--;
				markDirty();
			} else {
				timer = refillDelay;
				var value = lootTableCounts.computeIfAbsent(autoUpdatingLootTable, key -> new LootTableEntry(0, 0));
				if (value.getAmount() < maxLootTableCount) {
					value.increment();
				}
				markDirty();
			}
		};
	}

	@Override
	public LootContainerType<? extends LootContainer> getType() {
		return LootContainerRegistry.REFILLING;
	}

	public static class LootTableEntry {

		private int amount;
		private int priority;

		public static final Codec<LootTableEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.fieldOf("amount").forGetter(entry -> entry.amount),
						Codec.INT.fieldOf("priority").forGetter(entry -> entry.priority)
				).apply(instance, LootTableEntry::new)
		);

		public LootTableEntry(int amount, int priority) {
			this.amount = amount;
			this.priority = priority;
		}

		public int getAmount() {
			return amount;
		}

		public void increment() {
			amount++;
		}

		public int getPriority() {
			return priority;
		}

		public void setPriority(int priority) {
			this.priority = priority;
		}

	}
}
