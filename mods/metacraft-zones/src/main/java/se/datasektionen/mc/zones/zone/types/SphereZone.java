package se.datasektionen.mc.zones.zone.types;

import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

public class SphereZone extends CircleZone {

	public SphereZone(BlockPos center, double radius) {
		super(center, radius);
	}

	@Override
	public boolean contains(BlockPos pos) {
		return center.isWithinDistance(pos, radius);
	}

	@Override
	public double getSize() {
		return radius * radius * radius * 4/3 * Math.PI;
	}

	@Override
	public ZoneType copy() {
		return new SphereZone(center.toImmutable(), radius);
	}

	@Override
	public ZoneRegistry.ZoneTypeType<? extends SphereZone> getType() {
		return ZoneRegistry.sphere;
	}

	@Override
	public String toString() {
		return super.toString().replace("Circle", "Sphere");
	}
}
