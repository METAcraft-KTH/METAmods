package nu.metacraft.core.extensions;

import nu.metacraft.core.music.MusicEntry;

import java.util.Optional;

public interface CommandBossBarSerializedExtension {

	Optional<MusicEntry> metacraft_core$getMusic();

	void metacraft_core$setMusic(Optional<MusicEntry> music);

}
