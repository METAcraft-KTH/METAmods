package se.datasektionen.mc.metacraft_core;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.commands.*;
import se.datasektionen.mc.metacraft_core.util.helper.MusicHelper;

import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("reset-music").executes(ctx -> {
						MusicHelper.resetMusicTimer(ctx.getSource().getPlayerOrThrow());
						ctx.getSource().sendFeedback(() -> Text.literal("Reset Music Timer"), false);
						return 1;
					})
			);
			LinkPortals.register(dispatcher, registryAccess);
			PlayerMobCommand.register(dispatcher, registryAccess);
			PlayMusic.register(dispatcher, registryAccess);
			ForcedRespawnCommand.register(dispatcher);
			BossbarCommand.register(dispatcher, registryAccess);
			PrefCommand.register(dispatcher, registryAccess);
		});
	}

}
