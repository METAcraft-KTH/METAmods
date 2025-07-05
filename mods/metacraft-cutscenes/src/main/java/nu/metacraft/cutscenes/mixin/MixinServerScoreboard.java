package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.packet.Packet;
import net.minecraft.scoreboard.*;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.Cutscene;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.extension.ServerScoreboardExtensions;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

import java.util.List;
import java.util.stream.Stream;

@Mixin(value = ServerScoreboard.class, priority = -1)
public class MixinServerScoreboard implements ServerScoreboardExtensions {

	@Unique
	private CutsceneInstance connectedCutscene = null;

	@WrapOperation(
		method = {
			"updateScore",
			"onScoreHolderRemoved",
			"onScoreRemoved",
			"setObjectiveSlot",
			"addScoreHolderToTeam",
			"removeScoreHolderFromTeam",
			"updateExistingObjective",
			"updateScoreboardTeamAndPlayers",
			"updateScoreboardTeam",
			"updateRemovedTeam"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/packet/Packet;)V"
		)
	)
	public void sendOnlyToPlayersInSameCutscene(
			PlayerManager playerManager, Packet<?> packet, Operation<Void> original
	) {
		getMatchingPlayers(playerManager).forEach(
				player -> player.networkHandler.sendPacket(packet)
		);
	}

	@WrapOperation(
			method = {
					"startSyncing",
					"stopSyncing"
			},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/PlayerManager;getPlayerList()Ljava/util/List;"
			)
	)
	public List<ServerPlayerEntity> syncOnlyToPlayersInSameCutscene(
			PlayerManager playerManager, Operation<List<ServerPlayerEntity>> original
	) {
		return getMatchingPlayers(playerManager).toList();
	}

	@Unique
	private Stream<ServerPlayerEntity> getMatchingPlayers(PlayerManager playerManager) {
		return playerManager.getPlayerList().stream().filter(
				player -> {
					var scene = CutsceneHelper.getCutscene(player).orElse(null);
					if (scene == connectedCutscene) return true;
					return scene != null && scene.getCutscene().getScoreboardMode() == Cutscene.ScoreboardMode.SYNC;
				}
		);
	}

	@Override
	public void metacraft$setConnectedCutscene(CutsceneInstance cutscene) {
		this.connectedCutscene = cutscene;
	}
}
