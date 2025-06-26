package se.datasektionen.mc.metacraft_core.portal;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;

import java.util.Optional;

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
