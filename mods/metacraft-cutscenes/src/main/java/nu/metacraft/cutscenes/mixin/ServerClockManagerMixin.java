package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.level.gamerules.GameRules;
import nu.metacraft.cutscenes.cutscene.world.CutsceneClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ServerClockManager.class)
public class ServerClockManagerMixin {

	@WrapWithCondition(
			method = "modifyClock",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"
			)
	)
	public boolean onlyAffectCutscenePlayers(PlayerList instance, Packet<?> packet) {
		if ((Object) this instanceof CutsceneClockManager scene) {
			for (var player : scene.getLevel().players()) {
				player.connection.send(packet);
			}
			return false;
		}
		return true;
	}

	@ModifyExpressionValue(
			method = "modifyClock",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/MinecraftServer;getAllLevels()Ljava/lang/Iterable;"
			)
	)
	public Iterable<ServerLevel> onlyAffectCutsceneLevel(
			Iterable<ServerLevel> original
	) {
		if ((Object) this instanceof CutsceneClockManager scene) {
			return List.of(scene.getLevel());
		}
		return original;
	}

	@WrapOperation(
			method = {"modifyClock", "lambda$createFullSyncPacket$0"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/clock/ServerClockManager$ClockInstance;packNetworkState(Lnet/minecraft/server/MinecraftServer;)Lnet/minecraft/world/clock/ClockNetworkState;"
			),
			require = 2
	)
	public ClockNetworkState fixPacked(
			ServerClockManager.ClockInstance instance, MinecraftServer server, Operation<ClockNetworkState> original
	) {
		if ((Object) this instanceof CutsceneClockManager scene) {
			boolean advanceTime = scene.getLevel().getGameRules().get(GameRules.ADVANCE_TIME);
			var packed = instance.packState();
			boolean paused = packed.paused() || !advanceTime;
			return new ClockNetworkState(packed.totalTicks(), packed.partialTick(), paused ? 0.0F : packed.rate());
		}
		return original.call(instance, server);
	}

}
