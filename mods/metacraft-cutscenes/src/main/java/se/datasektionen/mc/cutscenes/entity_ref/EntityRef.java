package se.datasektionen.mc.cutscenes.entity_ref;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;

import java.util.stream.Stream;

public interface EntityRef {

	Stream<? extends Entity> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance);

	EntityRefType<?> getType();

}
