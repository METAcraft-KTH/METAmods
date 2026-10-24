package se.metacraft.portal_blocker.zone;

import se.metacraft.portal_blocker.PortalBlocker;
import se.metacraft.zones.zone.data.ZoneDataRegistry;
import se.metacraft.zones.zone.data.ZoneDataType;

import java.util.Map;
import net.minecraft.core.Registry;

public class ZoneDataPortalBlocker {

	public static final ZoneDataType<PortalZoneData> PORTAL_DATA = Registry.register(
			ZoneDataRegistry.REGISTRY, PortalBlocker.getID("portal-blocker"),
			new ZoneDataType<>(
					PortalZoneData.CODEC,
					() -> new PortalZoneData(
							Map.of()
					)
			)
	);

	public static void init() {

	}

}
