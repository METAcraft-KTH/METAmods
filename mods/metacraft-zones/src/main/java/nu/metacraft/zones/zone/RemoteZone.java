package nu.metacraft.zones.zone;

import nu.metacraft.zones.zone.data.ZoneData;
import nu.metacraft.zones.zone.data.ZoneDataType;
import nu.metacraft.zones.zone.types.ZoneType;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class RemoteZone extends Zone {

	private final Level world;
	private final RealZone container;
	private final ZoneType zone;

	public RemoteZone(Level world, RealZone container) {
		this.world = world;
		this.container = container;
		this.zone = container.getZone().copy();
		this.zone.setZoneRef(this);
	}

	@Override
	public boolean isPosWithinZoneBoundsNoDimCheck(BlockPos pos) {
		double factor = DimensionType.getTeleportationScale(world.dimensionType(), container.world.dimensionType());
		return zone.contains(
				world.getWorldBorder().clampToBounds(
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
	public ResourceKey<Level> getDim() {
		return world.dimension();
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
	public Level getWorld() {
		return world;
	}

	@Override
	public void markDirty() {
		container.markDirty();
	}
}
