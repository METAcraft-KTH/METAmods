package nu.metacraft.core.util.helper;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

public class BlockPosHelper {

	public static BlockPos lerp(float f, BlockPos lhs, BlockPos rhs) {
		if (f <= 0) return lhs;
		if (f >= 1) return rhs;
		return new BlockPos(
				Mth.lerpInt(f, lhs.getX(), rhs.getX()),
				Mth.lerpInt(f, lhs.getY(), rhs.getY()),
				Mth.lerpInt(f, lhs.getZ(), rhs.getZ())
		);
	}

}
