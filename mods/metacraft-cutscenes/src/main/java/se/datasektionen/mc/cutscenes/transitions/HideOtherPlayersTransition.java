package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HideOtherPlayersTransition implements Transition, TransitionConfig {

	public static final MapCodec<HideOtherPlayersTransition> CODEC = MapCodec.unit(HideOtherPlayersTransition::getInstance);

	private static final HideOtherPlayersTransition INSTANCE = new HideOtherPlayersTransition();

	public static HideOtherPlayersTransition getInstance() {
		return INSTANCE;
	}

	private HideOtherPlayersTransition() {}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		IntList ids = new IntArrayList();
		List<UUID> playerIDs = new ArrayList<>();
		cutscene.forAllPlayers(p -> {
			if (p != player) {
				playerIDs.add(p.getUuid());
				ids.add(p.getId());
			}
		});
		//Remove other players from client.
		player.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(ids));
		//Make sure other players are not re-added after teleports.
		player.networkHandler.sendPacket(new PlayerRemoveS2CPacket(playerIDs));
		if (interval.getStart() != cutscene.getCurrentTime()) {
			cutscene.forAllPlayers(p -> {
				if (p != player) {
					p.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(player.getUuid())));
					p.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(player.getId()));
				}
			});
		}
	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		//Re-add other players to player list.
		List<ServerPlayerEntity> players = new ArrayList<>();
		cutscene.forAllPlayers(p -> {
			if (p != player) {
				players.add(p);
			}
		});
		player.networkHandler.sendPacket(PlayerListS2CPacket.entryFromPlayer(players));

		//Re-add player entities to clients.
		var tracker = EntityTrackerHelper.getEntityTrackers(player.getServerWorld()).get(player.getId());
		cutscene.forAllPlayers(tracker::updateTrackedStatus);
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.HIDE_OTHER_PLAYERS;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.HIDE_OTHER_PLAYERS;
	}
}
