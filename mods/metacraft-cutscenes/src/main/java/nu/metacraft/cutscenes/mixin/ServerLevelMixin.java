package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level {

	protected ServerLevelMixin(WritableLevelData properties, ResourceKey<Level> registryRef, RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
		super(properties, registryRef, registryManager, dimensionEntry, isClient, debugWorld, seed, maxChainedNeighborUpdates);
	}

	@WrapOperation(
		method = "advanceWeatherCycle",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V"
		)
	)
	private void redirectCutscenePackets(PlayerList instance, Packet<?> packet, ResourceKey<Level> dimension, Operation<Void> original) {
		if ((Object) this instanceof CutsceneWorld cw) {
			cw.getCutscene().sendToPlayers(packet);
		} else {
			original.call(instance, packet, dimension);
		}
	}

	@WrapOperation(
			method = "advanceWeatherCycle",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"
			)
	)
	private void redirectCutscenePackets(PlayerList instance, Packet<?> packet, Operation<Void> original) {
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
					target = "Lnet/minecraft/world/level/entity/EntityTickList;contains(Lnet/minecraft/world/entity/Entity;)Z"
			)
	)
	public boolean skipDespawnInCutscenes(boolean original) {
		if ((Object) this instanceof CutsceneWorld) {
			return true;
		}
		return original;
	}

	@WrapOperation(
		method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/server/level/ServerLevel;"
		)
	)
	public final ServerLevel sendToPlayerIfNearby(ServerPlayer player, Operation<ServerLevel> original) {
		if ((Object) this instanceof CutsceneWorld cw && cw.getCutscene().hasPlayer(player)) {
			return (ServerLevel) (Object) this;
		}
		return original.call(player);
	}

}
