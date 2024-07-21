package se.datasektionen.mc.zones.zone;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import se.datasektionen.mc.zones.util.LockHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ZoneMap {

	private final ReadWriteLock worldZoneLock = new ReentrantReadWriteLock();

	protected final Multimap<RegistryKey<World>, Zone> worldZones = MultimapBuilder.hashKeys().treeSetValues().build();
	protected final Map<String, RealZone> zones = new ConcurrentHashMap<>();

	protected final Runnable markNeedsSave;
	protected final Consumer<Zone> onAdd;
	protected final Consumer<Zone> onRemove;

	public ZoneMap(Runnable markNeedsSave, Consumer<Zone> onAdd, Consumer<Zone> onRemove) {
		this.markNeedsSave = markNeedsSave;
		this.onAdd = onAdd;
		this.onRemove = onRemove;
	}

	public void addZone(Zone zone) {
		if (zone.isRealZone()) {
			markNeedsSave.run();
			onAdd.accept(zone);
		}
		addZoneInternal(zone);
	}

	private void addZoneInternal(Zone zone) {
		worldZoneLock.writeLock().lock();
		try {
			if (zone.isRealZone()) {
				zones.put(zone.getName(), zone.getRealZone());
			}
			worldZones.put(zone.getDim(), zone);
			if (zone.isRealZone()) {
				for (Zone remoteZone : zone.getRealZone().getRemoteZones()) {
					worldZones.put(remoteZone.getDim(), remoteZone);
				}
			}
		} finally {
			worldZoneLock.writeLock().unlock();
		}
	}

	public boolean removeZone(String name) {
		if (!zones.containsKey(name)) return false;
		removeZone(zones.get(name));
		return true;
	}

	public void removeZone(Zone zone) {
		worldZoneLock.writeLock().lock();
		try {
			if (zone.isRealZone()) {
				zones.remove(zone.getName());
				onRemove.accept(zone);
				markNeedsSave.run();
			}
			worldZones.remove(zone.getDim(), zone);
			if (zone.isRealZone()) {
				for (Zone remoteZone : zone.getRealZone().getRemoteZones()) {
					worldZones.remove(remoteZone.getDim(), remoteZone);
				}
			}
		} finally {
			worldZoneLock.writeLock().unlock();
		}
	}

	public RealZone getZone(String name) {
		return zones.get(name);
	}

	public void forZones(RegistryKey<World> dim, Consumer<Zone> run) {
		worldZoneLock.readLock().lock();
		try {
			worldZones.get(dim).forEach(run);
		} finally {
			worldZoneLock.readLock().unlock();
		}
	}

	public List<Zone> getZones(RegistryKey<World> dim, Predicate<Zone> zonePredicate) {
		return LockHelper.getThroughLock(
				worldZoneLock.readLock(),
				() -> worldZones.get(dim).stream().filter(zonePredicate).toList()
		);
	}

	public Optional<Zone> getFirstZoneMatching(RegistryKey<World> dim, Predicate<Zone> zonePredicate) {
		return LockHelper.getThroughLock(
				worldZoneLock.readLock(),
				() -> worldZones.get(dim).stream().filter(zonePredicate).findFirst()
		);
	}

	public <T> Optional<T> getValueForPrimaryZone(RegistryKey<World> dim, Function<Zone, Optional<T>> valueGetter) {
		return LockHelper.getThroughLock(
				worldZoneLock.readLock(),
				() -> worldZones.get(dim).stream().map(valueGetter).filter(
						Optional::isPresent
				).map(Optional::get).findFirst()
		);
	}

	public Collection<String> getZoneNames() {
		return zones.keySet();
	}

	public Collection<RealZone> getZones() {
		return zones.values();
	}

	public void updatePriority(RealZone zone) {
		worldZones.get(zone.getDim()).remove(zone);
		worldZones.get(zone.getDim()).add(zone);
	}

	public boolean containsZone(String name) {
		return zones.containsKey(name);
	}

	public NbtList writeNBT() {
		NbtList list = new NbtList();
		for (RealZone container : zones.values()) {
			list.add(container.toNBT());
		}
		return list;
	}

	public void readNBT(MinecraftServer server, RegistryWrapper.WrapperLookup lookup, NbtList nbt) {
		if (!nbt.isEmpty() && nbt.getHeldType() != NbtElement.COMPOUND_TYPE) {
			throw new IllegalStateException("NBT type of list must be Compound!");
		}
		for (NbtElement element : nbt) {
			RealZone.fromNBT(server, lookup, ((NbtCompound) element), markNeedsSave).ifPresent(this::addZoneInternal);
		}
	}

}
