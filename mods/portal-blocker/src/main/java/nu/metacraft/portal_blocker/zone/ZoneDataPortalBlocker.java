package nu.metacraft.portal_blocker.zone;

import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.zones.zone.data.ZoneDataRegistry;
import nu.metacraft.zones.zone.data.ZoneDataType;

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
