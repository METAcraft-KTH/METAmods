package nu.metacraft.core.entity.ai.sensors;

import com.google.common.collect.ImmutableSet;
import nu.metacraft.core.entity.ai.METAcraftMemoryModules;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.level.block.state.BlockState;

public class LastKnownOxygen extends Sensor<Mob> {
	@Override
	protected void doTick(ServerLevel world, Mob entity) {
		var eyePos = BlockPos.containing(entity.getEyePosition());
		if (!world.getBlockState(eyePos).getFluidState().is(FluidTags.WATER)) {
			entity.getBrain().setMemory(METAcraftMemoryModules.NEAREST_OXYGEN, GlobalPos.of(world.dimension(), eyePos));
		} else {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos().setWithOffset(eyePos, Direction.UP);
			BlockState state = world.getBlockState(pos);
			while (state.getFluidState().is(FluidTags.WATER) && pos.getY() <= world.getMaxY()) {
				pos.move(Direction.UP);
				state = world.getBlockState(pos);
				if (!state.getFluidState().is(FluidTags.WATER) && !state.entityCanStandOnFace(world, pos, entity, Direction.DOWN)) {
					var existing = entity.getBrain().getMemory(METAcraftMemoryModules.NEAREST_OXYGEN);
					if (
							existing.isEmpty() || existing.get().dimension() != world.dimension() ||
							existing.get().pos().distToCenterSqr(entity.position()) > pos.distToCenterSqr(entity.position())
					) {
						entity.getBrain().setMemory(METAcraftMemoryModules.NEAREST_OXYGEN, GlobalPos.of(world.dimension(), pos.immutable()));
					}
					return;
				}
			}
		}
	}

	@Override
	public Set<MemoryModuleType<?>> requires() {
		return ImmutableSet.of(METAcraftMemoryModules.NEAREST_OXYGEN);
	}
}
