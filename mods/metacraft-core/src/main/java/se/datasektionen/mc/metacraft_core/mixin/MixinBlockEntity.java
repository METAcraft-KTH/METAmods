package se.datasektionen.mc.metacraft_core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.extensions.BlockEntityExtensions;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;

@Mixin(BlockEntity.class)
public abstract class MixinBlockEntity implements BlockEntityExtensions {

	@Unique
	private static final String IS_MOVABLE = "IsMovable";

	@Shadow public abstract void markDirty();

	@Shadow @Nullable protected World world;
	@Unique
	private boolean isMovable = false;

	@Override
	public void metacraft_core$setMovable(boolean movable) {
		this.isMovable = movable;
		validate();
		if (this.world != null) {
			markDirty();
		}
	}

	@Override
	public boolean metacraft_core$isMovable() {
		return isMovable;
	}

	@Inject(method = {"read", "readComponentlessNbt"}, at = @At("RETURN"))
	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfo ci) {
		isMovable = nbt.getBoolean(IS_MOVABLE, false);
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

	@ModifyReturnValue(method = {"createNbt", "createComponentlessNbt"}, at = @At("RETURN"))
	public NbtCompound writeNBT(
			NbtCompound nbt
	) {
		nbt.putBoolean(IS_MOVABLE, isMovable);
		return nbt;
	}
}
