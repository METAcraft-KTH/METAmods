package nu.metacraft.info_commands.mixin;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ReloadCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.info_commands.Helper;
import nu.metacraft.info_commands.Info;

@Mixin(ReloadCommand.class)
public class ReloadCommandMixin {

	@Inject(method = "lambda$register$0", at = @At("RETURN")) //Lambda in register
	private static void postReload(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
		if (Info.getConfig().resendCommandTreeOnReload()) {
			Helper.resendCommandTreeToAllPlayers(context.getSource().getServer().getPlayerList());
		}
	}

}
