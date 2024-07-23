package se.datasektionen.mc.metacraft_core;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.commands.SetPistonMovable;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;

import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("reset-music").executes(ctx -> {
						((ServerPlayerEntityExtensions) ctx.getSource().getPlayerOrThrow()).metacraft_lib$resetMusicTimer();
						ctx.getSource().sendFeedback(() -> Text.literal("Reset Music Timer"), false);
						return 1;
					})
			);
			SetPistonMovable.register(dispatcher, registryAccess);
		});
	}

}
