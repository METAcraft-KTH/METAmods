package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.cutscenes.cutscene.world.CutsceneWorld;
import se.datasektionen.mc.cutscenes.util.cutscene_redirector.CutsceneServerRedirector;

@Mixin(ServerCommandSource.class)
public class MixinServerCommandSource {

	@Shadow @Final private ServerWorld world;

	@Unique
	private MinecraftServer proxyServer;

	@ModifyReturnValue(method = "getServer", at = @At("RETURN"))
	public MinecraftServer getServer(MinecraftServer server) {
		if (world instanceof CutsceneWorld cw) {
			if (proxyServer == null) {
				proxyServer = CutsceneServerRedirector.createProxyServer(server, cw);
			}
			return proxyServer;
		}
		return server;
	}

}
