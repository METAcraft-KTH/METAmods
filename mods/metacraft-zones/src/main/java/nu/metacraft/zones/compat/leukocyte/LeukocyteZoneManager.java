package nu.metacraft.zones.compat.leukocyte;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.zones.METAcraftZones;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.compat.mixin.IndexedAuthorityMapAccessor;
import nu.metacraft.zones.zone.Zone;
import xyz.nucleoid.leukocyte.Leukocyte;
import xyz.nucleoid.leukocyte.authority.Authority;
import xyz.nucleoid.leukocyte.rule.ProtectionExclusions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LeukocyteZoneManager {

	private static final String SEPARATOR = "-+-";

	public static String getAuthorityName(Zone zone) {
		return zone.getName() + SEPARATOR + METAcraftZones.MODID;
	}

	public static Authority getAuthorityFromZone(Zone zone) {
		return Leukocyte.get(zone.getWorld().getServer()).getAuthorityByKey(getAuthorityName(zone));
	}

	public static boolean isMETAcraftZoneName(String name) {
		return name.endsWith(SEPARATOR + METAcraftZones.MODID);
	}

	public static String getZoneNameFromAuthorityName(String name) {
		return name.substring(0, name.length() - SEPARATOR.length() - METAcraftZones.MODID.length());
	}

	public static void onZoneAdd(MinecraftServer server, Zone zone) {
		String name = getAuthorityName(zone);
		Leukocyte.get(server).addAuthority(Authority.create(name).addShape(
				name, new ZoneShape(zone.getName())
		));
		fixProtectionExclusions(zone, Leukocyte.get(server).getAuthorityByKey(name));
	}

	public static void onZoneRemove(MinecraftServer server, Zone zone) {
		Leukocyte.get(server).removeAuthority(getAuthorityName(zone));
	}

	public static void updateZoneDimensions(MinecraftServer server, Zone zone) {
		var leukocyte = Leukocyte.get(server);
		if (leukocyte.getAuthorities() instanceof IndexedAuthorityMapAccessor map) {
			var name = getAuthorityName(zone);
			map.callRemoveFromDimension(name);
			switch (leukocyte.getAuthorityByKey(name)) {
				case null -> {}
				case Authority auth -> map.callAddToDimension(auth);
			}
		}
	}

	public static Optional<Zone> getZoneFromExclusions(ProtectionExclusions exclusions) {
		return ((ExclusionData) (Object) exclusions).metacraft_zones$getZone();
	}

	private static void fixProtectionExclusions(Zone zone, Authority authority) {
		((ExclusionData) (Object) authority.getExclusions()).metacraft_zones$setZone(zone);
	}

	public static void init() {
		ZoneShape.REGISTRY.register(METAcraftZones.MODID, ZoneShape.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			var zones = ZoneManager.getInstance(server);
			zones.fixLeukocyteLoading();
			var leukocyte = Leukocyte.get(server);


			List<String> authoritiesToRemove = new ArrayList<>();
			leukocyte.getAuthorities().forEach(authority -> {
				if (isMETAcraftZoneName(authority.getKey())) {
					var name = getZoneNameFromAuthorityName(authority.getKey());
					if (zones.containsZone(name)) {
						fixProtectionExclusions(zones.getZone(name), authority);
					} else {
						authoritiesToRemove.add(authority.getKey());
					}
				}
			});
			for (String authority : authoritiesToRemove) {
				METAcraftZones.LOGGER.error("Found METAcraft authority " + authority + " with no matching zone, removing it.");
				leukocyte.removeAuthority(authority);
			}

			zones.getZones().getZones().forEach(zone -> {
				String authorityName = getAuthorityName(zone);
				Authority authority = leukocyte.getAuthorityByKey(authorityName);
				if (authority == null) {
					METAcraftZones.LOGGER.warn("METAcraft zone " + zone.getName() + " has no authority! Creating it.");
					onZoneAdd(server, zone);
				}
			});

		});
	}

}
