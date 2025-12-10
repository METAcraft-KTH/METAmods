package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.lib.util.helper.EntityTrackerHelper;

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
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		var tracker = EntityTrackerHelper.getEntityTrackers(player.level()).get(player.getId());
		cutscene.forAllPlayers(tracker::updatePlayer);
		if (interval.getStart() != cutscene.getCurrentTime()) {
			cutscene.forAllPlayers(p -> {
				if (p != player) {
					var otherTracker = EntityTrackerHelper.getEntityTrackers(player.level()).get(player.getId());
					otherTracker.updatePlayer(player);
				}
			});
		}
	}

	@Override
	public void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		var tracker = EntityTrackerHelper.getEntityTrackers(player.level()).get(player.getId());
		cutscene.forAllPlayers(tracker::updatePlayer);
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
