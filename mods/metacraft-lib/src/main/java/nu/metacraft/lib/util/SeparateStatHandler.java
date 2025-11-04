package nu.metacraft.lib.util;

import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.storage.LevelResource;
import nu.metacraft.lib.METAcraftLib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class SeparateStatHandler extends ServerStatsCounter {

	private final ResourceLocation type;

	private static File prepareFile(Path path) {
		try {
			FileUtil.createDirectoriesSafe(path.getParent());
		} catch (IOException e) {
			METAcraftLib.LOGGER.error(e.getMessage(), e);
		}
		return path.toFile();
	}

	public SeparateStatHandler(MinecraftServer server, ServerPlayer owner, ResourceLocation type) {
		super(
				server,
				prepareFile(SeparateAdvancementTracker.getPath(LevelResource.PLAYER_STATS_DIR, owner, type))
		);
		this.type = type;

	}

	public ResourceLocation getType() {
		return type;
	}
}
