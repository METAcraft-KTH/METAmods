package se.metacraft.fake_players;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;
import se.metacraft.lib.METAcraftLib;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class FakePlayersCommand {
	private FakePlayersCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				literal("fakeplayers").requires(
						src -> src.checkPermission(METAcraftLib.getID("fake_players.can_control"), PermissionLevel.ADMINS)
								|| FabricLoader.getInstance().isDevelopmentEnvironment()
				).then(
						literal("start")
								.executes(ctx -> start(ctx, 0, 0))
								.then(
										argument("offsetX", IntegerArgumentType.integer())
												.then(
														argument("offsetZ", IntegerArgumentType.integer())
																.executes(ctx -> start(
																		ctx, IntegerArgumentType.getInteger(ctx, "offsetX"),
																		IntegerArgumentType.getInteger(ctx, "offsetZ")
																))
												)
								)
				).then(
						literal("stop").executes(ctx -> {
							Playback.stop(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal("Stopped the fake player replay."), true);
							return 1;
						})
				).then(
						literal("status").executes(ctx -> {
							ctx.getSource().sendSuccess(() -> Component.literal(Playback.status()), false);
							return 1;
						})
				)
		);
	}

	private static int start(CommandContext<CommandSourceStack> ctx, int offsetX, int offsetZ) {
		if (!Playback.hasCarpet(ctx.getSource().getServer())) {
			ctx.getSource().sendFailure(Component.literal("Carpet is required (it provides the /player command)."));
			return 0;
		}
		if (Playback.isRunning()) {
			ctx.getSource().sendFailure(Component.literal("A replay is already running. Use /fakeplayers stop first."));
			return 0;
		}
		Playback.start(ctx.getSource().getServer(), offsetX, offsetZ);
		ctx.getSource().sendSuccess(
				() -> Component.literal("Started the fake player replay. It loops until /fakeplayers stop."), true
		);
		return 1;
	}
}
