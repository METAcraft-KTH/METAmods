package nu.metacraft.plots.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.plots.item.PlotItems;
import nu.metacraft.plots.item.PlotKey;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

	public AnvilMenuMixin(@Nullable MenuType<?> type, int syncId, Inventory playerInventory, ContainerLevelAccess context, ItemCombinerMenuSlotDefinition forgingSlotsManager) {
		super(type, syncId, playerInventory, context, forgingSlotsManager);
	}

	@Inject(
		method = "onTake",
		at = @At("HEAD")
	)
	public void onTakeOutput(Player player, ItemStack stack, CallbackInfo ci) {
		if (stack.is(PlotItems.PLOT_KEY) && stack.has(DataComponents.CUSTOM_NAME)) {
			PlotKey.renameKey(stack, player.level().getServer(), stack.getHoverName().getString());
		}
	}

	@Inject(method = "createResult", at = @At("RETURN"))
	public void updateResult(CallbackInfo ci) {
		var output = this.resultSlots.getItem(0);
		if (this.inputSlots.getItem(0).is(PlotItems.PLOT_KEY) && output.is(PlotItems.PLOT_KEY) && output.has(DataComponents.CUSTOM_NAME)) {
			PlotKey.getZone(output, this.player.level().getServer()).ifPresent(zone -> {
				if (!zone.plotData().friendlyNameIsOccupied(output.getHoverName().getString())) {
					PlotKey.setPlaceholderName(output, output.getHoverName().getString());
				}
			});
		}
	}

}
