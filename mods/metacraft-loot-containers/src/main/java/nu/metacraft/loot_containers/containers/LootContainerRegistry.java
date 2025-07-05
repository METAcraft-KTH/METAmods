package nu.metacraft.loot_containers.containers;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import nu.metacraft.loot_containers.METAcraftLootContainers;
import nu.metacraft.loot_containers.containers.events.LootContainerEventRegistry;

public class LootContainerRegistry {

	public static final Registry<LootContainerType<?>> REGISTRY = FabricRegistryBuilder.<LootContainerType<?>>createSimple(
			RegistryKey.ofRegistry(METAcraftLootContainers.getID("loot_container_type"))
	).buildAndRegister();


	static {
		RegistryEntryAddedCallback.event(REGISTRY).register((rawId, id, object) -> {
			object.initConfig();
		});
	}

	public static final LootContainerType<RefillingContainer> REFILLING = register(
			"refilling", new LootContainerType<>(RefillingContainer.CODEC, new RefillingContainer(
					12000, RegistryKey.of(RegistryKeys.LOOT_TABLE, METAcraftLootContainers.getID("refilling_container"))
			))
	);

	public static void init() {
		LootContainerEventRegistry.init();
	}

	private static <T extends LootContainer> LootContainerType<T> register(String key, LootContainerType<T> object) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(key), object);
	}

}
