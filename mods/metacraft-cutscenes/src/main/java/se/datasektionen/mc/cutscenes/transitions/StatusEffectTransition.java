package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.mixin.AccessorStatusEffectInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.StatusEffectTransitionConfig;

public class StatusEffectTransition implements Transition {

	public static final MapCodec<StatusEffectTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					StatusEffectTransitionConfig.CODEC.forGetter(t -> t.config)
			).apply(instance, StatusEffectTransition::new)
	);

	private final StatusEffectTransitionConfig config;
	private StatusEffectInstance template;

	public StatusEffectTransition(StatusEffectTransitionConfig config) {
		this.config = config;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	private StatusEffectInstance createEffect() {
		return new StatusEffectInstance(config.effect(), config.duration(), config.amplifier(), true, false);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (template == null) {
			template = createEffect();
		}
		cutscene.forAllPlayers(player -> {
			if (!player.hasStatusEffect(config.effect())) {
				player.addStatusEffect(createEffect());
			} else {
				var existingEffect = player.getStatusEffect(config.effect());
				if (existingEffect.getAmplifier() != config.amplifier()) {
					player.addStatusEffect(createEffect());
				} else if (existingEffect.getDuration() != config.duration()) {
					((AccessorStatusEffectInstance) existingEffect).callCopyFrom(template);
					if (player.age % config.resetInterval() == 0) {
						player.networkHandler.sendPacket(new EntityStatusEffectS2CPacket(player.getId(), existingEffect, true));
					}
				}
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			if (player.hasStatusEffect(config.effect())) {
				var effect = player.getStatusEffect(config.effect());
				player.removeStatusEffect(config.effect());
				effect = ((AccessorStatusEffectInstance) effect).getHiddenEffect();
				if (effect != null) {
					player.addStatusEffect(effect);
				}
			}
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.STATUS_EFFECT;
	}
}
