package metacraft.moredyes.mixin;

import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Wolf.class)
public interface WolfAccessor {
	@Invoker("setCollarColor")
	void moredyes$setCollarColor(DyeColor color);
}
