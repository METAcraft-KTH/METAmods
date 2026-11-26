package se.metacraft.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulators;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.metacraft.portable_jukebox.item.components.Components;

import java.util.stream.Stream;

@Mixin(ContainerComponentManipulators.class)
public interface ContainerComponentManipulatorsMixin {

	@ModifyExpressionValue(
		method = "<clinit>",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/stream/Stream;of([Ljava/lang/Object;)Ljava/util/stream/Stream;"
		)
	)
	private static <T> Stream<T> init(@NotNull Stream<T> original) {
		//noinspection unchecked
		return Stream.concat(
			original, Stream.of(
				(T) Components.Manipulators.PORTABLE_JUKEBOX, (T) Components.Manipulators.PORTABLE_JUKEBOX_STORAGE
			)
		);
	}

}
