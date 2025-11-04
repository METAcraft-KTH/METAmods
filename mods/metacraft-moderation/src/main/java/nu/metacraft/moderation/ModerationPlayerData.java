package nu.metacraft.moderation;

import nu.metacraft.moderation.moderator_mode.ModerationModeState;

import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

public interface ModerationPlayerData {

	Optional<ModerationModeState> METAcraft_Moderation$getModerationMode();
	void METAcraft_Moderation$setModerationMode(ModerationModeState moderationMode);

	void METAcraft_Moderation$setDefaultModerationMode(String mode);

	Optional<String> METAcraft_Moderation$getDefaultModerationMode();

	Map<String, CompoundTag> METAcraft_Moderation$getSavedNBT();

}
