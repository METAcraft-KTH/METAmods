package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.extensions.BlockEntityExtensions;
import nu.metacraft.lib.compat.IsLoaded;

@Mixin(BlockEntity.class)
public abstract class MixinBlockEntity implements BlockEntityExtensions {

	@Unique
	private static final String IS_MOVABLE = "IsMovable";

	@Shadow public abstract void setChanged();

	@Shadow @Nullable protected Level level;
	@Unique
	private boolean isMovable = false;

	@Override
	public void metacraft_core$setMovable(boolean movable) {
		this.isMovable = movable;
		validate();
		if (this.level != null) {
			setChanged();
		}
	}

	@Override
	public boolean metacraft_core$isMovable() {
		return isMovable;
	}

	@Inject(method = {"loadWithComponents", "loadCustomOnly"}, at = @At("RETURN"))
	public void readNBT(ValueInput nbt, CallbackInfo ci) {
		isMovable = nbt.getBooleanOr(IS_MOVABLE, false);
		validate();
	}

	@Unique
	private void validate() {
		if (isMovable && !IsLoaded.CARPET.isLoaded()) {
			METAcraftCore.LOGGER.warn(
					"Movable block entities requires the carpet mod!"
			);
		}
	}

	@ModifyReturnValue(method = {"saveWithoutMetadata(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", "saveCustomOnly(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"}, at = @At("RETURN"))
	public CompoundTag writeNBT(
			CompoundTag nbt
	) {
		nbt.putBoolean(IS_MOVABLE, isMovable);
		return nbt;
	}
}
