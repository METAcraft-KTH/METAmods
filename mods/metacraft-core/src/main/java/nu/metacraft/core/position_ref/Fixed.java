package nu.metacraft.core.position_ref;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public class Fixed implements PositionRef {

	public static final MapCodec<Fixed> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Vec3.CODEC.fieldOf("pos").forGetter(ref -> ref.pos)
			).apply(instance, Fixed::new)
	);

	private final Vec3 pos;

	public Fixed(Vec3 pos) {
		this.pos = pos;
	}

	@Override
	public Optional<Vec3> get(RefContext ctx) {
		return Optional.of(pos);
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.FIXED;
	}
}
