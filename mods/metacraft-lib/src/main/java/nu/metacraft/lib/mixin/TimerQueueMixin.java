package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import nu.metacraft.lib.scheduler.Throwaway;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;
import net.minecraft.world.level.timers.TimerQueue;

@Mixin(TimerQueue.class)
public class TimerQueueMixin<T> {

	@ModifyExpressionValue(
		method = "store",
		at = @At(
				value = "INVOKE",
				target = "Ljava/util/Queue;stream()Ljava/util/stream/Stream;"
		)
	)
	public Stream<TimerQueue.Event<T>> serialize(
			Stream<TimerQueue.Event<T>> original
	) {
		return original.filter(event -> !(event.callback instanceof Throwaway));
	}

}
