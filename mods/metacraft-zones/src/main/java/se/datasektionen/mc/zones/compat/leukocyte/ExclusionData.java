package se.datasektionen.mc.zones.compat.leukocyte;

import se.datasektionen.mc.zones.zone.Zone;

import java.util.Optional;

public interface ExclusionData {

	void metacraft_zones$setZone(Zone zone);

	Optional<Zone> metacraft_zones$getZone();

}
