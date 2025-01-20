package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.entity_ref.EntityRef;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

public class SetCurrentItem implements Transition, TransitionConfig {

	public static final MapCodec<SetCurrentItem> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
					ExtraCodecs.HAND_CODEC.optionalFieldOf("hand", Hand.OFF_HAND).forGetter(t -> t.hand),
					Codec.BOOL.optionalFieldOf("stop_after", true).forGetter(t -> t.stopAfter)
			).apply(instance, SetCurrentItem::new)
	);

	private final EntityRef entity;
	private final Hand hand;
	private final boolean stopAfter;

	public SetCurrentItem(
			EntityRef entity,
			Hand hand, boolean stopAfter
	) {
		this.entity = entity;
		this.hand = hand;
		this.stopAfter = stopAfter;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(null, cutscene).forEach(entity -> {
			if (entity instanceof LivingEntity living) {
				living.setCurrentHand(hand);
			}
		});
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (stopAfter) {
			entity.get(null, cutscene).forEach(entity -> {
				if (entity instanceof LivingEntity living) {
					living.stopUsingItem();
				}
			});
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.ENTITY_SET_CURRENT_HAND;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.ENTITY_SET_CURRENT_HAND;
	}
}
