package se.datasektionen.mc.metacraft_core.util.helper;

import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;

import java.util.function.Predicate;

public class MusicHelper {

	public static boolean isMusicPlaying(ServerPlayerEntity player, MusicEntry music) {
		return ((ServerPlayerEntityExtensions) player).metacraft_lib$hasMusicEntry(music);
	}

	public static boolean playMusic(ServerPlayerEntity player, MusicEntry music) {
		return ((ServerPlayerEntityExtensions) player).metacraft_lib$setMusicEntry(music);
	}

	public static void playMusic(ServerPlayerEntity player, MusicEntry music, Predicate<ServerPlayerEntity> continuePlaying) {
		if (playMusic(player, music)) {
			((ServerPlayerEntityExtensions) player).metacraft_lib$setContinuePlayingMusicPredicate(continuePlaying);
		}
	}

	public static boolean stopMusic(ServerPlayerEntity player) {
		return playMusic(player, null);
	}

}
