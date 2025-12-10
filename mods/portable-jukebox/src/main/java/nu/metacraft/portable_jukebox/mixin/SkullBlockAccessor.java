package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkullBlock.class)
public interface SkullBlockAccessor {

	@Accessor("SHAPE")
	static VoxelShape getShape() {
		throw new IllegalStateException("Mixin Error");
	}

}
