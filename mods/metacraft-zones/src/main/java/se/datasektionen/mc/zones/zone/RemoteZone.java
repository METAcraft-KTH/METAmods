package se.datasektionen.mc.zones.zone;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import se.datasektionen.mc.zones.zone.data.ZoneData;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;
import se.datasektionen.mc.zones.zone.types.ZoneType;

import java.util.Collection;
import java.util.Optional;

public class RemoteZone extends Zone {

	private final World world;
	private final RealZone container;
	private final ZoneType zone;

	public RemoteZone(World world, RealZone container) {
		this.world = world;
		this.container = container;
		this.zone = container.getZone().copy();
		this.zone.setZoneRef(this);
	}

	@Override
	public boolean contains(BlockPos pos) {
		double factor = DimensionType.getCoordinateScaleFactor(world.getDimension(), container.world.getDimension());
		return zone.contains(
				world.getWorldBorder().clamp(
						pos.getX() * factor, pos.getY(), pos.getZ() * factor
				)
		);
	}

	@Override
	public <T extends ZoneData> Optional<T> get(ZoneDataType<T> data) {
		return container.get(data);
	}

	@Override
	public <T extends ZoneData> T getOrCreate(ZoneDataType<T> data) {
		return container.getOrCreate(data);
	}

	@Override
	protected Collection<ZoneData> getZoneDatas() {
		return container.getZoneDatas();
	}

	@Override
	public String getName() {
		return container.getName();
	}

	@Override
	public RegistryKey<World> getDim() {
		return world.getRegistryKey();
	}

	@Override
	public ZoneType getZone() {
		return zone;
	}

	@Override
	public int getPriority() {
		return container.priority;
	}

	@Override
	public boolean isRealZone() {
		return false;
	}

	@Override
	public RealZone getRealZone() {
		return container;
	}

	@Override
	public World getWorld() {
		return world;
	}

	@Override
	public void markDirty() {
		container.markDirty();
	}
}
