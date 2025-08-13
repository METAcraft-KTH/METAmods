package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.timer.Timer;
import nu.metacraft.lib.scheduler.Throwaway;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

@Mixin(Timer.class)
public class MixinTimer<T> {

	@ModifyExpressionValue(
		method = "toNbt",
		at = @At(
				value = "INVOKE",
				target = "Ljava/util/Queue;stream()Ljava/util/stream/Stream;"
		)
	)
	public Stream<Timer.Event<T>> serialize(
			Stream<Timer.Event<T>> original
	) {
		return original.filter(event -> !(event.callback instanceof Throwaway));
	}

}
