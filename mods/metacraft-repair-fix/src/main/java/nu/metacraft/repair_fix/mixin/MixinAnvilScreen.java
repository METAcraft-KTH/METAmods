package nu.metacraft.repair_fix.mixin;

import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import nu.metacraft.repair_fix.RepairFixConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AnvilScreen.class)
public class MixinAnvilScreen {

	@ModifyConstant(method = "drawForeground", constant = @Constant(intValue = 40))
	public int anvilBugfixRemoveTooExpensiveClient(int constant) {
		return RepairFixConfig.getConfig().getMaxRepairCost();
	}

}
