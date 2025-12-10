package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CompoundContainer.class)
public interface CompoundContainerAccessor {

	@Accessor
	Container getContainer1();

	@Accessor
	Container getContainer2();

}
