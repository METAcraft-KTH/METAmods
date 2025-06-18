package se.datasektionen.mc.cutscenes.rotation_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Vec2f;
import se.datasektionen.mc.cutscenes.registry.RotationRefRegistry;
import se.datasektionen.mc.cutscenes.util.RefContext;

import java.util.Optional;

public class FixedRot implements RotationRef {

	public static final MapCodec<FixedRot> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.FLOAT.fieldOf("yaw").forGetter(t -> t.yaw),
					Codec.FLOAT.fieldOf("pitch").forGetter(t -> t.pitch)
			).apply(instance, FixedRot::new)
	);

	private final float yaw;
	private final float pitch;
	private final Vec2f rot;

	public FixedRot(float yaw, float pitch) {
		this.yaw = yaw;
		this.pitch = pitch;
		this.rot = new Vec2f(pitch, yaw);
	}

	@Override
	public Optional<Vec2f> get(RefContext ctx) {
		return Optional.of(rot);
	}

	@Override
	public RotationRefType<?> getType() {
		return RotationRefRegistry.FIXED;
	}
}
