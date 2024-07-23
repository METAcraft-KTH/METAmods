package se.datasektionen.mc.loot_containers.containers.events;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.loot_containers.METAcraftLootContainers;
import se.datasektionen.mc.metacraft_lib.time_getter.Daily;

import java.time.LocalTime;

public class LootContainerEventRegistry {

	public static final Registry<LootContainerEventType<?>> REGISTRY = FabricRegistryBuilder.<LootContainerEventType<?>>createSimple(
			RegistryKey.ofRegistry(METAcraftLootContainers.getID("loot_container_event"))
	).buildAndRegister();

	static {
		RegistryEntryAddedCallback.event(REGISTRY).register((rawId, id, object) -> {
			object.initConfig();
		});
	}

	public static final LootContainerEventType<FillAllRefillablesEvent> FILL_ALL_REFILLABLES = register(
			"fill_all_refillables", new LootContainerEventType<>(
					FillAllRefillablesEvent.CODEC, new FillAllRefillablesEvent(
							RegistryKey.of(RegistryKeys.LOOT_TABLE, METAcraftLootContainers.getID("super_refill")),
							new Daily(LocalTime.of(19, 0))
					)
			)
	);

	public static void init() {

	}

	private static <T extends LootContainerEvent> LootContainerEventType<T> register(String id, LootContainerEventType<T> type) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(id), type);
	}

}
