package nu.metacraft.player_specific_scoreboards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ResolutionContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import nu.metacraft.player_specific_scoreboards.PlayerScoreboardExtension;
import nu.metacraft.player_specific_scoreboards.util.PlayerScoreboard;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SetPlayerSidebarCommand {

	private static final DynamicCommandExceptionType ARBITRARY = new DynamicCommandExceptionType(
			v -> v::toString
	);

	private static void sendFeedback(
			CommandContext<CommandSourceStack> ctx, MutableComponent prefix, Collection<ServerPlayer> players
	) {
		if (players.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No players found"));
		} else if (players.size() == 1) {
			ctx.getSource().sendSuccess(() -> prefix.append(players.stream().findAny().get().getDisplayName()), true);
		} else {
			ctx.getSource().sendSuccess(() -> prefix.append(Component.literal(players.size() + " players")), true);
		}
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			literal("player-sidebar").requires(Permissions.require("metacraft.player_scoreboard", PermissionLevel.GAMEMASTERS)).then(
				literal("set").then(
					argument("target", EntityArgument.players()).then(
						argument("sidebar", NbtTagArgument.nbtTag()).executes(ctx -> {
							var targets = EntityArgument.getPlayers(ctx, "target");
							var scoreboard = PlayerScoreboard.CODEC.parse(
									ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE),
									NbtTagArgument.getNbtTag(ctx, "sidebar")
							).getOrThrow(ARBITRARY::create);
							for (var target : targets) {
								((PlayerScoreboardExtension) target).metacraft$setScoreboard(scoreboard.resolve(
										ResolutionContext.builder().withSource(ctx.getSource()).withEntityOverride(target).build()
								));
							}
							sendFeedback(ctx, Component.literal("Successfully set scoreboard for "), targets);
							return targets.size();
						})
					)
				)
			).then(
				literal("clear").then(
					argument("target", EntityArgument.players()).executes(ctx -> {
						var targets = EntityArgument.getPlayers(ctx, "target");
						for (var target : targets) {
							((PlayerScoreboardExtension) target).metacraft$setScoreboard(null);
						}
						sendFeedback(ctx, Component.literal("Successfully removed scoreboard from "), targets);
						return targets.size();
					})
				)
			)
		);
	}

}
