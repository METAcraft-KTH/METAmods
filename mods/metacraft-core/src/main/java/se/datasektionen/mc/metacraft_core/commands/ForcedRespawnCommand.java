package se.datasektionen.mc.metacraft_core.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.util.METAcraftCoreData;

public class ForcedRespawnCommand {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(
			CommandManager.literal("forced-respawn")
				.requires(Permissions.require("metacraft.forced-respawn", 2))
				.executes(ctx -> {
					ServerCommandSource source = ctx.getSource();
					METAcraftCoreData data = METAcraftCoreData.getInstance(source.getServer());
					RegistryKey<World> world = data.getForcedRespawnWorld();
					BlockPos pos = data.getForcedRespawnPos();
					boolean enabled = world != null && pos != null;
					source.sendFeedback(() -> Text.literal(enabled
						? "Forced respawn is activated and set to " + world + " at " + pos
						: "Forced respawn is not enabled right now."
					), true);
					return enabled ? 1 : 0;
				})
				.then(
					CommandManager.literal("set")
						.then(
							CommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.executes(ctx -> {
									BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");

									ServerCommandSource source = ctx.getSource();
									METAcraftCoreData data = METAcraftCoreData.getInstance(source.getServer());
									data.setForcedRespawn(source.getWorld().getRegistryKey(), pos);
									source.sendFeedback(() -> Text.literal("Forced respawn position updated."), true);
									return 1;
								})
						)
				)
				.then(
					CommandManager.literal("unset")
						.executes(ctx -> {
							ServerCommandSource source = ctx.getSource();
							METAcraftCoreData data = METAcraftCoreData.getInstance(source.getServer());
							data.unsetForcedRespawn();
							source.sendFeedback(() -> Text.literal("Forced respawn deactivated."), true);
							return 1;
						})
				)
		);
	}
}
