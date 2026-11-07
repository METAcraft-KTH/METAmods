package se.metacraft.fake_players;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;
import se.metacraft.lib.METAcraftLib;

import java.io.IOException;

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
						literal("start").then(
								argument("scenario", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Scenarios.names(), builder))
										.executes(ctx -> start(ctx, 0, 0))
										.then(
												argument("offsetX", IntegerArgumentType.integer()).then(
														argument("offsetZ", IntegerArgumentType.integer())
																.executes(ctx -> start(
																		ctx, IntegerArgumentType.getInteger(ctx, "offsetX"),
																		IntegerArgumentType.getInteger(ctx, "offsetZ")
																))
												)
										)
						)
				).then(
						literal("stop")
								.executes(ctx -> {
									int count = Playback.stopAll(ctx.getSource().getServer());
									ctx.getSource().sendSuccess(
											() -> Component.literal("Stopped " + count + " fake player replay(s)."), true
									);
									return count;
								})
								.then(
										argument("run", IntegerArgumentType.integer(1)).executes(ctx -> {
											int run = IntegerArgumentType.getInteger(ctx, "run");
											if (!Playback.stop(ctx.getSource().getServer(), run)) {
												ctx.getSource().sendFailure(Component.literal("No replay #" + run + " is running."));
												return 0;
											}
											ctx.getSource().sendSuccess(() -> Component.literal("Stopped replay #" + run + "."), true);
											return 1;
										})
								)
				).then(
						literal("status").executes(ctx -> {
							var lines = Playback.status();
							ctx.getSource().sendSuccess(
									() -> Component.literal(lines.isEmpty() ? "No replay is running." : String.join("\n", lines)),
									false
							);
							return lines.size();
						})
				).then(
						literal("list").executes(ctx -> {
							var names = Scenarios.names();
							var text = new StringBuilder("Scenarios (put your own .csv files in ")
									.append(Scenarios.folder()).append("):");
							for (var name : names) {
								text.append("\n- ").append(name).append(Scenarios.isCustom(name) ? " (custom)" : "");
							}
							ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
							return names.size();
						})
				)
		);
	}

	private static int start(CommandContext<CommandSourceStack> ctx, int offsetX, int offsetZ) {
		var server = ctx.getSource().getServer();
		if (!Playback.hasCarpet(server)) {
			ctx.getSource().sendFailure(Component.literal("Carpet is required (it provides the /player command)."));
			return 0;
		}
		Scenario scenario;
		try {
			scenario = Scenarios.load(StringArgumentType.getString(ctx, "scenario"));
		} catch (IOException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
		int run;
		try {
			run = Playback.start(server, scenario, offsetX, offsetZ);
		} catch (IllegalStateException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
		ctx.getSource().sendSuccess(
				() -> Component.literal(
						"Started replay #" + run + " of '" + scenario.name + "' (" + scenario.sessions.size()
								+ " fake players, " + scenario.length + "s). It loops until /fakeplayers stop " + run + "."
				), true
		);
		return run;
	}
}
