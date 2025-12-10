package nu.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;

@Mixin(SimpleContainer.class)
public interface SimpleContainerAccessor {

	@Accessor
	List<ContainerListener> getListeners();

}
