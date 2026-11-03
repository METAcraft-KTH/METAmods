package se.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.core.METAcraftCore;
import se.metacraft.core.extensions.BlockEntityExtensions;
import se.metacraft.lib.compat.IsLoaded;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements BlockEntityExtensions {

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

	@Inject(
		method = {
			"saveWithoutMetadata(Lnet/minecraft/world/level/storage/ValueOutput;)V",
			"saveCustomOnly(Lnet/minecraft/world/level/storage/ValueOutput;)V"
		},
		at = @At("RETURN")
	)
	public void writeNBT(ValueOutput output, CallbackInfo ci) {
		output.putBoolean(IS_MOVABLE, isMovable);
	}
}
