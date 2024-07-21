package se.datasektionen.mc.zones;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;

import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	public static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<ServerCommandSource> rootBuilder = literal("zone")
					.requires(Permissions.require("se.datasektionen.mc.zones.admin", 2));
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
