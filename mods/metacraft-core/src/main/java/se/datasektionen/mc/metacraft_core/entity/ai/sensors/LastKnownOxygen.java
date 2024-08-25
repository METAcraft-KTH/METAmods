package se.datasektionen.mc.metacraft_core.entity.ai.sensors;

import com.google.common.collect.ImmutableSet;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftMemoryModules;

import java.util.Set;

public class LastKnownOxygen extends Sensor<MobEntity> {
	@Override
	protected void sense(ServerWorld world, MobEntity entity) {
		var eyePos = BlockPos.ofFloored(entity.getEyePos());
		if (!world.getBlockState(eyePos).getFluidState().isIn(FluidTags.WATER)) {
			entity.getBrain().remember(METAcraftMemoryModules.NEAREST_OXYGEN, GlobalPos.create(world.getRegistryKey(), eyePos));
		} else {
			BlockPos.Mutable pos = new BlockPos.Mutable().set(eyePos, Direction.UP);
			BlockState state = world.getBlockState(pos);
			while (state.getFluidState().isIn(FluidTags.WATER) && pos.getY() <= world.getTopY()) {
				pos.move(Direction.UP);
				state = world.getBlockState(pos);
				if (!state.getFluidState().isIn(FluidTags.WATER) && !state.isSolidSurface(world, pos, entity, Direction.DOWN)) {
					var existing = entity.getBrain().getOptionalRegisteredMemory(METAcraftMemoryModules.NEAREST_OXYGEN);
					if (
							existing.isEmpty() || existing.get().dimension() != world.getRegistryKey() ||
							existing.get().pos().getSquaredDistance(entity.getPos()) > pos.getSquaredDistance(entity.getPos())
					) {
						entity.getBrain().remember(METAcraftMemoryModules.NEAREST_OXYGEN, GlobalPos.create(world.getRegistryKey(), pos.toImmutable()));
					}
					return;
				}
			}
		}
	}

	@Override
	public Set<MemoryModuleType<?>> getOutputMemoryModules() {
		return ImmutableSet.of(METAcraftMemoryModules.NEAREST_OXYGEN);
	}
}
