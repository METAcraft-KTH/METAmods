package nu.metacraft.core.extensions;

import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.core.music.MusicEntry;
import nu.metacraft.core.preferences.PreferenceData;
import nu.metacraft.core.util.helper.MusicHelper;

import java.util.function.Predicate;

public interface ServerPlayerEntityExtensions {


	void metacraft_core$playMusic(MusicEntry entry, boolean skipQueue, Predicate<ServerPlayerEntity> predicate);
	void metacraft_core$stopMusic(MusicEntry entry);

	void metacraft_core$replacePredicate(MusicEntry entry, Predicate<ServerPlayerEntity> predicate);

	void metacraft_core$clearAllMusic();

	/**
	 * Don't use this to set custom music, use
	 * {@link MusicHelper#isMusicPlaying(ServerPlayerEntity, MusicEntry)}
	 * instead!
	 */
	boolean metacraft_core$hasMusicEntry(MusicEntry entry);

	void metacraft_core$resetMusicTimer();

	void metacraft_core$setBlocksPistonMovable(boolean movable);

	boolean metacraft_core$areBlocksPistonMovable();

	PreferenceData metacraft_core$getPreferences();
}
