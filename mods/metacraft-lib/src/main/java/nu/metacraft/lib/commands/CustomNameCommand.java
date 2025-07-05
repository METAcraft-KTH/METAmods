package nu.metacraft.lib.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import nu.metacraft.lib.util.helper.CustomNameHelper;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CustomNameCommand {

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
				literal("custom-name").requires(Permissions.require("metacraft.custom-name", 3)).then(
						literal("set").then(
								argument("name", StringArgumentType.string()).executes(ctx -> {
									return changeName(ctx, StringArgumentType.getString(ctx, "name"), true);
								})
						)
				).then(
						literal("set-gui-hidden").then(
								argument("name", StringArgumentType.string()).executes(ctx -> {
									return changeName(ctx, StringArgumentType.getString(ctx, "name"), false);
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

	private static int changeName(CommandContext<ServerCommandSource> ctx, String unparsedName, boolean showInGUI) throws CommandSyntaxException {
		String name = fixColourCodes(unparsedName);
		if (name.length() > 16) {
			ctx.getSource().sendError(Text.literal("Name too long, 16 characters maximum"));
			return 0;
		}
		CustomNameHelper.setCustomName(ctx.getSource().getPlayerOrThrow(), name, showInGUI);
		ctx.getSource().sendFeedback(() -> Text.literal("Set player name to " + name), true);
		return 1;
	}

	private static String fixColourCodes(String name) {
		return name.replaceAll("(?<!([^\\\\]|^)\\\\)&", "§").replaceAll("(?<=[^\\\\]|^)\\\\(?=&)", "").replaceAll("\\\\(?=§)", "");
	}

}
