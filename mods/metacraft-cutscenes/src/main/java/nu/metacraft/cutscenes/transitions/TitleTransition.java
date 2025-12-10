package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TitleTransitionConfig;

public class TitleTransition implements Transition {

	public static final MapCodec<TitleTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TitleTransitionConfig.CODEC.forGetter(t -> t.config)
			).apply(instance, TitleTransition::new)
	);

	private final TitleTransitionConfig config;

	public TitleTransition(TitleTransitionConfig config) {
		this.config = config;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		int pos = interval.getPosInRange(cutscene.getCurrentTime());
		int remaining = interval.getRemaining(cutscene.getCurrentTime());
		int stayTicks = config.stay().orElse(Math.max(remaining - config.fadeIn() - config.fadeOut(), 0));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(
				Math.max(config.fadeIn() - pos, 0),
				stayTicks, Math.min(config.fadeOut(), remaining)
		));
		player.connection.send(new ClientboundSetTitleTextPacket(MessageTransition.parseText(player, cutscene, config.title())));
		config.subtitle().ifPresent(subtitle -> {
			player.connection.send(new ClientboundSetSubtitleTextPacket(MessageTransition.parseText(player, cutscene, subtitle)));
		});
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.stopAtEnd()) {
			player.connection.send(new ClientboundClearTitlesPacket(true));
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.TITLE;
	}
}
