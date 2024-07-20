package se.datasektionen.mc.metacraft_lib.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_lib.util.helper.CustomNameHelper;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CustomNameCommand {

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
				literal("customname").requires(Permissions.require("se.datasektionen.mc.customname", 3)).then(
						literal("set").then(
								argument("name", StringArgumentType.string()).executes(ctx -> {
									String name = fixColourCodes(StringArgumentType.getString(ctx, "name"));
									if (name.length() > 16) {
										ctx.getSource().sendError(Text.literal("Name too long, 16 characters maximum"));
										return 0;
									}
									CustomNameHelper.setCustomName(ctx.getSource().getPlayerOrThrow(), name);
									ctx.getSource().sendFeedback(() -> Text.literal("Set player name to " + name), true);
									return 1;
								})
						)
				).then(
						literal("clear").executes(ctx -> {
							CustomNameHelper.removeCustomName(ctx.getSource().getPlayerOrThrow());
							ctx.getSource().sendFeedback(() -> Text.literal("Reset player name"), true);
							return 1;
						})
				)
		);
	}

	private static String fixColourCodes(String name) {
		return name.replaceAll("(?<!([^\\\\]|^)\\\\)&", "§").replaceAll("(?<=[^\\\\]|^)\\\\(?=&)", "").replaceAll("\\\\(?=§)", "");
	}

}
