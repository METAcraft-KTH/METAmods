package nu.metacraft.lib.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.PlayerDataHelper;

import java.util.function.BiConsumer;
import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PlayerDataCommand {

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
			literal("player-data").requires(PermissionPredicates.require(METAcraftLib.getID("player-data"), PermissionLevel.GAMEMASTERS)).then(
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
			CommandContext<CommandSourceStack> ctx,
			ServerPlayer player,
			Function<ServerPlayer, Boolean> get,
			BiConsumer<ServerPlayer, Boolean> set,
			boolean value, String name
	) {
		var current = get.apply(player);
		if (current == value) {
			ctx.getSource().sendFailure(Component.literal("The value of " + name + " did not change!"));
			return 0;
		}
		set.accept(player, value);
		ctx.getSource().sendSuccess(() -> Component.literal("Updated " + name + " to " + value), false);
		return 1;
	}

	private static int changeBool(
			CommandContext<CommandSourceStack> ctx,
			Function<ServerPlayer, Boolean> get,
			BiConsumer<ServerPlayer, Boolean> set,
			boolean value, String name
	) throws CommandSyntaxException {
		return changeBool(ctx, ctx.getSource().getPlayerOrException(), get, set, value, name);
	}

}
