package nu.metacraft.lib.util.helper;

import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.Rotation;

public class OrientationHelper {

	public static FrontAndTop rotate(FrontAndTop orientation, Rotation rotation) {
		return FrontAndTop.fromFrontAndTop(rotation.rotate(orientation.front()), rotation.rotate(orientation.top()));
	}

	public static boolean isHorizontal(FrontAndTop orientation) {
		return orientation.front().getAxis().isHorizontal();
	}

	public static boolean isVertical(FrontAndTop orientation) {
		return orientation.front().getAxis().isVertical();
	}

	public static FrontAndTop fromDirection(Direction direction) {
		return FrontAndTop.fromFrontAndTop(direction, direction.getAxis().isHorizontal() ? Direction.UP : Direction.SOUTH);
	}

}
