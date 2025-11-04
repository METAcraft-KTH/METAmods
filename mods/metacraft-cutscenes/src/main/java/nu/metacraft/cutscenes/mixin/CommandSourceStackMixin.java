package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;
import nu.metacraft.cutscenes.util.cutscene_redirector.CutsceneServerRedirector;

@Mixin(CommandSourceStack.class)
public class CommandSourceStackMixin {

	@Shadow @Final private ServerLevel level;

	@Unique
	private MinecraftServer proxyServer;

	@ModifyReturnValue(method = "getServer", at = @At("RETURN"))
	public MinecraftServer getServer(MinecraftServer server) {
		if (level instanceof CutsceneWorld cw) {
			if (proxyServer == null) {
				proxyServer = CutsceneServerRedirector.createProxyServer(server, cw);
			}
			return proxyServer;
		}
		return server;
	}

}
