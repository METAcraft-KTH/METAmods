package se.datasektionen.mc.metacraft_core.util.helper;

import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;

import java.util.Objects;
import java.util.function.Predicate;

public class MusicHelper {

	public static boolean isMusicPlaying(ServerPlayerEntity player, MusicEntry music) {
		return ((ServerPlayerEntityExtensions) player).metacraft_core$hasMusicEntry(music);
	}

	public static void playMusic(ServerPlayerEntity player, MusicEntry music) {
		playMusic(player, music, false);
	}

	public static void playMusic(ServerPlayerEntity player, MusicEntry music, Predicate<ServerPlayerEntity> continuePlaying) {
		playMusic(player, music, false, continuePlaying);
	}

	public static void playMusic(ServerPlayerEntity player, MusicEntry music, boolean skipQueue) {
		playMusic(player, music, skipQueue, p -> true);
	}

	public static void playMusic(ServerPlayerEntity player, MusicEntry music, boolean skipQueue, Predicate<ServerPlayerEntity> continuePlaying) {
		((ServerPlayerEntityExtensions) player).metacraft_core$playMusic(music, skipQueue, continuePlaying);
	}

	public static void replaceMusic(ServerPlayerEntity player, MusicEntry prevMusic, MusicEntry newMusic) {
		if (!isMusicPlaying(player, prevMusic) || !Objects.equals(prevMusic, newMusic)) {
			stopMusic(player, prevMusic);
		}
		playMusic(player, newMusic);
	}

	public static void stopMusic(ServerPlayerEntity player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$stopMusic(null);
	}

	public static void stopMusic(ServerPlayerEntity player, MusicEntry prev) {
		((ServerPlayerEntityExtensions) player).metacraft_core$stopMusic(prev);
	}

	public static void clearAllMusic(ServerPlayerEntity player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$clearAllMusic();
	}

	public static void resetMusicTimer(ServerPlayerEntity player) {
		((ServerPlayerEntityExtensions) player).metacraft_core$resetMusicTimer();
	}

	public static void replacePredicate(ServerPlayerEntity player, MusicEntry music, Predicate<ServerPlayerEntity> continuePlaying) {
		((ServerPlayerEntityExtensions) player).metacraft_core$replacePredicate(music, continuePlaying);
	}

}
