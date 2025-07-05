package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.argument.AngleArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import nu.metacraft.core.util.METAcraftCoreData;

public class ForcedRespawnCommand {

	private static int set(CommandContext<ServerCommandSource> ctx, Vec3d pos, float angle) {
		ServerCommandSource source = ctx.getSource();
		METAcraftCoreData data = METAcraftCoreData.getInstance(source.getServer());
		data.setForcedRespawn(source.getWorld().getRegistryKey(), pos, angle);
		source.sendFeedback(() -> Text.literal("Forced respawn position updated."), true);
		return 1;
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(
			CommandManager.literal("forced-respawn")
				.requires(Permissions.require("metacraft.forced-respawn", 2))
				.executes(ctx -> {
					ServerCommandSource source = ctx.getSource();
					METAcraftCoreData data = METAcraftCoreData.getInstance(source.getServer());
					RegistryKey<World> world = data.getForcedRespawnWorld();
					Vec3d pos = data.getForcedRespawnPos();
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
							CommandManager.argument("pos", Vec3ArgumentType.vec3(true))
								.executes(ctx -> set(ctx, Vec3ArgumentType.getVec3(ctx, "pos"), 0)).then(
											CommandManager.argument("angle", AngleArgumentType.angle()).executes(
													ctx -> set(
															ctx, Vec3ArgumentType.getVec3(ctx, "pos"),
															AngleArgumentType.getAngle(ctx, "angle")
													)
											)
								)
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
