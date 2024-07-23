package se.datasektionen.mc.metacraft_core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import se.datasektionen.mc.metacraft_core.extensions.BlockEntityExtensions;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SetPistonMovable {

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
				literal("set-piston-movable").then(
						argument("movable", BoolArgumentType.bool()).executes(ctx -> {
							boolean movable = BoolArgumentType.getBool(ctx, "movable");
							var player = ctx.getSource().getPlayerOrThrow();
							var result = player.raycast(5, 1.0f, false);
							if (result.getType() != HitResult.Type.BLOCK) {
								ctx.getSource().sendError(Text.literal("No block found"));
								return 0;
							}
							var tile = ctx.getSource().getWorld().getBlockEntity(((BlockHitResult) result).getBlockPos());
							if (tile == null) {
								ctx.getSource().sendError(Text.literal("Not a block entity"));
								return 0;
							}
							((BlockEntityExtensions) tile).metacraft_core$setMovable(movable);
							ctx.getSource().sendFeedback(() -> Text.literal("Made block " + (!movable ? "not " : "") + "movable"), false);
							return 1;
						})
				)
		);
		dispatcher.register(
				literal("set-piston-movable-default").then(
						argument("movable", BoolArgumentType.bool()).executes(ctx -> {
							boolean movable = BoolArgumentType.getBool(ctx, "movable");
							((ServerPlayerEntityExtensions) ctx.getSource().getPlayerOrThrow()).metacraft_core$setBlocksPistonMovable(movable);
							ctx.getSource().sendFeedback(() -> Text.literal("Made block entities you place " + (!movable ? "not " : "") + "movable"), false);
							return 1;
						})
				)
		);
	}

}
