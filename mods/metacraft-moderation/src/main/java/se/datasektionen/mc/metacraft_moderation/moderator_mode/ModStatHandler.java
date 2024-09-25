package se.datasektionen.mc.metacraft_moderation.moderator_mode;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.util.PathUtil;
import net.minecraft.util.WorldSavePath;

public class ModStatHandler extends ServerStatHandler {
	public ModStatHandler(MinecraftServer server, ServerPlayerEntity owner, ModeratorModeDefinition def) {
		super(
				server,
				owner.server.getSavePath(WorldSavePath.STATS).resolve(
						owner.getUuid() + "-" + PathUtil.replaceInvalidChars(def.getName()) + ".json"
				).toFile()
		);
	}
}
