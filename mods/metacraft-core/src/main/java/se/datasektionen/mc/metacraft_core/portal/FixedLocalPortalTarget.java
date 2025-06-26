package se.datasektionen.mc.metacraft_core.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;

import java.util.Optional;

public record FixedLocalPortalTarget(BlockPos target, boolean autolink) implements PortalTarget {

	public static final MapCodec<FixedLocalPortalTarget> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BlockPos.CODEC.fieldOf("pos").forGetter(FixedLocalPortalTarget::target),
					Codec.BOOL.optionalFieldOf("autolink", false).forGetter(FixedLocalPortalTarget::autolink)
			).apply(instance, FixedLocalPortalTarget::new)
	);

	public static FixedLocalPortalTarget create(BlockPos pos) {
		return new FixedLocalPortalTarget(pos, false);
	}

	@Override
	public PortalTarget getWithPos(BlockPos pos) {
		return create(pos);
	}

	@Override
	public void initialize(PortalEntity portal) {
		if (autolink) {
			var targetWorld = portal.getWorld();
			PortalEntity.findPortal(targetWorld, target).ifPresent(
					target -> {
						target.setTarget(create(portal.getPos()));
						portal.setTarget(create(target.getPos()));
					}
			);
		}
	}

	@Override
	public DataResult<GlobalPos> getOrInitializeTargetForEntity(PortalEntity portal, Entity entity) {
		portal.initializeTarget();
		return DataResult.success(GlobalPos.create(portal.getWorld().getRegistryKey(), target));
	}

	@Override
	public Optional<GlobalPos> getFixedTarget(PortalEntity portal) {
		return Optional.of(GlobalPos.create(portal.getWorld().getRegistryKey(), target));
	}

	@Override
	public PortalTargetRegistry.PortalTargetType<?> getType() {
		return PortalTargetRegistry.FIXED_LOCAL;
	}
}
