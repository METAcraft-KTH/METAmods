package se.metacraft.player_bosses.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;
import se.metacraft.player_bosses.PlayerBosses;
import se.metacraft.player_bosses.state.PlayerBossState;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PlayerBossCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext access) {
		dispatcher.register(
			literal("boss-player").requires(PermissionPredicates.require(
				PlayerBosses.getID("player_boss"), PermissionLevel.GAMEMASTERS
			)).then(
				literal("init").then(
					argument("player", EntityArgument.player()).executes(ctx -> {
						var player = EntityArgument.getPlayer(ctx, "player");
						PlayerBossState.initBossState(ctx.getSource().getLevel(), player, player.position());
						ctx.getSource().sendSuccess(() -> Component.literal("Started bossfight with ").append(player.getName()), true);
						return 1;
					})
				)
			).then(
				literal("set-spawn-pos").then(
					argument("pos", Vec3Argument.vec3()).executes(ctx -> {
						var pos = Vec3Argument.getVec3(ctx, "pos");
						return PlayerBossState.getInstance(ctx.getSource().getLevel()).map(inst -> {
							inst.setPlayerSpawnPos(pos);
							ctx.getSource().sendSuccess(() -> Component.literal("Set spawn pos to " + pos), true);
							return 1;
						}).orElse(0);
					})
				)
			).then(
				literal("set-boss").then(
					argument("player", EntityArgument.player()).executes(ctx -> {
						var player = EntityArgument.getPlayer(ctx, "player");
						return PlayerBossState.getInstance(ctx.getSource().getLevel()).map(inst -> {
							inst.switchToBoss(player);
							ctx.getSource().sendSuccess(() -> Component.literal("Switched boss to ").append(player.getName()), true);
							return 1;
						}).orElse(0);
					})
				)
			)
		);
	}
	
}
