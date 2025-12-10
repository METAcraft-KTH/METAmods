package nu.metacraft.dungeons;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import nu.metacraft.dungeons.dungeons.DungeonData;

import static net.minecraft.commands.Commands.literal;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			dispatcher.register(
				literal("reset-dimension").requires(
						Permissions.require("metacraft.dungeons.reset_dimension", 4)
				).then(literal("yes-im-sure").then(literal("yes-im-really-sure").then(
						literal("just-do-it-already").executes(ctx -> {
							DungeonData.getIfPresent(ctx.getSource().getLevel()).ifPresentOrElse(data -> {
								ctx.getSource().sendSuccess(() -> Component.literal("Performing RESET!"), true);
								data.resetDimension();
							}, () -> {
								ctx.getSource().sendSuccess(() -> Component.literal("No data found for this dimension"), false);
							});
							return 1;
						})
				)))
			);
		});
	}
}
