package nu.metacraft.core.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.block.entities.PortalEntity;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public record FixedPortalTarget(GlobalPos target, boolean autolink) implements PortalTarget {

	public static final MapCodec<FixedPortalTarget> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					GlobalPos.MAP_CODEC.forGetter(FixedPortalTarget::target),
					Codec.BOOL.optionalFieldOf("autolink", false).forGetter(FixedPortalTarget::autolink)
			).apply(instance, FixedPortalTarget::new)
	);

	public static FixedPortalTarget create(ResourceKey<Level> dim, BlockPos pos) {
		return new FixedPortalTarget(GlobalPos.of(dim, pos), false);
	}

	@Override
	public PortalTarget getWithPos(BlockPos pos) {
		return create(target.dimension(), pos);
	}

	@Override
	public void initialize(PortalEntity portal) {
		if (autolink) {
			var targetWorld = portal.getLevel().getServer().getLevel(target.dimension());
			PortalEntity.findPortal(targetWorld, target.pos()).ifPresent(
					target -> {
						target.setTarget(create(portal.getLevel().dimension(), portal.getBlockPos()));
						portal.setTarget(create(target.getLevel().dimension(), target.getBlockPos()));
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
