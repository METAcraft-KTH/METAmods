package nu.metacraft.core.portal;

import com.mojang.serialization.DataResult;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import nu.metacraft.core.block.entities.PortalEntity;

import java.util.Optional;

public interface PortalTarget {

	DataResult<GlobalPos> getOrInitializeTargetForEntity(PortalEntity portal, Entity entity);

	Optional<GlobalPos> getFixedTarget(PortalEntity portal);

	PortalTarget getWithPos(BlockPos pos);

	default void initialize(PortalEntity portal) {}

	default PortalTarget getAsEmpty() {
		return EmptyPortalTarget.getInstance();
	}

	PortalTargetRegistry.PortalTargetType<?> getType();

}
