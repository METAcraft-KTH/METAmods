package se.datasektionen.mc.metacraft_lib.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.path.PathUtil;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class SeparateStatHandler extends ServerStatHandler {

	private final Identifier type;

	private static File prepareFile(Path path) {
		try {
			PathUtil.createDirectories(path.getParent());
		} catch (IOException e) {
			METAcraftLib.LOGGER.error(e.getMessage(), e);
		}
		return path.toFile();
	}

	public SeparateStatHandler(MinecraftServer server, ServerPlayerEntity owner, Identifier type) {
		super(
				server,
				prepareFile(SeparateAdvancementTracker.getPath(WorldSavePath.STATS, owner, type))
		);
		this.type = type;

	}

	public Identifier getType() {
		return type;
	}
}
