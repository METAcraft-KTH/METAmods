package nu.metacraft.loot_containers.containers.events;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import nu.metacraft.loot_containers.METAcraftLootContainers;
import nu.metacraft.lib.time_getter.Daily;

import java.time.LocalTime;

public class LootContainerEventRegistry {

	public static final Registry<LootContainerEventType<?>> REGISTRY = FabricRegistryBuilder.<LootContainerEventType<?>>createSimple(
			ResourceKey.createRegistryKey(METAcraftLootContainers.getID("loot_container_event"))
	).buildAndRegister();

	static {
		RegistryEntryAddedCallback.event(REGISTRY).register((rawId, id, object) -> {
			object.initConfig();
		});
	}

	public static final LootContainerEventType<FillAllRefillablesEvent> FILL_ALL_REFILLABLES = register(
			"fill_all_refillables", new LootContainerEventType<>(
					FillAllRefillablesEvent.CODEC, new FillAllRefillablesEvent(
							ResourceKey.create(Registries.LOOT_TABLE, METAcraftLootContainers.getID("super_refill")),
							new Daily(LocalTime.of(19, 0))
					)
			)
	);

	public static void init() {

	}

	private static <T extends LootContainerEvent> LootContainerEventType<T> register(String id, LootContainerEventType<T> type) {
		return Registry.register(REGISTRY, ResourceLocation.withDefaultNamespace(id), type);
	}

}
