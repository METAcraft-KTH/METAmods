package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneLevel;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level {

	protected ServerLevelMixin(WritableLevelData properties, ResourceKey<Level> registryRef, RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
		super(properties, registryRef, registryManager, dimensionEntry, isClient, debugWorld, seed, maxChainedNeighborUpdates);
	}

	@WrapOperation(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;getChunkSource()Lnet/minecraft/server/level/ServerChunkCache;"
			),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/server/level/ServerLevel;getChunkSource()Lnet/minecraft/server/level/ServerChunkCache;",
							ordinal = 0
					)
			)
	)
	public ServerChunkCache passthrough(ServerLevel instance, Operation<ServerChunkCache> original) {
		if ((Object) this instanceof CutsceneLevel) {
			return null;
		}
		return original.call(instance);
	}

	@WrapOperation(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerChunkCache;getGenerator()Lnet/minecraft/world/level/chunk/ChunkGenerator;"
			),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/server/level/ServerLevel;getChunkSource()Lnet/minecraft/server/level/ServerChunkCache;",
							ordinal = 0
					)
			)
	)
	public ChunkGenerator passthrough2(ServerChunkCache instance, Operation<ChunkGenerator> original) {
		if ((Object) this instanceof CutsceneLevel) {
			return null;
		}
		return original.call(instance);
	}

	@WrapOperation(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getBiomeSource()Lnet/minecraft/world/level/biome/BiomeSource;"
			),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/server/level/ServerLevel;getChunkSource()Lnet/minecraft/server/level/ServerChunkCache;",
							ordinal = 0
					)
			)
	)
	public BiomeSource passthrough3(ChunkGenerator instance, Operation<BiomeSource> original) {
		if ((Object) this instanceof CutsceneLevel) {
			return null;
		}
		return original.call(instance);
	}

	@WrapOperation(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerChunkCache;randomState()Lnet/minecraft/world/level/levelgen/RandomState;"
			),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/server/level/ServerLevel;getChunkSource()Lnet/minecraft/server/level/ServerChunkCache;",
							ordinal = 0
					)
			)
	)
	public RandomState passthrough4(ServerChunkCache instance, Operation<RandomState> original) {
		if ((Object) this instanceof CutsceneLevel) {
			return null;
		}
		return original.call(instance);
	}

	@WrapOperation(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/biome/BiomeSource;createUncachedResolver(Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/biome/BiomeResolver;"
			),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/server/level/ServerLevel;getChunkSource()Lnet/minecraft/server/level/ServerChunkCache;",
							ordinal = 0
					)
			)
	)
	public BiomeResolver passthrough5(BiomeSource instance, RandomState randomState, Operation<BiomeResolver> original) {
		if ((Object) this instanceof CutsceneLevel) {
			return null;
		}
		return original.call(instance, randomState);
	}

	@WrapOperation(
		method = "advanceWeatherCycle",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V"
		)
	)
	private void redirectCutscenePackets(PlayerList instance, Packet<?> packet, ResourceKey<Level> dimension, Operation<Void> original) {
		if ((Object) this instanceof CutsceneLevel cw) {
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
		if ((Object) this instanceof CutsceneLevel cw) {
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
		if ((Object) this instanceof CutsceneLevel) {
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
		if ((Object) this instanceof CutsceneLevel cw && cw.getCutscene().hasPlayer(player)) {
			return (ServerLevel) (Object) this;
		}
		return original.call(player);
	}

}
