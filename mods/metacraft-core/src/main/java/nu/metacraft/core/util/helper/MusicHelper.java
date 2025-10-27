package nu.metacraft.core.util.helper;

import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.core.extensions.ServerPlayerEntityExtensions;
import nu.metacraft.core.music.PlayerMusic;

import java.util.Objects;
import java.util.function.Predicate;

public class MusicHelper {

	public static boolean isMusicPlaying(ServerPlayerEntity player, PlayerMusic music) {
		return ((ServerPlayerEntityExtensions) player).metacraft_core$hasMusic(music);
	}

	public static void playMusic(ServerPlayerEntity player, PlayerMusic music) {
		playMusic(player, music, false);
	}

	public static void playMusic(ServerPlayerEntity player, PlayerMusic music, Predicate<ServerPlayerEntity> continuePlaying) {
		playMusic(player, music, false, continuePlaying);
	}

	public static void playMusic(ServerPlayerEntity player, PlayerMusic music, boolean skipQueue) {
		playMusic(player, music, skipQueue, p -> true);
	}

	public static void playMusic(ServerPlayerEntity player, PlayerMusic music, boolean skipQueue, Predicate<ServerPlayerEntity> continuePlaying) {
		((ServerPlayerEntityExtensions) player).metacraft_core$playMusic(music, skipQueue, continuePlaying);
	}

	public static void replaceMusic(ServerPlayerEntity player, PlayerMusic prevMusic, PlayerMusic newMusic) {
		if (!isMusicPlaying(player, prevMusic) || !Objects.equals(prevMusic, newMusic)) {
			stopMusic(player, prevMusic);
		}
		playMusic(player, newMusic);
	}

	public static void stopMusic(ServerPlayerEntity player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$stopMusic(null);
	}

	public static void stopMusic(ServerPlayerEntity player, PlayerMusic prev) {
		((ServerPlayerEntityExtensions) player).metacraft_core$stopMusic(prev);
	}

	public static void clearAllMusic(ServerPlayerEntity player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$clearAllMusic();
	}

	public static void resetMusicTimer(ServerPlayerEntity player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$resetMusicTimer();
	}

	public static void replacePredicate(ServerPlayerEntity player, PlayerMusic music, Predicate<ServerPlayerEntity> continuePlaying) {
		((ServerPlayerEntityExtensions) player).metacraft_core$replacePredicate(music, continuePlaying);
	}

}
