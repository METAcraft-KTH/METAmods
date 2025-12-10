package nu.metacraft.core.mixin;

import net.minecraft.util.random.WeightedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WeightedList.class)
public interface WeightedListAccessor {

	@Accessor
	int getTotalWeight();

}
