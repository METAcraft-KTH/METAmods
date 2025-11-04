package nu.metacraft.core.util.helper;

import nu.metacraft.core.extensions.ServerPlayerEntityExtensions;
import nu.metacraft.core.music.PlayerMusic;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

public class MusicHelper {

	public static boolean isMusicPlaying(ServerPlayer player, PlayerMusic music) {
		return ((ServerPlayerEntityExtensions) player).metacraft_core$hasMusic(music);
	}

	public static void playMusic(ServerPlayer player, PlayerMusic music) {
		playMusic(player, music, false);
	}

	public static void playMusic(ServerPlayer player, PlayerMusic music, Predicate<ServerPlayer> continuePlaying) {
		playMusic(player, music, false, continuePlaying);
	}

	public static void playMusic(ServerPlayer player, PlayerMusic music, boolean skipQueue) {
		playMusic(player, music, skipQueue, p -> true);
	}

	public static void playMusic(ServerPlayer player, PlayerMusic music, boolean skipQueue, Predicate<ServerPlayer> continuePlaying) {
		((ServerPlayerEntityExtensions) player).metacraft_core$playMusic(music, skipQueue, continuePlaying);
	}

	public static void replaceMusic(ServerPlayer player, PlayerMusic prevMusic, PlayerMusic newMusic) {
		if (!isMusicPlaying(player, prevMusic) || !Objects.equals(prevMusic, newMusic)) {
			stopMusic(player, prevMusic);
		}
		playMusic(player, newMusic);
	}

	public static void stopMusic(ServerPlayer player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$stopMusic(null);
	}

	public static void stopMusic(ServerPlayer player, PlayerMusic prev) {
		((ServerPlayerEntityExtensions) player).metacraft_core$stopMusic(prev);
	}

	public static void clearAllMusic(ServerPlayer player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$clearAllMusic();
	}

	public static void resetMusicTimer(ServerPlayer player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$resetMusicTimer();
	}

	public static void replacePredicate(ServerPlayer player, PlayerMusic music, Predicate<ServerPlayer> continuePlaying) {
		((ServerPlayerEntityExtensions) player).metacraft_core$replacePredicate(music, continuePlaying);
	}

}
