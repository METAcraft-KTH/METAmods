package nu.metacraft.plots.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.screen.GrindstoneScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GrindstoneScreenHandler.class)
public interface AccessorGrindstoneScreenHandler {

	@Accessor
	Inventory getInput();

}
