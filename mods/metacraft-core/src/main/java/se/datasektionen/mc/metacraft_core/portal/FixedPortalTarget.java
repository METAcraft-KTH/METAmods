package se.datasektionen.mc.metacraft_core.portal;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;

import java.util.Optional;

public record FixedPortalTarget(GlobalPos target) implements PortalTarget {

	public static final MapCodec<FixedPortalTarget> CODEC = GlobalPos.MAP_CODEC.xmap(
			FixedPortalTarget::new, FixedPortalTarget::target
	);

	public static FixedPortalTarget create(RegistryKey<World> dim, BlockPos pos) {
		return new FixedPortalTarget(GlobalPos.create(dim, pos));
	}

	@Override
	public PortalTarget getWithPos(BlockPos pos) {
		return create(target.dimension(), pos);
	}

	@Override
	public DataResult<GlobalPos> getTarget(PortalEntity portal, Entity entity) {
		return DataResult.success(target);
	}

	@Override
	public PortalTargetRegistry.PortalTargetType<?> getType() {
		return PortalTargetRegistry.FIXED;
	}
}
