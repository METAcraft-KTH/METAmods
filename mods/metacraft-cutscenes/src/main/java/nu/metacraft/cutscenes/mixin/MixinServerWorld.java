package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;

@Mixin(ServerWorld.class)
public abstract class MixinServerWorld extends World {

	protected MixinServerWorld(MutableWorldProperties properties, RegistryKey<World> registryRef, DynamicRegistryManager registryManager, RegistryEntry<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
		super(properties, registryRef, registryManager, dimensionEntry, isClient, debugWorld, seed, maxChainedNeighborUpdates);
	}

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

	@WrapOperation(
		method = "sendToPlayerIfNearby",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/network/ServerPlayerEntity;getWorld()Lnet/minecraft/server/world/ServerWorld;"
		)
	)
	public final ServerWorld sendToPlayerIfNearby(ServerPlayerEntity player, Operation<ServerWorld> original) {
		if ((Object) this instanceof CutsceneWorld cw && cw.getCutscene().hasPlayer(player)) {
			return (ServerWorld) (Object) this;
		}
		return original.call(player);
	}

}
