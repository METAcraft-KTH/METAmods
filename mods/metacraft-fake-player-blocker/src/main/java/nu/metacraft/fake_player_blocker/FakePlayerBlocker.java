package nu.metacraft.fake_player_blocker;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;
import nu.metacraft.lib.METAcraftLib;

import java.util.Locale;

public class FakePlayerBlocker implements ModInitializer {
	@Override
	public void onInitialize() {

	}

	public static boolean canSpawn(CommandContext<CommandSourceStack> ctx, String username) {
		if (!ctx.getSource().checkPermission(METAcraftLib.getID("fake_player.can_spawn_any"), PermissionLevel.GAMEMASTERS)) {
			if (!username.toLowerCase(Locale.ROOT).startsWith("bot_")) {
				ctx.getSource().sendFailure(Component.literal("Please prefix the bot username with \"bot_\""));
				return false;
			}
			if (RealPlayerStorage.getInstance(ctx.getSource().getServer()).isRealUsername(username)) {
				ctx.getSource().sendFailure(Component.literal(username + " is a player on the server!"));
				return false;
			}
		}
		return true;
	}
}
