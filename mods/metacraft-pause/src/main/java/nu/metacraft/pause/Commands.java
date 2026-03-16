package nu.metacraft.pause;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;

import static net.minecraft.commands.Commands.literal;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			registerModerationModeCommand(dispatcher);
		});
	}

	public static void registerModerationModeCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				literal("pause").requires(
					ctx -> Permissions.check(ctx, "metacraft.pause.can_toggle", PermissionLevel.ADMINS) || FabricLoader.getInstance().isDevelopmentEnvironment()
				).executes(ctx -> {
					var server = ctx.getSource().getServer();
					var data = PauseData.getInstance(server);
					if (data.isPaused() && !data.isResuming()) {
						data.unpause(server, 5);
					} else {
						data.pause(server);
					}

					if (data.isResuming() || !data.isPaused()) {
						ctx.getSource().sendSuccess(() -> Component.literal("Resumed the server"), true);
					} else {
						ctx.getSource().sendSuccess(() -> Component.literal("Paused the server"), true);
					}
					return 1;
				})
		);
	}

}
