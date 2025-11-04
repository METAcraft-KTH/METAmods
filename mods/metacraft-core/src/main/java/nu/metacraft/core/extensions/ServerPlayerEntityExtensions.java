package nu.metacraft.core.extensions;

import nu.metacraft.core.music.MusicEntry;
import nu.metacraft.core.music.PlayerMusic;
import nu.metacraft.core.preferences.PreferenceData;
import nu.metacraft.core.util.helper.MusicHelper;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

public interface ServerPlayerEntityExtensions {


	void metacraft_core$playMusic(PlayerMusic entry, boolean skipQueue, Predicate<ServerPlayer> predicate);
	void metacraft_core$stopMusic(PlayerMusic entry);

	void metacraft_core$replacePredicate(PlayerMusic entry, Predicate<ServerPlayer> predicate);

	void metacraft_core$clearAllMusic();

	/**
	 * Don't use this to set custom music, use
	 * {@link MusicHelper#isMusicPlaying(ServerPlayer, PlayerMusic)}
	 * instead!
	 */
	boolean metacraft_core$hasMusic(PlayerMusic entry);

	boolean metacraft_core$hasMusicEntry(MusicEntry entry);

	void metacraft_core$resetMusicTimer();

	void metacraft_core$setBlocksPistonMovable(boolean movable);

	boolean metacraft_core$areBlocksPistonMovable();

	PreferenceData metacraft_core$getPreferences();
}
