package nu.metacraft.season_4.entity.ai.sensors;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.NearestLivingEntitiesSensor;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerWorld;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Copy-paste of {@link net.minecraft.entity.ai.brain.sensor.BreezeAttackablesSensor} but with support for all entity types.
 * Not sure why it wasn't made generic to begin with.
 */
public class TargetEntitySensor extends NearestLivingEntitiesSensor<LivingEntity> {

	@Override
	public Set<MemoryModuleType<?>> getOutputMemoryModules() {
		return ImmutableSet.copyOf(
				Iterables.concat(super.getOutputMemoryModules(), List.of(MemoryModuleType.NEAREST_ATTACKABLE))
		);
	}

	@Override
	protected void sense(ServerWorld serverWorld, LivingEntity entity) {
		super.sense(serverWorld, entity);
		entity.getBrain().getOptionalRegisteredMemory(MemoryModuleType.MOBS).stream().flatMap(Collection::stream).filter(
				EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR
		).filter(
				(target) -> Sensor.testAttackableTargetPredicate(serverWorld, entity, target)
		).findFirst().ifPresentOrElse(
				(target) -> entity.getBrain().remember(MemoryModuleType.NEAREST_ATTACKABLE, target),
				() -> entity.getBrain().forget(MemoryModuleType.NEAREST_ATTACKABLE)
		);
	}

}
