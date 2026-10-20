package nu.metacraft.qol.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConcretePowderBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The concrete a powder hardens into, as vanilla links them. */
@Mixin(ConcretePowderBlock.class)
public interface ConcretePowderBlockAccessor {

	@Accessor("concrete")
	Block getConcrete();

}
