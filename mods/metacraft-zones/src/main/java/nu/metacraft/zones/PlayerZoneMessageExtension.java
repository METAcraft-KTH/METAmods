package nu.metacraft.zones;

import nu.metacraft.zones.zone.Zone;
import nu.metacraft.zones.zone.data.MessageZoneData;

public interface PlayerZoneMessageExtension {

	void metacraft$addZoneMessage(Zone zone, MessageZoneData.MessageEntry message);
	void metacraft$removeZoneMessage(Zone zone);
	MessageZoneData.MessageEntry metacraft$getZoneMessage(Zone zone);

}
