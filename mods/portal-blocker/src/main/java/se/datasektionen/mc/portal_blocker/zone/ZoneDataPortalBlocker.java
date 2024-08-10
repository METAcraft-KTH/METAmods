package se.datasektionen.mc.portal_blocker.zone;

import com.google.common.collect.ImmutableList;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.portal_blocker.PortalBlocker;
import se.datasektionen.mc.portal_blocker.PortalState;
import se.datasektionen.mc.zones.zone.data.ZoneDataRegistry;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;

public class ZoneDataPortalBlocker {

	public static final ZoneDataType<PortalZoneData> PORTAL_DATA = Registry.register(
			ZoneDataRegistry.REGISTRY, PortalBlocker.getID("portal-blocker"),
			new ZoneDataType<>(
					PortalZoneData.CODEC,
					() -> new PortalZoneData(
							ImmutableList.of(),
							new PortalState()
					)
			)
	);

	public static void init() {

	}

}
