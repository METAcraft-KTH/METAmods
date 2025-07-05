package nu.metacraft.season_4.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import nu.metacraft.season_4.lodestone.CampusLodestoneState;

public class CampusLodestoneCommand {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(
			CommandManager.literal("campus-lodestone")
				.requires(obj -> obj.hasPermissionLevel(2))
				.then(
					CommandManager.literal("set-campus")
						.then(
							CommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.executes(ctx -> {
									ServerCommandSource source = ctx.getSource();
									BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
									ServerWorld world = source.getWorld();

									CampusLodestoneState campusState = CampusLodestoneState.getInstance(source.getServer());
									campusState.setCampusLocation(world, pos);
									source.sendMessage(Text.literal("Campus lodestone was updated."));
									return 1;
								})
						)
				)
		);
	}
}
