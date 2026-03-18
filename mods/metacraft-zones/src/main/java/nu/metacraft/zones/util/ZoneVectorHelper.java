package nu.metacraft.zones.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.zones.zone.types.ZoneType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ZoneVectorHelper {

	public static ZoneType.InwardVector getVectorForZoneFromPivots(Predicate<BlockPos> contains, Vec3 pos, Vec3... pivots) {
		return getVectorForZoneFromPivots(contains, pos, Arrays.stream(pivots));
	}

	public static ZoneType.InwardVector getVectorForZoneFromPivots(Predicate<BlockPos> contains, Vec3 pos, Stream<Vec3> pivots) {
		Vec3 nearest = pivots.min(
				Comparator.comparingDouble(pivot -> pivot.distanceToSqr(pos))
		).orElseThrow();
		if (contains.test(BlockPos.containing(pos))) {
			return new ZoneType.InwardVector(pos.subtract(nearest).normalize(), nearest);
		} else {
			return new ZoneType.InwardVector(nearest.subtract(pos).normalize(), nearest);
		}
	}

}
