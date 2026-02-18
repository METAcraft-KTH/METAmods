package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import net.minecraft.util.DirectoryLock;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;

@Mixin(LevelStorageSource.LevelStorageAccess.class)
public class LevelStorageAccessMixin {

	@Shadow @Final
	DirectoryLock lock;

	@Shadow @Final private String levelId;

	@WrapOperation(
		method = "createLock",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/util/DirectoryLock;create(Ljava/nio/file/Path;)Lnet/minecraft/util/DirectoryLock;"
		)
	)
	public DirectoryLock init(Path path, Operation<DirectoryLock> original) {
		if (levelId != null) {
			return original.call(path);
		} else {
			return null;
		}
	}

	@Inject(method = "createPlayerStorage", at = @At("HEAD"), cancellable = true)
	public void createSaveHandler(CallbackInfoReturnable<PlayerDataStorage> cir) {
		if (lock == null) cir.setReturnValue(null);
	}

}
