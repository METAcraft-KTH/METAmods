package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_lib.extensions.AbstractFurnaceEntityExtensions;
import se.datasektionen.mc.metacraft_lib.extensions.RecipeRemainderExtension;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class MixinAbstractFurnaceBlockEntity extends LockableContainerBlockEntity implements AbstractFurnaceEntityExtensions {

	@Unique
	private static final String IS_INPUT_EXTRACTABLE = "IsInputExtractable";

	@Shadow protected DefaultedList<ItemStack> inventory;

	@Unique
	private boolean isInputExtractable = false;

	@Unique
	private static final ThreadLocal<Boolean> shouldMakeExtractable = ThreadLocal.withInitial(() -> false);

	protected MixinAbstractFurnaceBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
		super(blockEntityType, blockPos, blockState);
	}

	@WrapOperation(
		method = "craftRecipe",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/item/ItemStack;decrement(I)V"
		)
	)
	private static void craftRecipe(
			ItemStack stack, int amount, Operation<Void> original,
			DynamicRegistryManager registryManager, @Nullable RecipeEntry<?> recipe, DefaultedList<ItemStack> slots
	) {
		if (recipe != null && recipe.value() instanceof RecipeRemainderExtension data && stack.getCount() - amount <= 0) {
			ItemStack replacement = data.metacraft_lib$getRemainderFunction().apply(stack);
			original.call(stack, amount);
			slots.set(0, replacement);
			if (!replacement.isEmpty()) {
				shouldMakeExtractable.set(true);
			}
		} else {
			original.call(stack, amount);
		}
	}

	@Inject(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;setLastRecipe(Lnet/minecraft/recipe/RecipeEntry;)V"
		)
	)
	private static void setInputExtractable(
			World world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci
	) {
		if (shouldMakeExtractable.get()) {
			((MixinAbstractFurnaceBlockEntity) (Object) blockEntity).isInputExtractable = true;
			blockEntity.markDirty();
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private static void tickEnd(
			World world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci
	) {
		shouldMakeExtractable.remove();
	}

	@Inject(
		method = "getAvailableSlots",
		at = @At("HEAD"),
		cancellable = true
	)
	public void getAvailableSlots(Direction side, CallbackInfoReturnable<int[]> cir) {
		if (
				side == Direction.DOWN && !inventory.get(0).isEmpty() && isInputExtractable
		) {
			cir.setReturnValue(new int[]{0, 1, 2});
		}
	}

	@Inject(
			method = "canExtract",
			at = @At("HEAD"),
			cancellable = true
	)
	public void canExtract(int slot, ItemStack stack, Direction dir, CallbackInfoReturnable<Boolean> cir) {
		if (
				dir == Direction.DOWN && slot == 0
		) {
			cir.setReturnValue(
					!inventory.get(0).isEmpty() && isInputExtractable
			);
		}
	}

	@Inject(method = "writeNbt", at = @At("RETURN"))
	public void writeNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfo ci) {
		nbt.putBoolean(IS_INPUT_EXTRACTABLE, isInputExtractable);
	}

	@Inject(method = "readNbt", at = @At("RETURN"))
	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfo ci) {
		isInputExtractable = nbt.getBoolean(IS_INPUT_EXTRACTABLE);
	}

	@Inject(
			method = "setStack",
			at = @At("HEAD")
	)
	public void onSetStack(int slot, ItemStack stack, CallbackInfo ci) {
		if (slot == 0 && !inventory.get(0).isEmpty() && !ItemStack.areItemsAndComponentsEqual(stack, inventory.get(0))) {
			isInputExtractable = false;
			this.markDirty();
		}
	}

	@Inject(
			method = {
					"dropExperienceForRecipesUsed"
			},
			at = @At("RETURN")
	)
	public void onRemoveStack(ServerPlayerEntity player, CallbackInfo ci) {
		isInputExtractable = false;
		this.markDirty();
	}

	@Override
	public void metacraft_lib$unsetInputExtractable() {
		isInputExtractable = false;
		this.markDirty();
	}
}
