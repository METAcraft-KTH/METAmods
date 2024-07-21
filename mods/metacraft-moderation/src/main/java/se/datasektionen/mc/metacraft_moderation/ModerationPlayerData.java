package se.datasektionen.mc.metacraft_moderation;

import net.minecraft.nbt.NbtCompound;
import se.datasektionen.mc.metacraft_moderation.moderator_mode.ModerationModeState;

import java.util.Map;
import java.util.Optional;

public interface ModerationPlayerData {

	Optional<ModerationModeState> METAcraft_Moderation$getModerationMode();
	void METAcraft_Moderation$setModerationMode(ModerationModeState moderationMode);

	void METAcraft_Moderation$setDefaultModerationMode(String mode);

	Optional<String> METAcraft_Moderation$getDefaultModerationMode();

	Map<String, NbtCompound> METAcraft_Moderation$getSavedNBT();

}
