package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelStorageSource.class)
public class LevelStorageSourceMixin {

	@WrapOperation(
			method = "readExistingSavedData",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/nbt/NbtAccounter;defaultQuota()Lnet/minecraft/nbt/NbtAccounter;"
			)
	)
	private static <T extends SavedData> NbtAccounter readExistingSavedData(
			Operation<NbtAccounter> original,
			@Local(argsOnly = true, name = "savedDataType") SavedDataType<T> savedDataType
	) {
		if (savedDataType == WorldGenSettings.TYPE) {
			return NbtAccounter.unlimitedHeap();
		}
		return original.call();
	}

}
