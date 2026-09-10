package nu.metacraft.weather.rainseason;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;

public class RainSeasonCommand {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(
				Commands.literal("rainseason")
					.requires(ctx -> ctx.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(
						Commands.literal("info")
							.executes(RainSeasonCommand::info)
					)
					.then(
						Commands.literal("activate")
							.executes(RainSeasonCommand::activate)
					)
					.then(
						Commands.literal("deactivate")
							.executes(RainSeasonCommand::deactivate)
					)
					.then(
						Commands.literal("percentage")
							.executes(RainSeasonCommand::getPercentage)
							.then(
								Commands.argument("percentage", DoubleArgumentType.doubleArg(0.01, 1.0))
									.executes(ctx -> {
										double percentage = DoubleArgumentType.getDouble(ctx, "percentage");
										return RainSeasonCommand.setPercentage(ctx, percentage);
									})
							)
					)
			)
		);
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		ServerLevel world = ctx.getSource().getLevel();
		RainSeasonState state = RainSeasonState.get(world);
		ctx.getSource().sendSuccess(() -> Component.literal(
			"Rain season enabled: " + state.isRainSeason()
		), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
			"Rain percentage: " + (state.getRainPercentage() * 100) + "%"
		), false);
		return 1;
	}

	public static int activate(CommandContext<CommandSourceStack> ctx) {
		ServerLevel world = ctx.getSource().getLevel();
		RainSeasonState state = RainSeasonState.get(world);
		state.setIsRainSeason(true);
		return 1;
	}

	public static int deactivate(CommandContext<CommandSourceStack> ctx) {
		ServerLevel world = ctx.getSource().getLevel();
		RainSeasonState state = RainSeasonState.get(world);
		state.setIsRainSeason(false);
		return 1;
	}

	private static int getPercentage(CommandContext<CommandSourceStack> ctx) {
		ServerLevel world = ctx.getSource().getLevel();
		RainSeasonState state = RainSeasonState.get(world);
		ctx.getSource().sendSuccess(() -> Component.literal(
			"The rain percentage is currently " + state.getRainPercentage()
		), false);
		return 1;
	}

	private static int setPercentage(CommandContext<CommandSourceStack> ctx, double percentage) {
		ServerLevel world = ctx.getSource().getLevel();
		RainSeasonState state = RainSeasonState.get(world);
		state.setRainPercentage(percentage);
		ctx.getSource().sendSuccess(() -> Component.literal(
			"The rain percentage was set to " + percentage
		), true);
		return 1;
	}

}
