package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.mob.MobEntity;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.mixin.AccessorBrain;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.metacraft_core.mixin.AccessorMobEntity;

import java.util.*;

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

	private Set<PrioritizedGoal> goalSelector;
	private Set<PrioritizedGoal> targetSelector;

	private Map<Integer, Map<Activity, Set<Task<?>>>> tasks;
	private Map<SensorType<? extends Sensor<?>>, Sensor<?>> sensors;

	private final Set<Entity> cache = new HashSet<>();

	private void activate(Entity entity) {
		goalSelector = null;
		targetSelector = null;
		tasks = null;
		sensors = null;
		if (entity instanceof MobEntity mob) {
			if (!config.onlySensors()) {
				var selector = ((AccessorMobEntity) mob).getGoalSelector();
				goalSelector = new HashSet<>(selector.getGoals());
				selector.clear(goal -> true);

				var tasks = ((AccessorBrain) mob.getBrain()).getTasks();
				this.tasks = new HashMap<>(tasks);
				tasks.clear();
			}
			var tselector = ((AccessorMobEntity) mob).getTargetSelector();
			targetSelector = new HashSet<>(tselector.getGoals());
			tselector.clear(goal -> true);
			activated = true;

			var sensors = ((AccessorBrain) mob.getBrain()).getSensors();
			this.sensors = new HashMap<>(sensors);
			sensors.clear();
		}
		cache.add(entity);
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entity().get(null, cutscene).forEach(this::activate);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (!activated) {
			activate(cutscene, interval);
		}
		config.entity().get(null, cutscene).forEach(entity -> {
			if (!cache.contains(entity)) {
				activate(entity);
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entity().get(null, cutscene).forEach(entity -> {
			if (!cache.contains(entity)) return;
			if (entity instanceof MobEntity mob) {
				if (!config.onlySensors()) {
					var selector = ((AccessorMobEntity) mob).getGoalSelector();
					goalSelector.forEach(goal -> {
						selector.add(goal.getPriority(), goal.getGoal());
					});
					goalSelector.clear();

					var tasks = ((AccessorBrain) mob.getBrain()).getTasks();
					tasks.putAll(this.tasks);
					this.tasks.clear();
				}

				var tSelector = ((AccessorMobEntity) mob).getTargetSelector();
				targetSelector.forEach(goal -> {
					tSelector.add(goal.getPriority(), goal.getGoal());
				});
				targetSelector.clear();

				var sensors = ((AccessorBrain) mob.getBrain()).getSensors();
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
