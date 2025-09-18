package nu.metacraft.repair_fix.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerPropertyUpdateS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.ForgingSlotsManager;
import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.repair_fix.RepairFixConfig;
import nu.metacraft.repair_fix.parse.ParseBase;
import nu.metacraft.repair_fix.parse.RepairParse;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AnvilScreenHandler.class, priority = 0)
public abstract class MixinAnvilScreenHandler extends ForgingScreenHandler {

	@Shadow @Final private Property levelCost;

	public MixinAnvilScreenHandler(@Nullable ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context, ForgingSlotsManager forgingSlotsManager) {
		super(type, syncId, playerInventory, context, forgingSlotsManager);
	}

	@WrapOperation(
		method = "updateResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/item/ItemStack;canRepairWith(Lnet/minecraft/item/ItemStack;)Z"
		)
	)
	public boolean canRepair(
			ItemStack stack, ItemStack ingredient, Operation<Boolean> op,
			@Share("parse") LocalRef<ParseBase.ParseResult<ItemStack, RepairParse>> parse
	) {
		return RepairFixConfig.getConfig().repairConfigReader.get().getFirstMatch(stack, ingredient).map(value -> {
			parse.set(value);
			return value.result().shouldAllow();
		}).orElse(op.call(stack, ingredient));
	}

	@ModifyExpressionValue(
		method = "updateResult",
		at = @At(
			value = "CONSTANT",
			args = "intValue=4"
		)
	)
	public int modifyValue(
			int constant, @Share("parse") LocalRef<ParseBase.ParseResult<ItemStack, RepairParse>> parse
	) {
		if (parse.get() != null) {
			return parse.get().parse().getRepairItemsUntilFull();
		}
		return constant;
	}

	@ModifyExpressionValue(
		method = "updateResult",
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
		method = "updateResult",
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
		method = "updateResult",
		at = @At(
			value = "CONSTANT",
			args = "intValue=39"
		)
	)
	public int setMaxLevelWhenTooHigh(int constant) {
		return RepairFixConfig.getConfig().getMaxRepairCost()-1;
	}

	@Inject(
		method = "updateResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/screen/Property;get()I",
			ordinal = 1
		)
	)
	public void capAtMaxLevelIfConfigured(CallbackInfo ci) {
		if (RepairFixConfig.getConfig().capAtMaxLevel) {
			if (this.levelCost.get() >= RepairFixConfig.getConfig().getMaxRepairCost()) {
				this.levelCost.set(RepairFixConfig.getConfig().getMaxRepairCost()-1);
				if (!player.getEntityWorld().isClient()) {
					((ServerPlayerEntity) player).networkHandler.sendPacket(
							new ScreenHandlerPropertyUpdateS2CPacket(syncId, 0, this.levelCost.get())
					);
				}
			}
		}
	}

	@WrapOperation(
		method = "updateResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/enchantment/Enchantment;canBeCombined(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/registry/entry/RegistryEntry;)Z"
		)
	)
	public boolean setAdditionalCostOnCanCombine(
			RegistryEntry<Enchantment> first, RegistryEntry<Enchantment> second, Operation<Boolean> org,
			@Share("additionalCost") LocalIntRef additionalCost
	) {
		return RepairFixConfig.getConfig().enchantmentConfigReader.getFirstMatch(first, second).map(result -> {
			additionalCost.set(result.parse().getAdditionalCost());
			return result.result().shouldAllow();
		}).orElse(org.call(first, second));
	}

	@ModifyVariable(
		method = "updateResult",
		slice = @Slice(from = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/enchantment/Enchantment;isAcceptableItem(Lnet/minecraft/item/ItemStack;)Z"
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
		method = "updateResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/enchantment/Enchantment;getMaxLevel()I",
			ordinal = 0
		)
	)
	public void setAddingEnchantment(
			CallbackInfo info, @Share("isAddingEnchantment") LocalBooleanRef isAddingEnchantment
	) {
		isAddingEnchantment.set(true);
	}

	@Redirect(
		method = "updateResult",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/screen/AnvilScreenHandler;getNextCost(I)I"
		)
	)
	public int preventPriceGrowth(int value, @Share("isAddingEnchantment") LocalBooleanRef isAddingEnchantment) {
		value = switch (RepairFixConfig.getConfig().baseCostIncreaseMode) {
			case DEFAULT -> AnvilScreenHandler.getNextCost(value);
			case ENCHANTING_ONLY -> {
				if (isAddingEnchantment.get()) {
					yield AnvilScreenHandler.getNextCost(value);
				} else {
					yield value;
				}
			}
			case NONE -> value;
		};
		return value;
	}

}
