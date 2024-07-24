package se.datasektionen.mc.metacraft_weather.rainseason;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public class RainSeasonCommand {

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                CommandManager.literal("rainseason")
                    .requires(ctx -> ctx.hasPermissionLevel(2))
                    .then(
                        CommandManager.literal("info")
                            .executes(RainSeasonCommand::info)
                    )
                    .then(
                        CommandManager.literal("activate")
                            .executes(RainSeasonCommand::activate)
                    )
                    .then(
                        CommandManager.literal("deactivate")
                            .executes(RainSeasonCommand::deactivate)
                    )
                    .then(
                        CommandManager.literal("percentage")
                            .executes(RainSeasonCommand::getPercentage)
                            .then(
                                CommandManager.argument("percentage", DoubleArgumentType.doubleArg(0.01, 1.0))
                                    .executes(ctx -> {
                                        double percentage = DoubleArgumentType.getDouble(ctx, "percentage");
                                        return RainSeasonCommand.setPercentage(ctx, percentage);
                                    })
                            )
                    )
            )
        );
    }

    private static int info(CommandContext<ServerCommandSource> ctx) {
        ServerWorld world = ctx.getSource().getWorld();
        RainSeasonState state = RainSeasonState.get(world);
        ctx.getSource().sendFeedback(() -> Text.literal(
            "Rain season enabled: " + state.isRainSeason()
        ), false);
        ctx.getSource().sendFeedback(() -> Text.literal(
            "Rain percentage: " + (state.getRainPercentage() * 100) + "%"
        ), false);
        return 1;
    }

    public static int activate(CommandContext<ServerCommandSource> ctx) {
        ServerWorld world = ctx.getSource().getWorld();
        RainSeasonState state = RainSeasonState.get(world);
        state.setIsRainSeason(true);
        return 1;
    }

    public static int deactivate(CommandContext<ServerCommandSource> ctx) {
        ServerWorld world = ctx.getSource().getWorld();
        RainSeasonState state = RainSeasonState.get(world);
        state.setIsRainSeason(false);
        return 1;
    }

    private static int getPercentage(CommandContext<ServerCommandSource> ctx) {
        ServerWorld world = ctx.getSource().getWorld();
        RainSeasonState state = RainSeasonState.get(world);
        ctx.getSource().sendFeedback(() -> Text.literal(
            "The rain percentage is currently " + state.getRainPercentage()
        ), false);
        return 1;
    }

    private static int setPercentage(CommandContext<ServerCommandSource> ctx, double percentage) {
        ServerWorld world = ctx.getSource().getWorld();
        RainSeasonState state = RainSeasonState.get(world);
        state.setRainPercentage(percentage);
        ctx.getSource().sendFeedback(() -> Text.literal(
            "The rain percentage was set to " + percentage
        ), true);
        return 1;
    }

}
