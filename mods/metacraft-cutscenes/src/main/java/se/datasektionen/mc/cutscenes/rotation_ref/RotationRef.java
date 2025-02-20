package se.datasektionen.mc.cutscenes.rotation_ref;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec2f;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;

import java.util.Optional;

public interface RotationRef {

	//Important: Pitch is x and yaw is y!
	Optional<Vec2f> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance);

	RotationRefType<?> getType();

}
