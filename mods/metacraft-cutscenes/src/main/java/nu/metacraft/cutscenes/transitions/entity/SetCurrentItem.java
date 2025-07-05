package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.lib.util.ExtraCodecs;

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
		entity.get(cutscene.getRefContext()).forEach(entity -> {
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
			entity.get(cutscene.getRefContext()).forEach(entity -> {
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
