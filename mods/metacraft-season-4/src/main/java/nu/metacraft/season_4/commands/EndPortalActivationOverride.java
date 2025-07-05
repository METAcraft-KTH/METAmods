package nu.metacraft.season_4.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import nu.metacraft.season_4.end.EndCommandActivation;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EndPortalActivationOverride {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access) {
		dispatcher.register(
			literal("end-portal-activation-override").requires(Permissions.require(
				"metacraft.double-team", 2
			)).then(
				literal("set").then(
					argument("command", StringArgumentType.greedyString()).executes(ctx -> {
						String command = StringArgumentType.getString(ctx, "command");
						EndCommandActivation.getInstance(ctx.getSource().getServer()).setCommand(command);
						ctx.getSource().sendFeedback(() -> Text.literal("Set command to " + command), true);
						return 1;
					})
				)
			).then(
				literal("remove").executes(ctx -> {
					EndCommandActivation.getInstance(ctx.getSource().getServer()).removeCommand();
					ctx.getSource().sendFeedback(() -> Text.literal("Removed command"), true);
					return 1;
				})
			)
		);
	}

}
