package se.metacraft.pirkko;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;

/** Commands for handing out Pirkko heads. */
public final class PirkkoCommands {
	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pirkko")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("give")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("variant", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(PirkkoHeads.VARIANTS, builder))
										.executes(ctx -> give(ctx.getSource(),
												EntityArgument.getPlayers(ctx, "targets"),
												StringArgumentType.getString(ctx, "variant")))))));
	}

	private static int give(CommandSourceStack source, Collection<ServerPlayer> targets, String variant) throws CommandSyntaxException {
		if (!PirkkoHeads.VARIANTS.contains(variant)) {
			throw new SimpleCommandExceptionType(Component.literal("Unknown variant '" + variant + "'; one of " + PirkkoHeads.VARIANTS)).create();
		}
		for (var player : targets) {
			var stack = PirkkoHeads.stack(variant);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
		source.sendSuccess(() -> Component.literal("Gave " + variant + " to " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private PirkkoCommands() {}
}
