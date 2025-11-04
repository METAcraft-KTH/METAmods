package nu.metacraft.core.portal;

import com.mojang.serialization.DataResult;
import nu.metacraft.core.block.entities.PortalEntity;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;

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
