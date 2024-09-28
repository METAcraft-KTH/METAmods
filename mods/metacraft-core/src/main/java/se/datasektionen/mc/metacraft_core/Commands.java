package se.datasektionen.mc.metacraft_core;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.commands.CampusLodestoneCommand;
import se.datasektionen.mc.metacraft_core.commands.ForcedRespawnCommand;
import se.datasektionen.mc.metacraft_core.commands.LinkPortals;
import se.datasektionen.mc.metacraft_core.commands.NoArmorDamageCommand;
import se.datasektionen.mc.metacraft_core.commands.PlayMusic;
import se.datasektionen.mc.metacraft_core.commands.PlayerMobCommand;
import se.datasektionen.mc.metacraft_core.commands.SetPistonMovable;
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
			SetPistonMovable.register(dispatcher, registryAccess);
			LinkPortals.register(dispatcher, registryAccess);
			PlayerMobCommand.register(dispatcher, registryAccess);
			PlayMusic.register(dispatcher, registryAccess);
			CampusLodestoneCommand.register(dispatcher);
			NoArmorDamageCommand.register(dispatcher);
			ForcedRespawnCommand.register(dispatcher);
		});
	}

}
