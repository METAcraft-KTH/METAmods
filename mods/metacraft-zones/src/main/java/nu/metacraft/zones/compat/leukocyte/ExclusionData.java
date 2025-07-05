package nu.metacraft.zones.compat.leukocyte;

import nu.metacraft.zones.zone.Zone;

import java.util.Optional;

public interface ExclusionData {

	void metacraft_zones$setZone(Zone zone);

	Optional<Zone> metacraft_zones$getZone();

}
