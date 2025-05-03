package se.datasektionen.mc.metacraft_core.portal;

import com.mojang.serialization.DataResult;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;

public interface PortalTarget {

	DataResult<GlobalPos> getTarget(PortalEntity portal, Entity entity);

	PortalTarget getWithPos(BlockPos pos);

	default void initialize(PortalEntity portal) {}

	default PortalTarget getAsEmpty() {
		return EmptyPortalTarget.getInstance();
	}

	PortalTargetRegistry.PortalTargetType<?> getType();

}
