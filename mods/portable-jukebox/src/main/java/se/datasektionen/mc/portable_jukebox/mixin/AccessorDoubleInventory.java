package se.datasektionen.mc.portable_jukebox.mixin;

import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DoubleInventory.class)
public interface AccessorDoubleInventory {

	@Accessor
	Inventory getFirst();

	@Accessor
	Inventory getSecond();

}
