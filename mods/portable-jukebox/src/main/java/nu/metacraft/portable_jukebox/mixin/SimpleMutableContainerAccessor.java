package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.world.item.component.SimpleMutableContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.item.ItemStack;

@Mixin(SimpleMutableContainer.class)
public interface SimpleMutableContainerAccessor {

	@Accessor
	List<ItemStack> getItems();

}
