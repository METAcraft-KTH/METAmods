package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
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
public class ServerScoreboardMixin implements ServerScoreboardExtensions {

	@Unique
	private CutsceneInstance connectedCutscene = null;

	@WrapOperation(
		method = {
			"onScoreChanged",
			"onPlayerRemoved",
			"onPlayerScoreRemoved",
			"setDisplayObjective",
			"addPlayerToTeam",
			"removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V",
			"onObjectiveChanged",
			"onTeamAdded",
			"onTeamChanged",
			"onTeamRemoved"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"
		)
	)
	public void sendOnlyToPlayersInSameCutscene(
			PlayerList playerManager, Packet<?> packet, Operation<Void> original
	) {
		getMatchingPlayers(playerManager).forEach(
				player -> player.connection.send(packet)
		);
	}

	@WrapOperation(
			method = {
					"startTrackingObjective",
					"stopTrackingObjective"
			},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"
			)
	)
	public List<ServerPlayer> syncOnlyToPlayersInSameCutscene(
			PlayerList playerManager, Operation<List<ServerPlayer>> original
	) {
		return getMatchingPlayers(playerManager).toList();
	}

	@Unique
	private Stream<ServerPlayer> getMatchingPlayers(PlayerList playerManager) {
		return playerManager.getPlayers().stream().filter(
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
