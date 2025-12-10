package nu.metacraft.loot_containers.containers.events;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.loot.LootTable;
import nu.metacraft.lib.time_getter.RegularTimeGetter;
import nu.metacraft.loot_containers.containers.LootContainerData;
import nu.metacraft.loot_containers.containers.RefillingContainer;

import java.time.Instant;
import java.util.Optional;

public class FillAllRefillablesEvent extends LootContainerEvent {

	public static final MapCodec<FillAllRefillablesEvent> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("lootTable").forGetter(fill -> fill.lootTable),
					RegularTimeGetter.REGISTRY_CODEC.fieldOf("updateInterval").forGetter(fill -> fill.updateInterval),
					ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("next").forGetter(fill -> Optional.of(fill.next))
			).apply(instance, FillAllRefillablesEvent::new)
	);

	private final ResourceKey<LootTable> lootTable;
	private final RegularTimeGetter updateInterval;

	private Instant next;

	public FillAllRefillablesEvent(ResourceKey<LootTable> lootTable, RegularTimeGetter updateInterval) {
		this(lootTable, updateInterval, Optional.empty());
	}

	public FillAllRefillablesEvent(ResourceKey<LootTable> lootTable, RegularTimeGetter updateInterval, Optional<Instant> next) {
		this.lootTable = lootTable;
		this.updateInterval = updateInterval;
		this.next = next.orElse(updateInterval.getNextTime(Instant.now()));
	}


	@Override
	public void tick(String group, LootContainerData data, MinecraftServer server) {
		var now = Instant.now();
		if (now.isAfter(next)) {
			next = updateInterval.getNextTime(now);
			markModified();
			var containers = data.getAllLootContainers(group).filter(
					container -> container instanceof RefillingContainer
			).map(
					container -> (RefillingContainer) container
			).toList();
			var container = containers.get(server.overworld().getRandom().nextInt(containers.size()));
			container.addLootTable(lootTable, Short.MAX_VALUE);
		}
	}

	@Override
	public LootContainerEventType<?> getType() {
		return LootContainerEventRegistry.FILL_ALL_REFILLABLES;
	}
}
