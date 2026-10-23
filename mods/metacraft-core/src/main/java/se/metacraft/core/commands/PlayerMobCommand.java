package se.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import se.metacraft.core.METAcraftCore;
import se.metacraft.core.entity.entities.player_mob.PlayerMob;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PlayerMobCommand {

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
				literal("player-mob").requires(PermissionPredicates.require(METAcraftCore.getID("player-mob"), PermissionLevel.GAMEMASTERS)).then(
						literal("copy-skin-to").then(
								argument("player-mobs", EntityArgument.entities()).executes(
										ctx -> copySkinTo(
												ctx, EntityArgument.getEntities(ctx, "player-mobs"),
												ctx.getSource().getPlayerOrException()
										)
								).then(
										argument("source", EntityArgument.player()).executes(
												ctx -> copySkinTo(
														ctx, EntityArgument.getEntities(ctx, "player-mobs"),
														EntityArgument.getPlayer(ctx, "source")
												)
										)
								)
						)
				)
		);
	}

	private static int copySkinTo(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> cutscenePlayers, ServerPlayer source) {
		int count = 0;
		for (var entity : cutscenePlayers) {
			if (entity instanceof PlayerMob player) {
				player.copySkinFromPlayer(source);
				count++;
			}
		}
		if (count == 0) {
			ctx.getSource().sendFailure(Component.literal("No entity changed."));
		} else if (count == 1) {
			ctx.getSource().sendSuccess(() -> Component.literal("Changed skin of ").append(cutscenePlayers.stream().filter(
					e -> e instanceof PlayerMob
			).findAny().get().getName()), true);
		} else {
			int c = count;
			ctx.getSource().sendSuccess(() -> Component.literal("Changed skin of " + c + " players"), true);
		}
		return count;
	}

}
