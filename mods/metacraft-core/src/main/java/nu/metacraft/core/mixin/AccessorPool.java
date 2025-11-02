package nu.metacraft.core.mixin;

import net.minecraft.util.collection.Pool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Pool.class)
public interface AccessorPool {

	@Accessor
	int getTotalWeight();

}
