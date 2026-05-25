package nu.metacraft.loot_containers.containers;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import nu.metacraft.loot_containers.METAcraftLootContainers;
import nu.metacraft.loot_containers.containers.events.LootContainerEventRegistry;

public class LootContainerRegistry {

	public static final Registry<LootContainerType<?>> REGISTRY = FabricRegistryBuilder.<LootContainerType<?>>create(
			ResourceKey.createRegistryKey(METAcraftLootContainers.getID("loot_container_type"))
	).buildAndRegister();


	static {
		RegistryEntryAddedCallback.event(REGISTRY).register((rawId, id, object) -> {
			object.initConfig();
		});
	}

	public static final LootContainerType<RefillingContainer> REFILLING = register(
			"refilling", new LootContainerType<>(RefillingContainer.CODEC, new RefillingContainer(
					12000, ResourceKey.create(Registries.LOOT_TABLE, METAcraftLootContainers.getID("refilling_container"))
			))
	);

	public static void init() {
		LootContainerEventRegistry.init();
	}

	private static <T extends LootContainer> LootContainerType<T> register(String key, LootContainerType<T> object) {
		return Registry.register(REGISTRY, Identifier.withDefaultNamespace(key), object);
	}

}
