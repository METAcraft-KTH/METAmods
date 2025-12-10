package nu.metacraft.lib.util;

import net.minecraft.util.FileUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.storage.LevelResource;
import nu.metacraft.lib.METAcraftLib;

import java.io.IOException;
import java.nio.file.Path;

public class SeparateStatHandler extends ServerStatsCounter {

	private final Identifier type;

	private static Path prepareFile(Path path) {
		try {
			FileUtil.createDirectoriesSafe(path.getParent());
		} catch (IOException e) {
			METAcraftLib.LOGGER.error(e.getMessage(), e);
		}
		return path;
	}

	public SeparateStatHandler(MinecraftServer server, ServerPlayer owner, Identifier type) {
		super(
				server,
				prepareFile(SeparateAdvancementTracker.getPath(LevelResource.PLAYER_STATS_DIR, owner, type))
		);
		this.type = type;

	}

	public Identifier getType() {
		return type;
	}
}
