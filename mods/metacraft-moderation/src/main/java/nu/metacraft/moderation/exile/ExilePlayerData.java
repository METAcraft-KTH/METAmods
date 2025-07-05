package nu.metacraft.moderation.exile;

import nu.metacraft.zones.zone.Zone;

import java.util.Set;

public interface ExilePlayerData {
	Set<Zone> METAcraft_Moderation$getCurrentZones();

	void METAcraft_Moderation$setCanInteract(boolean canInteract);
	boolean METAcraft_Moderation$canInteract();

}
