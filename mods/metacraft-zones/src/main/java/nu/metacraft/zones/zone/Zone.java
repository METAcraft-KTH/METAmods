package nu.metacraft.zones.zone;

import org.jetbrains.annotations.NotNull;
import nu.metacraft.zones.zone.data.ZoneData;
import nu.metacraft.zones.zone.data.ZoneDataEntityTracking;
import nu.metacraft.zones.zone.data.ZoneDataType;
import nu.metacraft.zones.zone.types.ZoneType;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public abstract class Zone implements Comparable<Zone> {

	protected final Set<Entity> entities = new HashSet<>();

	public abstract boolean isPosWithinZoneBoundsNoDimCheck(BlockPos pos);

	public abstract <T extends ZoneData> Optional<T> get(ZoneDataType<T> data);

	public abstract <T extends ZoneData> T getOrCreate(ZoneDataType<T> data);

	protected abstract Collection<ZoneData> getZoneDatas();

	public abstract String getName();

	public abstract ResourceKey<Level> getDim();

	public abstract ZoneType getZone();

	public abstract int getPriority();

	public abstract boolean isRealZone();

	public abstract RealZone getRealZone();

	public abstract Level getWorld();

	public void addToZone(Entity entity) {
		entities.add(entity);
		getZoneDatas().forEach(data -> {
			if (data instanceof ZoneDataEntityTracking tracking) {
				tracking.onEnter(entity);
			}
		});
	}

	public void removeFromZone(Entity entity) {
		getZoneDatas().forEach(data -> {
			if (data instanceof ZoneDataEntityTracking tracking) {
				tracking.onLeave(entity);
			}
		});
		entities.remove(entity);
	}

	public Set<Entity> getEntities() {
		return Collections.unmodifiableSet(entities);
	}


	@Override
	public int compareTo(@NotNull Zone zone) {
		int state = Integer.compare(zone.getPriority(), getPriority());
		if (state == 0) {
			state = Double.compare(this.getZone().getSize(), zone.getZone().getSize());
			if (state == 0) {
				return getName().compareTo(zone.getName());
			}
		}
		return state;
	}

	public abstract void markDirty();

}
