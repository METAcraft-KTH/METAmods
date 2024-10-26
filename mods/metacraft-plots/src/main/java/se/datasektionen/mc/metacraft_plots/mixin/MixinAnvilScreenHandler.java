package se.datasektionen.mc.metacraft_plots.mixin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.ForgingSlotsManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_plots.item.PlotItems;
import se.datasektionen.mc.metacraft_plots.item.PlotKey;

@Mixin(AnvilScreenHandler.class)
public abstract class MixinAnvilScreenHandler extends ForgingScreenHandler {

	public MixinAnvilScreenHandler(@Nullable ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context, ForgingSlotsManager forgingSlotsManager) {
		super(type, syncId, playerInventory, context, forgingSlotsManager);
	}

	@Inject(
		method = "onTakeOutput",
		at = @At("HEAD")
	)
	public void onTakeOutput(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		if (stack.isOf(PlotItems.PLOT_KEY) && stack.contains(DataComponentTypes.CUSTOM_NAME)) {
			PlotKey.renameKey(stack, player.getServer(), stack.getName().getString());
		}
	}

	@Inject(method = "updateResult", at = @At("RETURN"))
	public void updateResult(CallbackInfo ci) {
		var output = this.output.getStack(0);
		if (this.input.getStack(0).isOf(PlotItems.PLOT_KEY) && output.isOf(PlotItems.PLOT_KEY) && output.contains(DataComponentTypes.CUSTOM_NAME)) {
			PlotKey.getZone(output, this.player.getServer()).ifPresent(zone -> {
				if (!zone.plotData().friendlyNameIsOccupied(output.getName().getString())) {
					PlotKey.setPlaceholderName(output, output.getName().getString());
				}
			});
		}
	}

}
