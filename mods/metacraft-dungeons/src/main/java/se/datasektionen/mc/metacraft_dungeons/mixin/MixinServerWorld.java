package se.datasektionen.mc.metacraft_dungeons.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;

@Mixin(ServerWorld.class)
public class MixinServerWorld {

	@Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
	private void addPlayer(ServerPlayerEntity player, CallbackInfo ci) {
		DungeonData.getIfPresent((ServerWorld) (Object) this).ifPresent(data -> {
			if (data.isClearing()) {
				data.teleportOut(player);
				ci.cancel();
			}
		});
	}

}
