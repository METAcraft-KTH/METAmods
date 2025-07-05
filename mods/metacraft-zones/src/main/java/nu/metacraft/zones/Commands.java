package nu.metacraft.zones;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import nu.metacraft.zones.util.ZoneCommandUtils;

public class Commands {

	public static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<ServerCommandSource> rootBuilder = ZoneCommandUtils.zoneCommandRoot();
			ZoneManagementCommand.registerCommand(rootBuilder, registryAccess, dispatcher);
			dispatcher.register(rootBuilder);
		});
	}

	public static String getIDAsString(Identifier id) {
		if (id.getNamespace().equals("minecraft")) {
			return id.getPath();
		} else {
			return id.toString();
		}
	}
}
