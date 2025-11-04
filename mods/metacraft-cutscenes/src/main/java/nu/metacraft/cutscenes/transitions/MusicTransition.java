package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.core.music.PlayerMusic;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.core.util.helper.MusicHelper;

public class MusicTransition implements Transition, TransitionConfig {

	public static final MapCodec<MusicTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PlayerMusic.EASY_MAP_CODEC.forGetter(t -> t.music)
			).apply(instance, MusicTransition::new)
	);

	private final PlayerMusic music;

	public MusicTransition(PlayerMusic music) {
		this.music = music;
	}

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
		MusicHelper.playMusic(player, music);
	}

	@Override
	public void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		MusicHelper.stopMusic(player, music);
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.MUSIC;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.MUSIC;
	}
}
