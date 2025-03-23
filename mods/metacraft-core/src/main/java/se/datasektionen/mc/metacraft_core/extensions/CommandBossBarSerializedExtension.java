package se.datasektionen.mc.metacraft_core.extensions;

import se.datasektionen.mc.metacraft_core.music.MusicEntry;

import java.util.Optional;

public interface CommandBossBarSerializedExtension {

	Optional<MusicEntry> metacraft_core$getMusic();

	void metacraft_core$setMusic(Optional<MusicEntry> music);

}
