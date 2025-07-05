package nu.metacraft.lib.util.helper;

import net.minecraft.block.enums.Orientation;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;

public class OrientationHelper {

	public static Orientation rotate(Orientation orientation, BlockRotation rotation) {
		return Orientation.byDirections(rotation.rotate(orientation.getFacing()), rotation.rotate(orientation.getRotation()));
	}

	public static boolean isHorizontal(Orientation orientation) {
		return orientation.getFacing().getAxis().isHorizontal();
	}

	public static boolean isVertical(Orientation orientation) {
		return orientation.getFacing().getAxis().isVertical();
	}

	public static Orientation fromDirection(Direction direction) {
		return Orientation.byDirections(direction, direction.getAxis().isHorizontal() ? Direction.UP : Direction.SOUTH);
	}

}
