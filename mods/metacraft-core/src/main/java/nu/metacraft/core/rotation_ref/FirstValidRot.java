package nu.metacraft.core.rotation_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.registry.RotationRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec2;

public class FirstValidRot implements RotationRef {

	public static final MapCodec<FirstValidRot> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> RotationRefRegistry.CODEC).listOf().fieldOf("rotations").forGetter(ref -> ref.rotations)
			).apply(instance, FirstValidRot::new)
	);

	private final List<RotationRef> rotations;

	public FirstValidRot(List<RotationRef> rotations) {
		this.rotations = rotations;
	}

	@Override
	public Optional<Vec2> get(RefContext ctx) {
		return rotations.stream().map(
				pos -> pos.get(ctx)
		).filter(Optional::isPresent).map(Optional::get).findFirst();
	}

	@Override
	public RotationRefType<?> getType() {
		return RotationRefRegistry.FIRST_VALID;
	}
}
