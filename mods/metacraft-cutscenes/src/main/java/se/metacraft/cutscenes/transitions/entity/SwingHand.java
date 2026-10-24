package se.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import se.metacraft.cutscenes.cutscene.CutsceneInstance;
import se.metacraft.core.entity_ref.EntityRef;
import se.metacraft.core.registry.EntityRefRegistry;
import se.metacraft.cutscenes.registry.TransitionConfigRegistry;
import se.metacraft.cutscenes.registry.TransitionRegistry;
import se.metacraft.cutscenes.transitions.InstantTransition;
import se.metacraft.cutscenes.transitions.Transition;
import se.metacraft.cutscenes.transitions.TransitionType;
import se.metacraft.cutscenes.transitions.config.TransitionConfigType;
import se.metacraft.cutscenes.util.IntervalMap;
import se.metacraft.lib.util.METACodecs;

public class SwingHand extends InstantTransition {

	public static final MapCodec<SwingHand> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
					METACodecs.HAND_CODEC.optionalFieldOf("hand", InteractionHand.MAIN_HAND).forGetter(t -> t.hand),
					SwingAnimation.CODEC.optionalFieldOf("animation", SwingAnimation.DEFAULT).forGetter(t -> t.swingAnimation)
			).apply(instance, SwingHand::new)
	);

	private final EntityRef entity;
	private final InteractionHand hand;
	private final SwingAnimation swingAnimation;

	public SwingHand(EntityRef entity, InteractionHand hand, SwingAnimation swingAnimation) {
		this.entity = entity;
		this.hand = hand;
		this.swingAnimation = swingAnimation;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(cutscene.getRefContext()).filter(
				entity -> entity instanceof LivingEntity
		).map(entity -> (LivingEntity) entity).forEach(entity -> {
			entity.swing(hand, swingAnimation, true);
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SWING_HAND;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SWING_HAND;
	}
}
