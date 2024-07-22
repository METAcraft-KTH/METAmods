package se.datasektionen.mc.metacraft_plots.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_plots.item.PlotItems;
import se.datasektionen.mc.metacraft_plots.item.PlotKey;

import java.util.function.BiConsumer;

@Mixin(GrindstoneScreenHandler.class)
public class MixinGrindstoneScreenHandler {

	@Shadow @Final Inventory input;

	@Shadow @Final private Inventory result;

	@Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
	public void updateResult(CallbackInfo ci) {
		if (this.input.getStack(0).isOf(PlotItems.PLOT_MASTER_KEY) && this.input.getStack(1).isEmpty()) {
			this.result.setStack(0, this.input.getStack(0).copy());
			ci.cancel();
		}
	}

	@Mixin(targets = "net.minecraft.screen.GrindstoneScreenHandler$2")
	private static class InputSlot1 {
		@Inject(
				method = "canInsert",
				at = @At("HEAD"),
				cancellable = true
		)
		public void canInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
			if (stack.isOf(PlotItems.PLOT_MASTER_KEY)) {
				cir.setReturnValue(true);
			}
		}
	}

	@Mixin(targets = "net.minecraft.screen.GrindstoneScreenHandler$4")
	private static class ResultSlot {

		@Shadow @Final GrindstoneScreenHandler field_16780;

		@ModifyArg(
			method = "onTakeItem",
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/screen/ScreenHandlerContext;run(Ljava/util/function/BiConsumer;)V"
			)
		)
		public BiConsumer<World, BlockPos> onTakeItemInsideContext(BiConsumer<World, BlockPos> function) {
			var grindstone = (AccessorGrindstoneScreenHandler) field_16780;
			var plotMasterKey = grindstone.getInput().getStack(0);
			if (
					plotMasterKey.isOf(PlotItems.PLOT_MASTER_KEY) &&
					grindstone.getInput().getStack(1).isEmpty()
			) {
				return (world, pos) -> {
					PlotKey.getZone(plotMasterKey, world.getServer()).ifPresent(zone -> {
						zone.plotData().revokeAllSecondarySecrets();
					});
					world.syncWorldEvent(WorldEvents.GRINDSTONE_USED, pos, 0);
				};
			}
			return function;
		}
	}

}
