package nu.metacraft.core.rotation_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.registry.RotationRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;
import net.minecraft.world.phys.Vec2;

public class FixedRot implements RotationRef {

	public static final MapCodec<FixedRot> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.FLOAT.fieldOf("yaw").forGetter(t -> t.yaw),
					Codec.FLOAT.fieldOf("pitch").forGetter(t -> t.pitch)
			).apply(instance, FixedRot::new)
	);

	private final float yaw;
	private final float pitch;
	private final Vec2 rot;

	public FixedRot(float yaw, float pitch) {
		this.yaw = yaw;
		this.pitch = pitch;
		this.rot = new Vec2(pitch, yaw);
	}

	@Override
	public Optional<Vec2> get(RefContext ctx) {
		return Optional.of(rot);
	}

	@Override
	public RotationRefType<?> getType() {
		return RotationRefRegistry.FIXED;
	}
}
