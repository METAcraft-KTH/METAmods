package se.metacraft.core.rotation_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec2;
import se.metacraft.core.registry.RotationRefRegistry;
import se.metacraft.core.util.RefContext;

import java.util.Optional;

public record OffsetRot(
	RotationRef source, Vec2 rotOffset
) implements RotationRef {

	public static final MapCodec<OffsetRot> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			RotationRefRegistry.CODEC.fieldOf("source").forGetter(OffsetRot::source),
			Codec.floatRange(-180, 180).fieldOf("yaw_offset").forGetter(OffsetRot::yawOffset),
			Codec.floatRange(-180, 180).fieldOf("pitch_offset").forGetter(OffsetRot::pitchOffset)
		).apply(instance, OffsetRot::new)
	);

	public OffsetRot(RotationRef source, float yawOffset, float pitchOffset) {
		this(source, new Vec2(pitchOffset, yawOffset));
	}

	public float yawOffset() {
		return rotOffset.y;
	}

	public float pitchOffset() {
		return rotOffset.x;
	}

	private static float wrap(float angle) {
		return ((angle + 180) % 360) - 180;
	}

	private static Vec2 wrap(Vec2 vec) {
		float pitch = wrap(vec.x);
		float yaw = vec.y;
		if (Math.abs(pitch) > 90) {
			yaw += 180;
			pitch = pitch > 0 ? 180 - pitch : -180 - pitch;
		}
		return new Vec2(pitch, wrap(yaw));
	}

	@Override
	public Optional<Vec2> get(RefContext ctx) {
		return source.get(ctx).map(rot -> wrap(rot.add(rotOffset)));
	}

	@Override
	public RotationRefType<?> getType() {
		return RotationRefRegistry.OFFSET;
	}
}
