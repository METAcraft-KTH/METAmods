package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.extensions.AbstractFurnaceEntityExtensions;
import nu.metacraft.lib.extensions.RecipeRemainderExtension;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin extends BaseContainerBlockEntity implements AbstractFurnaceEntityExtensions {

	@Unique
	private static final String IS_INPUT_EXTRACTABLE = "IsInputExtractable";

	@Shadow protected NonNullList<ItemStack> items;

	@Unique
	private boolean isInputExtractable = false;

	@Unique
	private static final ThreadLocal<Boolean> shouldMakeExtractable = ThreadLocal.withInitial(() -> false);

	protected AbstractFurnaceBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
		super(blockEntityType, blockPos, blockState);
	}

	@WrapOperation(
		method = "burn",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"
		)
	)
	private static void craftRecipe(
			ItemStack stack, int amount, Operation<Void> original,
			RegistryAccess registryManager, @Nullable RecipeHolder<?> recipe,
			SingleRecipeInput input,
			NonNullList<ItemStack> slots
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
		method = "serverTick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;setRecipeUsed(Lnet/minecraft/world/item/crafting/RecipeHolder;)V"
		)
	)
	private static void setInputExtractable(
			ServerLevel world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci
	) {
		if (shouldMakeExtractable.get()) {
			((AbstractFurnaceBlockEntityMixin) (Object) blockEntity).isInputExtractable = true;
			blockEntity.setChanged();
		}
	}

	@Inject(method = "serverTick", at = @At("RETURN"))
	private static void tickEnd(
			ServerLevel world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci
	) {
		shouldMakeExtractable.remove();
	}

	@Inject(
		method = "getSlotsForFace",
		at = @At("HEAD"),
		cancellable = true
	)
	public void getAvailableSlots(Direction side, CallbackInfoReturnable<int[]> cir) {
		if (
				side == Direction.DOWN && !items.get(0).isEmpty() && isInputExtractable
		) {
			cir.setReturnValue(new int[]{0, 1, 2});
		}
	}

	@Inject(
			method = "canTakeItemThroughFace",
			at = @At("HEAD"),
			cancellable = true
	)
	public void canExtract(int slot, ItemStack stack, Direction dir, CallbackInfoReturnable<Boolean> cir) {
		if (
				dir == Direction.DOWN && slot == 0
		) {
			cir.setReturnValue(
					!items.get(0).isEmpty() && isInputExtractable
			);
		}
	}

	@Inject(method = "saveAdditional", at = @At("RETURN"))
	public void writeNBT(ValueOutput nbt, CallbackInfo ci) {
		nbt.putBoolean(IS_INPUT_EXTRACTABLE, isInputExtractable);
	}

	@Inject(method = "loadAdditional", at = @At("RETURN"))
	public void readNBT(ValueInput nbt, CallbackInfo ci) {
		isInputExtractable = nbt.getBooleanOr(IS_INPUT_EXTRACTABLE, false);
	}

	@Inject(
			method = "setItem",
			at = @At("HEAD")
	)
	public void onSetStack(int slot, ItemStack stack, CallbackInfo ci) {
		if (slot == 0 && !items.get(0).isEmpty() && !ItemStack.isSameItemSameComponents(stack, items.get(0))) {
			isInputExtractable = false;
			this.setChanged();
		}
	}

	@Inject(
			method = {
					"awardUsedRecipesAndPopExperience"
			},
			at = @At("RETURN")
	)
	public void onRemoveStack(ServerPlayer player, CallbackInfo ci) {
		isInputExtractable = false;
		this.setChanged();
	}

	@Override
	public void metacraft_lib$unsetInputExtractable() {
		isInputExtractable = false;
		this.setChanged();
	}
}
