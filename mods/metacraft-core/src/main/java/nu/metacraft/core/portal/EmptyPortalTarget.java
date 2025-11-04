package nu.metacraft.core.portal;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import nu.metacraft.core.block.entities.PortalEntity;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;

public final class EmptyPortalTarget implements PortalTarget {

	private static final EmptyPortalTarget INSTANCE = new EmptyPortalTarget();

	public static final MapCodec<EmptyPortalTarget> CODEC = MapCodec.unit(INSTANCE);

	public static EmptyPortalTarget getInstance() {
		return INSTANCE;
	}

	private EmptyPortalTarget() {}

	@Override
	public DataResult<GlobalPos> getOrInitializeTargetForEntity(PortalEntity portal, Entity entity) {
		return DataResult.error(() -> "Portal had no target");
	}

	@Override
	public Optional<GlobalPos> getFixedTarget(PortalEntity portal) {
		return Optional.empty();
	}

	@Override
	public PortalTarget getWithPos(BlockPos pos) {
		return this;
	}

	@Override
	public PortalTargetRegistry.PortalTargetType<?> getType() {
		return PortalTargetRegistry.EMPTY;
	}
}
