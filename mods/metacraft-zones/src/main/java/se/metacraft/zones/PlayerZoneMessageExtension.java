package se.metacraft.zones;

import se.metacraft.zones.zone.Zone;
import se.metacraft.zones.zone.data.MessageZoneData;

public interface PlayerZoneMessageExtension {

	void metacraft$addZoneMessage(Zone zone, MessageZoneData.MessageEntry message);
	void metacraft$removeZoneMessage(Zone zone);
	MessageZoneData.MessageEntry metacraft$getZoneMessage(Zone zone);

}
