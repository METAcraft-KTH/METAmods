package se.datasektionen.mc.metacraft_core.extensions;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.entity.entities.PlayerMusicPoint;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;
import se.datasektionen.mc.metacraft_core.util.helper.MusicHelper;

import java.util.function.Predicate;

public interface ServerPlayerEntityExtensions {

	/**
	 * Don't use this to set custom music, use
	 * {@link MusicHelper#playMusic(ServerPlayerEntity, MusicEntry)}
	 * instead!
	 */
	default boolean metacraft_lib$setMusicEntry(MusicEntry entry) {
		return metacraft_lib$setMusicEntry(entry, false);
	}

	default boolean metacraft_lib$replaceMusic(MusicEntry prev, MusicEntry newMusic) {
		if (prev == newMusic) return true;
		if (metacraft_lib$hasMusicEntry(prev, false)) {
			return metacraft_lib$setMusicEntry(newMusic, true);
		} else {
			return metacraft_lib$setMusicEntry(newMusic);
		}
	}

	boolean metacraft_lib$setMusicEntry(MusicEntry entry, boolean skipPriorityCheck);
	/**
	 * Don't use this to set custom music, use
	 * {@link MusicHelper#playMusic(ServerPlayerEntity, MusicEntry, Predicate)}
	 * instead!
	 */
	void metacraft_lib$setContinuePlayingMusicPredicate(Predicate<ServerPlayerEntity> predicate);

	/**
	 * Don't use this to set custom music, use
	 * {@link MusicHelper#isMusicPlaying(ServerPlayerEntity, MusicEntry)}
	 * instead!
	 */
	boolean metacraft_lib$hasMusicEntry(MusicEntry entry, boolean allowEquivalent);

	void metacraft_lib$resetMusicTimer();

	PlayerMusicPoint metacraft_lib$getMusicPoint();

	void metacraft_core$setBlocksPistonMovable(boolean movable);

	boolean metacraft_core$areBlocksPistonMovable();


	@Nullable
	RegistryKey<World> metacraft_core$getCampusLodestoneBackWorld();

	@Nullable
	BlockPos metacraft_core$getCampusLodestoneBackPos();

	void metacraft_core$setCampusLodestoneBackPos(RegistryKey<World> world, BlockPos pos);

	void metacraft_core$unsetCampusLodestoneBackPos();
}
