package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import nu.metacraft.core.preferences.PreferenceMenu;

import static net.minecraft.commands.Commands.literal;

public class PrefCommand {

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
				literal(PreferenceMenu.COMMAND).executes(
						ctx -> {
							new PreferenceMenu(ctx.getSource().getPlayerOrException()).open();
							return 1;
						}
				)
		);
	}

}
