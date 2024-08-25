package se.datasektionen.mc.metacraft_core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PlayerMobCommand {

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
				literal("player-mob").requires(Permissions.require("metacraft.player-mob", 2)).then(
						literal("copy-skin-to").then(
								argument("player-mobs", EntityArgumentType.entities()).executes(
										ctx -> copySkinTo(
												ctx, EntityArgumentType.getEntities(ctx, "player-mobs"),
												ctx.getSource().getPlayerOrThrow()
										)
								).then(
										argument("source", EntityArgumentType.player()).executes(
												ctx -> copySkinTo(
														ctx, EntityArgumentType.getEntities(ctx, "player-mobs"),
														EntityArgumentType.getPlayer(ctx, "source")
												)
										)
								)
						)
				)
		);
	}

	private static int copySkinTo(CommandContext<ServerCommandSource> ctx, Collection<? extends Entity> cutscenePlayers, ServerPlayerEntity source) {
		int count = 0;
		for (var entity : cutscenePlayers) {
			if (entity instanceof se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerMob player) {
				player.copySkinFromPlayer(source);
				count++;
			}
		}
		if (count == 0) {
			ctx.getSource().sendError(Text.literal("No entity changed."));
		} else if (count == 1) {
			ctx.getSource().sendFeedback(() -> Text.literal("Changed skin of ").append(cutscenePlayers.stream().filter(
					e -> e instanceof se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerMob
			).findAny().get().getName()), true);
		} else {
			int c = count;
			ctx.getSource().sendFeedback(() -> Text.literal("Changed skin of " + c + " players"), true);
		}
		return count;
	}

}
