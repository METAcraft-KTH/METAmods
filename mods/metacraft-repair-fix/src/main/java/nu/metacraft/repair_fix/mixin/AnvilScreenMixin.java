package nu.metacraft.repair_fix.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import nu.metacraft.repair_fix.RepairFixConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilScreen.class)
public class AnvilScreenMixin {

	@ModifyExpressionValue(
			method = "extractLabels",
			at = @At(
					value = "CONSTANT",
					args = "intValue=40"
			)
	)
	public int anvilBugfixRemoveTooExpensiveClient(int constant) {
		return RepairFixConfig.getConfig().getMaxRepairCost();
	}

}
