package se.datasektionen.mc.cutscenes.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;

import java.util.List;
import java.util.Optional;

public class FirstValidPos implements PositionRef {

	public static final MapCodec<FirstValidPos> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> PositionRefRegistry.CODEC).listOf().fieldOf("positions").forGetter(ref -> ref.positions)
			).apply(instance, FirstValidPos::new)
	);

	private final List<PositionRef> positions;

	public FirstValidPos(List<PositionRef> positions) {
		this.positions = positions;
	}

	@Override
	public Optional<Vec3d> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance) {
		return positions.stream().map(
				pos -> pos.get(player, cutsceneInstance)
		).filter(Optional::isPresent).map(Optional::get).findFirst();
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.FIRST_VALID;
	}
}
