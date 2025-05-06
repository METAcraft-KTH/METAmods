package se.datasektionen.mc.metacraft_core.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;

import java.util.Optional;

public record FixedPortalTarget(GlobalPos target, boolean autolink) implements PortalTarget {

	public static final MapCodec<FixedPortalTarget> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					GlobalPos.MAP_CODEC.forGetter(FixedPortalTarget::target),
					Codec.BOOL.optionalFieldOf("autolink", false).forGetter(FixedPortalTarget::autolink)
			).apply(instance, FixedPortalTarget::new)
	);

	public static FixedPortalTarget create(RegistryKey<World> dim, BlockPos pos) {
		return new FixedPortalTarget(GlobalPos.create(dim, pos), false);
	}

	@Override
	public PortalTarget getWithPos(BlockPos pos) {
		return create(target.dimension(), pos);
	}

	@Override
	public void initialize(PortalEntity portal) {
		if (autolink) {
			var targetWorld = portal.getWorld().getServer().getWorld(target.dimension());
			PortalEntity.findPortal(targetWorld, target.pos()).ifPresent(
					target -> {
						target.setTarget(create(portal.getWorld().getRegistryKey(), portal.getPos()));
						portal.setTarget(create(target.getWorld().getRegistryKey(), target.getPos()));
					}
			);
		}
	}

	@Override
	public DataResult<GlobalPos> getOrInitializeTargetForEntity(PortalEntity portal, Entity entity) {
		portal.initializeTarget();
		return DataResult.success(target);
	}

	@Override
	public Optional<GlobalPos> getFixedTarget(PortalEntity portal) {
		return Optional.of(target);
	}

	@Override
	public PortalTargetRegistry.PortalTargetType<?> getType() {
		return PortalTargetRegistry.FIXED;
	}
}
