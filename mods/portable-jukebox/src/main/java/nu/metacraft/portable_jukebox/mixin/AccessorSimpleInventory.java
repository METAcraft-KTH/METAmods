package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.inventory.SimpleInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(SimpleInventory.class)
public interface AccessorSimpleInventory {

	@Accessor
	List<InventoryChangedListener> getListeners();

}
