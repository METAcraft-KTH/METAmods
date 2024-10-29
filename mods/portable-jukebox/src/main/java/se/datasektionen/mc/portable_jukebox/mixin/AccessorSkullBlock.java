package se.datasektionen.mc.portable_jukebox.mixin;

import net.minecraft.block.SkullBlock;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkullBlock.class)
public interface AccessorSkullBlock {

	@Accessor("SHAPE")
	static VoxelShape getShape() {
		throw new IllegalStateException("Mixin Error");
	}

}
