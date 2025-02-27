package se.datasektionen.mc.metacraft_lib.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.util.PathUtil;
import net.minecraft.util.WorldSavePath;

public class SeparateStatHandler extends ServerStatHandler {

	private final String suffix;

	public SeparateStatHandler(MinecraftServer server, ServerPlayerEntity owner, String suffix) {
		super(
				server,
				owner.server.getSavePath(WorldSavePath.STATS).resolve(
						owner.getUuid() + "-" + PathUtil.replaceInvalidChars(suffix) + ".json"
				).toFile()
		);
		this.suffix = suffix;
	}

	public String getSuffix() {
		return suffix;
	}
}
