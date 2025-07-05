package nu.metacraft.info_commands.mixin;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ReloadCommand;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.info_commands.Helper;
import nu.metacraft.info_commands.Info;

@Mixin(ReloadCommand.class)
public class MixinReloadCommand {

	@Inject(method = "method_13530", at = @At("RETURN")) //Lambda in register
	private static void method_13530Post(CommandContext<ServerCommandSource> context, CallbackInfoReturnable<Integer> cir) {
		if (Info.getConfig().resendCommandTreeOnReload()) {
			Helper.resendCommandTreeToAllPlayers(context.getSource().getServer().getPlayerManager());
		}
	}

}
