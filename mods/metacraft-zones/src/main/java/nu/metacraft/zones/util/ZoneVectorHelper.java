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

	private static Vec3 incrementYIfNecessary(Vec3 vector) {
		if (vector.y > 0 && vector.y < 1) {
			return vector.add(0, 1, 0).normalize();
		}
		return vector;
	}

	public static ZoneType.InwardVector getVectorForZoneFromPivots(Predicate<BlockPos> contains, Vec3 pos, Stream<Vec3> pivots) {
		var pivotsSorted = pivots.sorted(
				Comparator.comparingDouble(pivot -> pivot.distanceToSqr(pos))
		).toList();
		Vec3 nearest = pivotsSorted.stream().filter(
				p -> {
					var bPos = BlockPos.containing(p);
					return contains.test(bPos) || contains.test(bPos.north().west());
				}
		).findAny().orElseGet(
				() -> pivotsSorted.stream().reduce(Vec3::add).map(v -> v.scale(1.0 / pivotsSorted.size())).orElseThrow()
		);
		if (contains.test(BlockPos.containing(pos))) {
			return new ZoneType.InwardVector(incrementYIfNecessary(pos.subtract(nearest).normalize()), nearest);
		} else {
			return new ZoneType.InwardVector(incrementYIfNecessary(nearest.subtract(pos).normalize()), nearest);
		}
	}

}
