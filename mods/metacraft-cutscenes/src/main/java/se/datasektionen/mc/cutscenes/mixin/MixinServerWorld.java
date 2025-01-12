package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.cutscenes.cutscene.world.CutsceneWorld;

@Mixin(ServerWorld.class)
public class MixinServerWorld {

	@WrapOperation(
		method = "tickWeather",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/PlayerManager;sendToDimension(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/registry/RegistryKey;)V"
		)
	)
	private void redirectCutscenePackets(PlayerManager instance, Packet<?> packet, RegistryKey<World> dimension, Operation<Void> original) {
		if ((Object) this instanceof CutsceneWorld cw) {
			cw.getCutscene().sendToPlayers(packet);
		} else {
			original.call(instance, packet, dimension);
		}
	}

	@WrapOperation(
			method = "tickWeather",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/packet/Packet;)V"
			)
	)
	private void redirectCutscenePackets(PlayerManager instance, Packet<?> packet, Operation<Void> original) {
		if ((Object) this instanceof CutsceneWorld cw) {
			cw.getCutscene().sendToPlayers(packet);
		} else {
			original.call(instance, packet);
		}
	}

	@ModifyExpressionValue(
			method = "tickPassenger",//Lambda inside tick.
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/EntityList;has(Lnet/minecraft/entity/Entity;)Z"
			)
	)
	public boolean skipDespawnInCutscenes(boolean original) {
		if ((Object) this instanceof CutsceneWorld) {
			return true;
		}
		return original;
	}

}
