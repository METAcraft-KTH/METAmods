package nu.metacraft.portal_blocker.zone;

import com.google.common.collect.ImmutableList;
import net.minecraft.registry.Registry;
import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.zones.zone.data.ZoneDataRegistry;
import nu.metacraft.zones.zone.data.ZoneDataType;

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
