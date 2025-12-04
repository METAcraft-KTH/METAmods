package nu.metacraft.revival.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public class FoodDataMixin {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	public void tick(ServerPlayer player, CallbackInfo ci) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious()) {
			ci.cancel();
		}
	}

}
