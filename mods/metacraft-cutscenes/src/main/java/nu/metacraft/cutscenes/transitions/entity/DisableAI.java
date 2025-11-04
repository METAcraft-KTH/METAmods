package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.mixin.BrainAccessor;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.core.mixin.MobAccessor;

import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;

public class DisableAI implements Transition {

	public static final MapCodec<DisableAI> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					DisableAIConfig.CODEC.forGetter(a -> a.config)
			).apply(instance, DisableAI::new)
	);

	private final DisableAIConfig config;

	public DisableAI(DisableAIConfig config) {
		this.config = config;
	}

	private boolean activated = false;

	private Set<WrappedGoal> goalSelector;
	private Set<WrappedGoal> targetSelector;

	private Map<Integer, Map<Activity, Set<BehaviorControl<?>>>> tasks;
	private Map<SensorType<? extends Sensor<?>>, Sensor<?>> sensors;

	private final Set<Entity> cache = new HashSet<>();

	private void activate(Entity entity) {
		goalSelector = null;
		targetSelector = null;
		tasks = null;
		sensors = null;
		if (entity instanceof Mob mob) {
			if (!config.onlySensors()) {
				var selector = ((MobAccessor) mob).getGoalSelector();
				goalSelector = new HashSet<>(selector.getAvailableGoals());
				selector.removeAllGoals(goal -> true);

				var tasks = ((BrainAccessor) mob.getBrain()).getAvailableBehaviorsByPriority();
				this.tasks = new HashMap<>(tasks);
				tasks.clear();
			}
			var tselector = ((MobAccessor) mob).getTargetSelector();
			targetSelector = new HashSet<>(tselector.getAvailableGoals());
			tselector.removeAllGoals(goal -> true);
			activated = true;

			var sensors = ((BrainAccessor) mob.getBrain()).getSensors();
			this.sensors = new HashMap<>(sensors);
			sensors.clear();
		}
		cache.add(entity);
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entity().get(cutscene.getRefContext()).forEach(this::activate);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (!activated) {
			activate(cutscene, interval);
		}
		config.entity().get(cutscene.getRefContext()).forEach(entity -> {
			if (!cache.contains(entity)) {
				activate(entity);
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entity().get(cutscene.getRefContext()).forEach(entity -> {
			if (!cache.contains(entity)) return;
			if (entity instanceof Mob mob) {
				if (!config.onlySensors()) {
					var selector = ((MobAccessor) mob).getGoalSelector();
					goalSelector.forEach(goal -> {
						selector.addGoal(goal.getPriority(), goal.getGoal());
					});
					goalSelector.clear();

					var tasks = ((BrainAccessor) mob.getBrain()).getAvailableBehaviorsByPriority();
					tasks.putAll(this.tasks);
					this.tasks.clear();
				}

				var tSelector = ((MobAccessor) mob).getTargetSelector();
				targetSelector.forEach(goal -> {
					tSelector.addGoal(goal.getPriority(), goal.getGoal());
				});
				targetSelector.clear();

				var sensors = ((BrainAccessor) mob.getBrain()).getSensors();
				sensors.putAll(this.sensors);
				this.sensors.clear();
			}
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.DISABLE_ENTITY_AI;
	}
}
