package nu.metacraft.pause.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.pause.PauseData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

	@Inject(method = "move", at = @At("HEAD"), cancellable = true)
	public void onMove(MoverType moverType, Vec3 vec3, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			var data = PauseData.getInstance(player.level().getServer());
			if (data.isPaused() && PauseData.shouldFreezeWhenPaused(player)) {
				ci.cancel();
			}
		}
	}

}
