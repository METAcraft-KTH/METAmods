package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.cutscenes.extension.ServerPlayerEntityExtensions;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {

	@Shadow public ServerPlayerEntity player;

	@ModifyExpressionValue(
		method = "onPlayerMove",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayerEntity;isInTeleportationState()Z",
			ordinal = 1
		)
	)
	public boolean onPlayerMove(boolean inTPState) {
		if (((ServerPlayerEntityExtensions) player).metacraft$getAllowWrongMovements()) {
			return true;
		}
		return inTPState;
	}

}
