package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;

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
		var tracker = EntityTrackerHelper.getEntityTrackers(player.getWorld()).get(player.getId());
		cutscene.forAllPlayers(tracker::updateTrackedStatus);
		if (interval.getStart() != cutscene.getCurrentTime()) {
			cutscene.forAllPlayers(p -> {
				if (p != player) {
					var otherTracker = EntityTrackerHelper.getEntityTrackers(player.getWorld()).get(player.getId());
					otherTracker.updateTrackedStatus(player);
				}
			});
		}
	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		var tracker = EntityTrackerHelper.getEntityTrackers(player.getWorld()).get(player.getId());
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
