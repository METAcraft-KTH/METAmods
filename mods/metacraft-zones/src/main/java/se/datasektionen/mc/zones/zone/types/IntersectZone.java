package se.datasektionen.mc.zones.zone.types;

import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.stream.Streams;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IntersectZone extends CombinedZone {

	public IntersectZone(ZoneType... zones) {
		this(new ArrayList<>(Arrays.asList(zones)));
	}

	public IntersectZone(List<ZoneType> zones) {
		super(zones);
	}

	@Override
	protected double getSize(Iterable<ZoneType> zones) {
		return Streams.of(zones).mapToDouble(ZoneType::getSize).min().orElse(0);
	}

	@Override
	protected double updateSize(ZoneType newZone, UpdateDirection direction) {
		return switch (direction) {
			case ADD -> Math.min(size, newZone.getSize());
			case REMOVE -> getSize(zones);
		};
	}

	@Override
	public boolean combinedZoneContains(BlockPos pos) {
		for (var zone : zones) {
			if (!zone.contains(pos)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ZoneType copy(List<ZoneType> zones) {
		return new IntersectZone(zones);
	}

	@Override
	public ZoneRegistry.ZoneTypeType<?> getType() {
		return ZoneRegistry.intersect;
	}
}
