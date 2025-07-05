package nu.metacraft.lib.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nu.metacraft.lib.util.helper.PlayerDataHelper;

import java.util.function.BiConsumer;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PlayerDataCommand {

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
			literal("player-data").requires(Permissions.require("metacraft.player-data", 2)).then(
				literal("set-announce-advancements").then(
					argument("state", BoolArgumentType.bool()).executes(
						ctx -> changeBool(
								ctx, PlayerDataHelper::getAnnounceAdvancements,
								PlayerDataHelper::setAnnounceAdvancements,
								BoolArgumentType.getBool(ctx, "state"),
								"announce-advancements"
						)
					)
				)
			).then(
				literal("set-announce-death").then(
					argument("state", BoolArgumentType.bool()).executes(
						ctx -> changeBool(
								ctx, PlayerDataHelper::getAnnounceDeath,
								PlayerDataHelper::setAnnounceDeath, BoolArgumentType.getBool(ctx, "state"),
								"announce-death"
						)
					)
				)
			).then(
				literal("set-announce-join-leave").then(
					argument("state", BoolArgumentType.bool()).executes(
						ctx -> changeBool(
								ctx, PlayerDataHelper::getAnnounceJoinLeave,
								PlayerDataHelper::setAnnounceJoinLeave,
								BoolArgumentType.getBool(ctx, "state"),
								"announce-join-leave"
						)
					)
				)
			)
		);
	}

	private static int changeBool(
			CommandContext<ServerCommandSource> ctx,
			ServerPlayerEntity player,
			Function<ServerPlayerEntity, Boolean> get,
			BiConsumer<ServerPlayerEntity, Boolean> set,
			boolean value, String name
	) {
		var current = get.apply(player);
		if (current == value) {
			ctx.getSource().sendError(Text.literal("The value of " + name + " did not change!"));
			return 0;
		}
		set.accept(player, value);
		ctx.getSource().sendFeedback(() -> Text.literal("Updated " + name + " to " + value), false);
		return 1;
	}

	private static int changeBool(
			CommandContext<ServerCommandSource> ctx,
			Function<ServerPlayerEntity, Boolean> get,
			BiConsumer<ServerPlayerEntity, Boolean> set,
			boolean value, String name
	) throws CommandSyntaxException {
		return changeBool(ctx, ctx.getSource().getPlayerOrThrow(), get, set, value, name);
	}

}
