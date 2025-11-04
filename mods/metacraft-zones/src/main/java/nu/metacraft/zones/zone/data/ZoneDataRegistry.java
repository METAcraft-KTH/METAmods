package nu.metacraft.zones.zone.data;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import nu.metacraft.zones.METAcraftZones;
import nu.metacraft.zones.spawns.SpawnRemoverRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

public class ZoneDataRegistry {

	public static final Registry<ZoneDataType<?>> REGISTRY = FabricRegistryBuilder.<ZoneDataType<?>>createSimple(
			ResourceKey.createRegistryKey(METAcraftZones.getID("data_types"))
	).buildAndRegister();


	public static final ZoneDataType<MessageZoneData> MESSAGE = Registry.register(
			REGISTRY, METAcraftZones.getID("message"),
			new ZoneDataType<>(MessageZoneData.CODEC, () -> new MessageZoneData(Optional.empty(), Optional.empty()))
	);
	public static final ZoneDataType<AdditionalSpawnsZoneData> SPAWN = Registry.register(
			REGISTRY, METAcraftZones.getID("spawn"),
			new ZoneDataType<>(AdditionalSpawnsZoneData.CODEC, () -> new AdditionalSpawnsZoneData(new HashMap<>(), new ArrayList<>(), new ArrayList<>()))
	);

	public static void init() {
		SpawnRemoverRegistry.init();
	}

}
