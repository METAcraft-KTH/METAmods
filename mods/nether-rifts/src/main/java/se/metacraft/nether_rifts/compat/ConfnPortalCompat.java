package se.metacraft.nether_rifts.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.portal.PortalShape;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ConfnPortalCompat {

	private static final Field PORTAL_SHAPE_BLOCKS;

	static {
		Field blocks = null;
		try {
			blocks = PortalShape.class.getDeclaredField("blocks");
			if (!blocks.getType().equals(ArrayList.class)) {
				blocks = null;
			}
			if (blocks != null) {
				blocks.setAccessible(true);
			}
		} catch (NoSuchFieldException ignored) {}
		PORTAL_SHAPE_BLOCKS = blocks;
	}

	public static List<BlockPos> getBlocks(PortalShape shape) {
		if (PORTAL_SHAPE_BLOCKS == null) return List.of();
		try {
			var list = (List<?>) PORTAL_SHAPE_BLOCKS.get(shape);
			if (list.isEmpty()) return List.of();
			for (var e : list) {
				if (!(e instanceof BlockPos)) {
					return List.of();
				}
			}
			//noinspection unchecked
			return (List<BlockPos>) list;
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

}
