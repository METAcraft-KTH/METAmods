package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.commons.lang3.mutable.MutableBoolean;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;

public class ConditionalTransition implements Transition {

	private final Config config;
	private final Transition transition;

	private boolean active = false;

	public static final MapCodec<ConditionalTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					Codec.lazyInitialized(() -> TransitionRegistry.CODEC).fieldOf("transition").forGetter(t -> t.transition),
					Codec.BOOL.fieldOf("active").forGetter(t -> t.active)
			).apply(instance, ConditionalTransition::new)
	);

	public ConditionalTransition(Config config) {
		this.config = config;
		this.transition = config.transition.create();
	}

	public ConditionalTransition(Config config, Transition transition, boolean active) {
		this.config = config;
		this.transition = transition;
		this.active = active;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (!config.checkEveryTick) {
			if (config.test(cutscene)) {
				transition.activate(cutscene, interval);
				active = true;
			}
		}
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (active) {
			transition.tick(cutscene, interval);
			if (config.checkEveryTick) {
				if (!config.test(cutscene)) {
					transition.deactivate(cutscene, interval);
					cutscene.forAllPlayers(player -> transition.deactivate(player, cutscene, interval));
					active = false;
				}
			}
		} else if (config.checkEveryTick) {
			if (config.test(cutscene)) {
				active = true;
				transition.activate(cutscene, interval);
				cutscene.forAllPlayers(player -> transition.activate(player, cutscene, interval));
			}
		}
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (active) {
			transition.deactivate(cutscene, interval);
		}
	}

	@Override
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (active) {
			transition.activate(player, cutscene, interval);
		}
	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (active) {
			transition.deactivate(player, cutscene, interval);
		}
	}

	@Override
	public void copyFromPreviousCutscene(CutsceneInstance prev, CutsceneInstance current, IntervalMap.Interval<Transition> interval) {
		transition.copyFromPreviousCutscene(prev, current, interval);
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.CONDITIONAL;
	}

	public record Config(String commandCondition, TransitionConfig transition, boolean checkEveryTick) implements TransitionConfig {
		public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Codec.STRING.fieldOf("condition").forGetter(Config::commandCondition),
						Codec.lazyInitialized(() -> TransitionConfigRegistry.CODEC).fieldOf("transition").forGetter(Config::transition),
						Codec.BOOL.optionalFieldOf("check_every_tick", false).forGetter(Config::checkEveryTick)
				).apply(instance, Config::new)
		);

		public boolean test(CutsceneInstance cutscene) {
			MutableBoolean isSuccessful = new MutableBoolean(false);
			var source = RunCommandTransition.getSource(cutscene, false, null, false).withReturnValueConsumer(
					(success, result) -> {
						if (result != 0) {
							isSuccessful.setTrue();
						}
					}
			);
			cutscene.getServer().getCommandManager().executeWithPrefix(source, commandCondition);
			return isSuccessful.booleanValue();
		}

		@Override
		public Transition create() {
			return new ConditionalTransition(this);
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return TransitionConfigRegistry.CONDITIONAL;
		}
	}
}
