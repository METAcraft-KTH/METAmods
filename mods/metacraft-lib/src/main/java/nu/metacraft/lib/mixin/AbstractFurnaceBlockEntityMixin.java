package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
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

	@Inject(
			method = "serverTick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",
					ordinal = 0
			)
	)
	private static void craftRecipe(
			ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity, CallbackInfo ci,
			@Local(name = "recipe") RecipeHolder<? extends AbstractCookingRecipe> recipe,
			@Share("replacement") LocalRef<ItemStack> replacement, @Local(name = "ingredient") ItemStack ingredient
	) {
		if (recipe != null && recipe.value() instanceof RecipeRemainderExtension data && ingredient.getCount() - 1 <= 0) {
			replacement.set(data.metacraft_lib$getRemainderFunction().apply(ingredient));
		}
	}

	@Inject(
			method = "serverTick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;setRecipeUsed(Lnet/minecraft/world/item/crafting/RecipeHolder;)V",
					ordinal = 0
			),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"
					)
			)
	)
	private static void craftRecipe(
			ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity, CallbackInfo ci,
			@Share("replacement") LocalRef<ItemStack> replacement
	) {
		if (replacement.get() != null) {
			((AbstractFurnaceBlockEntityAccessor) entity).callGetItems().set(0, replacement.get());
			if (!replacement.get().isEmpty()) {
				shouldMakeExtractable.set(true);
			}
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
