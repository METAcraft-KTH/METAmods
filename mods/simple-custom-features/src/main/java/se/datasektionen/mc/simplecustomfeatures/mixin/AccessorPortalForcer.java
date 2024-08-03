package se.datasektionen.mc.simplecustomfeatures.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PortalForcer.class)
public interface AccessorPortalForcer {

	@Invoker
	boolean callIsBlockStateValid(BlockPos.Mutable pos);

}
