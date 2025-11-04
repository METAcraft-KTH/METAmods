package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffectInstance;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.mixin.AccessorStatusEffectInstance;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.StatusEffectTransitionConfig;

public class StatusEffectTransition implements Transition {

	public static final MapCodec<StatusEffectTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					StatusEffectTransitionConfig.CODEC.forGetter(t -> t.config)
			).apply(instance, StatusEffectTransition::new)
	);

	private final StatusEffectTransitionConfig config;
	private MobEffectInstance template;

	public StatusEffectTransition(StatusEffectTransitionConfig config) {
		this.config = config;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	private MobEffectInstance createEffect() {
		return new MobEffectInstance(config.effect(), config.duration(), config.amplifier(), true, false);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (template == null) {
			template = createEffect();
		}
		cutscene.forAllPlayers(player -> {
			if (!player.hasEffect(config.effect())) {
				player.addEffect(createEffect());
			} else {
				var existingEffect = player.getEffect(config.effect());
				if (existingEffect.getAmplifier() != config.amplifier()) {
					player.addEffect(createEffect());
				} else if (existingEffect.getDuration() != config.duration()) {
					((AccessorStatusEffectInstance) existingEffect).callSetDetailsFrom(template);
					if (player.tickCount % config.resetInterval() == 0) {
						player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), existingEffect, true));
					}
				}
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			if (player.hasEffect(config.effect())) {
				var effect = player.getEffect(config.effect());
				player.removeEffect(config.effect());
				effect = ((AccessorStatusEffectInstance) effect).getHiddenEffect();
				if (effect != null) {
					player.addEffect(effect);
				}
			}
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.STATUS_EFFECT;
	}
}
