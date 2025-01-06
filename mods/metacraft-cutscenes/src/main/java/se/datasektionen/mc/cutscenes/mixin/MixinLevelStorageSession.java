package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.PlayerSaveHandler;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.level.storage.SessionLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

@Mixin(LevelStorage.Session.class)
public class MixinLevelStorageSession {

	@Shadow @Final
	SessionLock lock;

	@Shadow @Final private String directoryName;

	@WrapOperation(
		method = "<init>",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/level/storage/SessionLock;create(Ljava/nio/file/Path;)Lnet/minecraft/world/level/storage/SessionLock;"
		)
	)
	public SessionLock init(Path path, Operation<SessionLock> original) {
		if (directoryName != null) {
			return original.call(path);
		} else {
			return null;
		}
	}

	@Inject(method = "createSaveHandler", at = @At("HEAD"), cancellable = true)
	public void createSaveHandler(CallbackInfoReturnable<PlayerSaveHandler> cir) {
		if (lock == null) cir.setReturnValue(null);
	}

}
