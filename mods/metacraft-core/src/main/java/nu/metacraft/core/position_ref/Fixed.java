package nu.metacraft.core.position_ref;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Vec3d;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;

public class Fixed implements PositionRef {

	public static final MapCodec<Fixed> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Vec3d.CODEC.fieldOf("pos").forGetter(ref -> ref.pos)
			).apply(instance, Fixed::new)
	);

	private final Vec3d pos;

	public Fixed(Vec3d pos) {
		this.pos = pos;
	}

	@Override
	public Optional<Vec3d> get(RefContext ctx) {
		return Optional.of(pos);
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.FIXED;
	}
}
