package se.datasektionen.mc.metacraft_dungeons;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;

import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			dispatcher.register(
				literal("reset-dimension").requires(
						Permissions.require("metacraft.dungeons.reset_dimension", 4)
				).then(literal("yes-im-sure").then(literal("yes-im-really-sure").then(
						literal("just-do-it-already").executes(ctx -> {
							DungeonData.getIfPresent(ctx.getSource().getWorld()).ifPresentOrElse(data -> {
								ctx.getSource().sendFeedback(() -> Text.literal("Performing RESET!"), true);
								data.resetDimension();
							}, () -> {
								ctx.getSource().sendFeedback(() -> Text.literal("No data found for this dimension"), false);
							});
							return 1;
						})
				)))
			);
		});
	}
}
