package se.datasektionen.mc.zones.zone.data;

import com.google.common.collect.HashMultimap;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import se.datasektionen.mc.zones.METAcraftZones;
import se.datasektionen.mc.zones.spawns.SpawnRemoverRegistry;

import java.util.ArrayList;
import java.util.Optional;

public class ZoneDataRegistry {

	public static final Registry<ZoneDataType<?>> REGISTRY = FabricRegistryBuilder.<ZoneDataType<?>>createSimple(
			RegistryKey.ofRegistry(METAcraftZones.getID("data_types"))
	).buildAndRegister();


	public static final ZoneDataType<MessageZoneData> MESSAGE = Registry.register(
			REGISTRY, METAcraftZones.getID("message"),
			new ZoneDataType<>(MessageZoneData.CODEC, () -> new MessageZoneData(Optional.empty(), Optional.empty()))
	);
	public static final ZoneDataType<AdditionalSpawnsZoneData> SPAWN = Registry.register(
			REGISTRY, METAcraftZones.getID("spawn"),
			new ZoneDataType<>(AdditionalSpawnsZoneData.CODEC, () -> new AdditionalSpawnsZoneData(HashMultimap.create(), new ArrayList<>(), new ArrayList<>()))
	);

	public static void init() {
		SpawnRemoverRegistry.init();
	}

}
