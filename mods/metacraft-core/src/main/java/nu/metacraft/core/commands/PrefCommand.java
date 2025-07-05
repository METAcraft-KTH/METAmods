package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import nu.metacraft.core.preferences.PreferenceMenu;

import static net.minecraft.server.command.CommandManager.literal;

public class PrefCommand {

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
				literal(PreferenceMenu.COMMAND).executes(
						ctx -> {
							new PreferenceMenu(ctx.getSource().getPlayerOrThrow()).open();
							return 1;
						}
				)
		);
	}

}
