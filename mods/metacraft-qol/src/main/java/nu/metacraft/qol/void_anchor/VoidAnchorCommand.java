package nu.metacraft.qol.void_anchor;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import nu.metacraft.qol.void_anchor.block.VoidAnchorBlock;
import nu.metacraft.qol.void_anchor.rift.Rift;
import nu.metacraft.qol.void_anchor.rift.RiftStyle;

/**
 * {@code /voidanchor rift [crack|shatter] [pos]}: opens a rift that only looks, for testing how it
 * renders; in the configured style unless one is named. {@code /voidanchor bind <players> <pos>}:
 * binds players to the void anchor at pos in this dimension, as using it with an empty hand would.
 */
public final class VoidAnchorCommand {

	private static final int DEBUG_RIFT_TICKS = 60;

	private VoidAnchorCommand() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			var rift = Commands.literal("rift")
					.executes(ctx -> openRift(ctx.getSource(), inFrontOf(ctx.getSource().getPlayerOrException()), null))
					.then(Commands.argument("pos", Vec3Argument.vec3()).executes(
							ctx -> openRift(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), null)
					));
			for (var style : RiftStyle.values()) {
				rift.then(Commands.literal(style.getSerializedName())
						.executes(ctx -> openRift(ctx.getSource(), inFrontOf(ctx.getSource().getPlayerOrException()), style))
						.then(Commands.argument("pos", Vec3Argument.vec3()).executes(
								ctx -> openRift(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), style)
						)));
			}
			var bind = Commands.literal("bind").then(Commands.argument("players", EntityArgument.players()).then(
					Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> bind(
							ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), BlockPosArgument.getLoadedBlockPos(ctx, "pos")
					))
			));
			dispatcher.register(Commands.literal("voidanchor").requires(Permissions.require("metacraft.qol.voidanchor", 2)).then(rift).then(bind));
		});
	}

	private static int bind(CommandSourceStack source, Collection<ServerPlayer> players, BlockPos pos) {
		if (!(source.getLevel().getBlockState(pos).getBlock() instanceof VoidAnchorBlock)) {
			source.sendFailure(Component.literal("No void anchor at %d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ())));
			return 0;
		}
		var anchor = GlobalPos.of(source.getLevel().dimension(), pos);
		for (var player : players) {
			AnchorBinding.bind(player, anchor);
		}
		source.sendSuccess(() -> Component.literal(
				"Bound %d player(s) to the void anchor at %d %d %d".formatted(players.size(), pos.getX(), pos.getY(), pos.getZ())
		), true);
		return players.size();
	}

	private static Vec3 inFrontOf(ServerPlayer player) {
		var look = player.getLookAngle();
		return player.position().add(look.x * 3, -1.5, look.z * 3);
	}

	private static int openRift(CommandSourceStack source, Vec3 pos, RiftStyle style) {
		var config = VoidAnchorConfig.getInstance();
		Rift.open(source.getLevel(), pos, config.riftSize(), style != null ? style : config.riftStyle()).closeAfter(DEBUG_RIFT_TICKS);
		source.sendSuccess(() -> Component.literal(
				"Opened a rift at %.1f %.1f %.1f".formatted(pos.x, pos.y, pos.z)
		), true);
		return 1;
	}

}
