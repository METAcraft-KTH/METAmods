package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerManager.class)
public interface AccessorPlayerManager {

	@Invoker
	void callSendScoreboard(ServerScoreboard scoreboard, ServerPlayerEntity player);

}
