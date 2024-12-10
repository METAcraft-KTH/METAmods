package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.TitleTransitionConfig;

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
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		int pos = interval.getPosInRange(cutscene.getCurrentTime());
		int remaining = interval.getRemaining(cutscene.getCurrentTime());
		int stayTicks = config.stay().orElse(Math.max(remaining - config.fadeIn() - config.fadeOut(), 0));
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(
				Math.max(config.fadeIn() - pos, 0),
				stayTicks, Math.min(config.fadeOut(), remaining)
		));
		player.networkHandler.sendPacket(new TitleS2CPacket(config.title()));
		config.subtitle().ifPresent(subtitle -> {
			player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
		});
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.stopAtEnd()) {
			player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.TITLE;
	}
}
