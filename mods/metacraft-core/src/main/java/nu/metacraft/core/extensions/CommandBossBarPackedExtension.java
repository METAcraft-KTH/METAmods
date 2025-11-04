package nu.metacraft.core.extensions;

import nu.metacraft.core.music.PlayerMusic;

import java.util.Optional;

public interface CommandBossBarPackedExtension {

	Optional<PlayerMusic> metacraft_core$getMusic();

	void metacraft_core$setMusic(Optional<PlayerMusic> music);

}
