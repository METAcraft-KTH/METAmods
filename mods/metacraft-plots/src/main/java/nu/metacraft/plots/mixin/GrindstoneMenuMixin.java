package nu.metacraft.plots.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.plots.item.PlotItems;
import nu.metacraft.plots.item.PlotKey;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;

@Mixin(GrindstoneMenu.class)
public class GrindstoneMenuMixin {

	@Shadow @Final Container repairSlots;

	@Shadow @Final private Container resultSlots;

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	public void updateResult(CallbackInfo ci) {
		if (this.repairSlots.getItem(0).is(PlotItems.PLOT_MASTER_KEY) && this.repairSlots.getItem(1).isEmpty()) {
			this.resultSlots.setItem(0, this.repairSlots.getItem(0).copy());
			ci.cancel();
		}
	}

	@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$2")
	private static class InputSlot1 {
		@Inject(
				method = "mayPlace",
				at = @At("HEAD"),
				cancellable = true
		)
		public void canInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
			if (stack.is(PlotItems.PLOT_MASTER_KEY)) {
				cir.setReturnValue(true);
			}
		}
	}

	@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
	private static class ResultSlot {

		@Shadow @Final GrindstoneMenu this$0;

		@ModifyArg(
			method = "onTake",
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"
			)
		)
		public BiConsumer<Level, BlockPos> onTakeItemInsideContext(BiConsumer<Level, BlockPos> function) {
			var grindstone = (GrindstoneMenuAccessor) this$0;
			var plotMasterKey = grindstone.getRepairSlots().getItem(0);
			if (
					plotMasterKey.is(PlotItems.PLOT_MASTER_KEY) &&
					grindstone.getRepairSlots().getItem(1).isEmpty()
			) {
				return (world, pos) -> {
					PlotKey.getZone(plotMasterKey, world.getServer()).ifPresent(zone -> {
						zone.plotData().revokeAllSecondarySecrets();
					});
					world.levelEvent(LevelEvent.SOUND_GRINDSTONE_USED, pos, 0);
				};
			}
			return function;
		}
	}

}
