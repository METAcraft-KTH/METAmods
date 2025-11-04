package nu.metacraft.repair_fix.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import nu.metacraft.repair_fix.RepairFixConfig;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AnvilMenu.class, priority = 0)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

	@Shadow @Final private DataSlot cost;

	public AnvilMenuMixin(@Nullable MenuType<?> type, int syncId, Inventory playerInventory, ContainerLevelAccess context, ItemCombinerMenuSlotDefinition forgingSlotsManager) {
		super(type, syncId, playerInventory, context, forgingSlotsManager);
	}

	@WrapOperation(
		method = "createResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;isValidRepairItem(Lnet/minecraft/world/item/ItemStack;)Z"
		)
	)
	public boolean canRepair(
			ItemStack stack, ItemStack ingredient, Operation<Boolean> op,
			@Share("parse") LocalRef<Integer> parse
	) {
		return RepairFixConfig.getConfig(player.level().getServer()).findRepairCount(stack, ingredient).stream().mapToObj(value -> {
			parse.set(value);
			return value > 0;
		}).findAny().orElse(op.call(stack, ingredient));
	}

	@ModifyExpressionValue(
		method = "createResult",
		at = @At(
			value = "CONSTANT",
			args = "intValue=4"
		)
	)
	public int modifyValue(
			int constant, @Share("parse") LocalRef<Integer> parse
	) {
		if (parse.get() != null) {
			return parse.get();
		}
		return constant;
	}

	@ModifyExpressionValue(
		method = "createResult",
		at = @At(
			value = "CONSTANT",
			args = "intValue=40",
			ordinal = 1
		)
	)
	public int checkAboveMaxLevel(int constant) {
		return RepairFixConfig.getConfig().getMaxRepairCost();
	}

	@ModifyExpressionValue(
		method = "createResult",
		at = @At(
			value = "CONSTANT",
			args = "intValue=40",
			ordinal = 2
		)
	)
	public int actWhenAboveMaxLevel(int constant) {
		return RepairFixConfig.getConfig().getMaxRepairCost();
	}

	@ModifyExpressionValue(
		method = "createResult",
		at = @At(
			value = "CONSTANT",
			args = "intValue=39"
		)
	)
	public int setMaxLevelWhenTooHigh(int constant) {
		return RepairFixConfig.getConfig().getMaxRepairCost()-1;
	}

	@Inject(
		method = "createResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/DataSlot;get()I",
			ordinal = 1
		)
	)
	public void capAtMaxLevelIfConfigured(CallbackInfo ci) {
		if (RepairFixConfig.getConfig().capAtMaxLevel()) {
			if (this.cost.get() >= RepairFixConfig.getConfig().getMaxRepairCost()) {
				this.cost.set(RepairFixConfig.getConfig().getMaxRepairCost()-1);
				if (!player.level().isClientSide()) {
					((ServerPlayer) player).connection.send(
							new ClientboundContainerSetDataPacket(containerId, 0, this.cost.get())
					);
				}
			}
		}
	}

	@WrapOperation(
		method = "createResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z"
		)
	)
	public boolean setAdditionalCostOnCanCombine(
			Holder<Enchantment> first, Holder<Enchantment> second, Operation<Boolean> org,
			@Share("additionalCost") LocalIntRef additionalCost
	) {
		return RepairFixConfig.getConfig(player.level().getServer()).findCombineCost(first, second).stream().mapToObj(result -> {
			additionalCost.set(result);
			return true;
		}).findAny().orElse(org.call(first, second));
	}

	@ModifyVariable(
		method = "createResult",
		slice = @Slice(from = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"
		)),
		at = @At(
			value = "JUMP", opcode = Opcodes.GOTO, ordinal = 0
		),
		ordinal = 0
	)
	public int addToCost( //Funnily enough, this mixin is in the right place. IntelliJ just doesn't agree.
			int value, @Local(ordinal = 3) boolean flag3,
			@Share("additionalCost") LocalIntRef additionalCost
	) {
		if (flag3) {
			value += additionalCost.get();
		}
		return value;
	}

	@Inject(
		method = "createResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I",
			ordinal = 0
		)
	)
	public void setAddingEnchantment(
			CallbackInfo info, @Share("isAddingEnchantment") LocalBooleanRef isAddingEnchantment
	) {
		isAddingEnchantment.set(true);
	}

	@Redirect(
		method = "createResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/AnvilMenu;calculateIncreasedRepairCost(I)I"
		)
	)
	public int preventPriceGrowth(int value, @Share("isAddingEnchantment") LocalBooleanRef isAddingEnchantment) {
		value = switch (RepairFixConfig.getConfig().baseCostIncreaseMode()) {
			case DEFAULT -> AnvilMenu.calculateIncreasedRepairCost(value);
			case ENCHANTING_ONLY -> {
				if (isAddingEnchantment.get()) {
					yield AnvilMenu.calculateIncreasedRepairCost(value);
				} else {
					yield value;
				}
			}
			case NONE -> value;
		};
		return value;
	}

}
