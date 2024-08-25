package se.datasektionen.mc.cutscenes.position_ref;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;

import java.util.Optional;

public interface PositionRef {

	Optional<Vec3d> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance);

	PositionRefType<?> getType();

}
