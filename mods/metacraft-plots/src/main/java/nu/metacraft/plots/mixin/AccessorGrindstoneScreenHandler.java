package nu.metacraft.plots.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GrindstoneMenu.class)
public interface AccessorGrindstoneScreenHandler {

	@Accessor
	Container getRepairSlots();

}
