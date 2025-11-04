package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.AngleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.util.METAcraftCoreData;

public class ForcedRespawnCommand {

	private static int set(CommandContext<CommandSourceStack> ctx, Vec3 pos, float angle) {
		CommandSourceStack source = ctx.getSource();
		METAcraftCoreData data = METAcraftCoreData.getInstance(source.getServer());
		data.setForcedRespawn(source.getLevel().dimension(), pos, angle);
		source.sendSuccess(() -> Component.literal("Forced respawn position updated."), true);
		return 1;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("forced-respawn")
				.requires(Permissions.require("metacraft.forced-respawn", 2))
				.executes(ctx -> {
					CommandSourceStack source = ctx.getSource();
					METAcraftCoreData data = METAcraftCoreData.getInstance(source.getServer());
					ResourceKey<Level> world = data.getForcedRespawnWorld();
					Vec3 pos = data.getForcedRespawnPos();
					boolean enabled = world != null && pos != null;
					source.sendSuccess(() -> Component.literal(enabled
						? "Forced respawn is activated and set to " + world + " at " + pos
						: "Forced respawn is not enabled right now."
					), true);
					return enabled ? 1 : 0;
				})
				.then(
					Commands.literal("set")
						.then(
							Commands.argument("pos", Vec3Argument.vec3(true))
								.executes(ctx -> set(ctx, Vec3Argument.getVec3(ctx, "pos"), 0)).then(
											Commands.argument("angle", AngleArgument.angle()).executes(
													ctx -> set(
															ctx, Vec3Argument.getVec3(ctx, "pos"),
															AngleArgument.getAngle(ctx, "angle")
													)
											)
								)
						)
				)
				.then(
					Commands.literal("unset")
						.executes(ctx -> {
							CommandSourceStack source = ctx.getSource();
							METAcraftCoreData data = METAcraftCoreData.getInstance(source.getServer());
							data.unsetForcedRespawn();
							source.sendSuccess(() -> Component.literal("Forced respawn deactivated."), true);
							return 1;
						})
				)
		);
	}
}
