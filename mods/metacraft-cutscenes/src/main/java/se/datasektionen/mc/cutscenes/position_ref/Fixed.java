package se.datasektionen.mc.cutscenes.position_ref;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;

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
	public Optional<Vec3d> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance) {
		return Optional.of(pos);
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.FIXED;
	}
}
