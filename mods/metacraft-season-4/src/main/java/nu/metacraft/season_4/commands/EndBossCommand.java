package nu.metacraft.season_4.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import nu.metacraft.season_4.end.EndBossPlayerState;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EndBossCommand {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access) {
		dispatcher.register(
			literal("end-boss-player").requires(Permissions.require(
					"metacraft.end-boss-player", 2
			)).then(
				literal("init").then(
					argument("player", EntityArgumentType.player()).executes(ctx -> {
						var player = EntityArgumentType.getPlayer(ctx, "player");
						EndBossPlayerState.initBossState(ctx.getSource().getWorld(), player, player.getEntityPos());
						ctx.getSource().sendFeedback(() -> Text.literal("Started bossfight with ").append(player.getName()), true);
						return 1;
					})
				)
			).then(
				literal("set-spawn-pos").then(
						argument("pos", Vec3ArgumentType.vec3()).executes(ctx -> {
							var pos = Vec3ArgumentType.getVec3(ctx, "pos");
							return EndBossPlayerState.getInstance(ctx.getSource().getWorld()).map(inst -> {
								inst.setPlayerSpawnPos(pos);
								ctx.getSource().sendFeedback(() -> Text.literal("Set spawn pos to " + pos), true);
								return 1;
							}).orElse(0);
						})
				)
			).then(
				literal("set-boss").then(
					argument("player", EntityArgumentType.player()).executes(ctx -> {
						var player = EntityArgumentType.getPlayer(ctx, "player");
						return EndBossPlayerState.getInstance(ctx.getSource().getWorld()).map(inst -> {
							inst.switchToBoss(player);
							ctx.getSource().sendFeedback(() -> Text.literal("Switched boss to ").append(player.getName()), true);
							return 1;
						}).orElse(0);
					})
				)
			)
		);
	}

}
