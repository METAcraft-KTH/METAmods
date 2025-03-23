package se.datasektionen.mc.zones.zone;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.TreePSet;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.metacraft_lib.util.helper.ThreadHelper;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ZoneMap {

	protected final AtomicReference<PMap<RegistryKey<World>, TreePSet<Zone>>> worldZones = new AtomicReference<>(HashTreePMap.empty());
	protected final AtomicReference<PMap<String, RealZone>> zones = new AtomicReference<>(HashTreePMap.empty());

	protected final Runnable markNeedsSave;
	protected final Consumer<Zone> onAdd;
	protected final Consumer<Zone> onRemove;

	private final List<Runnable> leukocyteFixes = new ArrayList<>();

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

	private TreePSet<Zone> getWorldZone(RegistryKey<World> dim) {
		return worldZones.get().getOrDefault(dim, TreePSet.empty());
	}

	private void addWorldZone(Zone zone) {
		var dim = zone.getDim();
		ThreadHelper.updateAtomic(worldZones, () -> {
			var existing = getWorldZone(dim);
			if (existing == null) {
				return worldZones.get().plus(dim, TreePSet.singleton(zone));
			} else {
				return worldZones.get().plus(dim, existing.plus(zone));
			}
		});
	}

	private void addZoneInternal(Zone zone) {
		if (zone.isRealZone()) {
			ThreadHelper.updateAtomic(zones, () -> {
				return zones.get().plus(zone.getName(), zone.getRealZone());
			});
		}
		addWorldZone(zone);
		if (zone.isRealZone()) {
			for (Zone remoteZone : zone.getRealZone().getRemoteZones()) {
				addWorldZone(remoteZone);
			}
		}
	}

	public boolean removeZone(String name) {
		if (!zones.get().containsKey(name)) return false;
		removeZone(zones.get().get(name));
		return true;
	}

	private void removeWorldZone(Zone zone) {
		var dim = zone.getDim();
		ThreadHelper.updateAtomic(worldZones, () -> {
			var result = getWorldZone(dim).minus(zone);
			if (result.isEmpty()) {
				return worldZones.get().minus(dim);
			} else {
				return worldZones.get().plus(dim, result);
			}
		});
	}

	public void removeZone(Zone zone) {
		if (zone.isRealZone()) {
			ThreadHelper.updateAtomic(zones, () -> {
				return zones.get().minus(zone.getName());
			});
			onRemove.accept(zone);
			markNeedsSave.run();
		}
		removeWorldZone(zone);
		if (zone.isRealZone()) {
			for (Zone remoteZone : zone.getRealZone().getRemoteZones()) {
				removeWorldZone(remoteZone);
			}
		}
	}

	public RealZone getZone(String name) {
		return zones.get().get(name);
	}

	public void forZones(RegistryKey<World> dim, Consumer<Zone> run) {
		getWorldZone(dim).forEach(run);
	}

	public List<Zone> getZones(RegistryKey<World> dim, Predicate<Zone> zonePredicate) {
		return getWorldZone(dim).stream().filter(zonePredicate).toList();
	}

	public Optional<Zone> getFirstZoneMatching(RegistryKey<World> dim, Predicate<Zone> zonePredicate) {
		return getWorldZone(dim).stream().filter(zonePredicate).findFirst();
	}

	public <T> Optional<T> getValueForPrimaryZone(RegistryKey<World> dim, Function<Zone, Optional<T>> valueGetter) {
		return getWorldZone(dim).stream().map(valueGetter).filter(
				Optional::isPresent
		).map(Optional::get).findFirst();
	}

	public Collection<String> getZoneNames() {
		return zones.get().keySet();
	}

	public Collection<RealZone> getZones() {
		return zones.get().values();
	}

	public void updatePriority(RealZone zone) {
		ThreadHelper.updateAtomic(worldZones, () -> {
			var result = getWorldZone(zone.getDim()).minus(zone).plus(zone);
			return worldZones.get().plus(zone.getDim(), result);
		});
	}

	public boolean containsZone(String name) {
		return zones.get().containsKey(name);
	}

	public List<RealZone.SerializedZone> serialize() {
		return zones.get().values().stream().map(RealZone::serialize).toList();
	}

	public void deserialize(MinecraftServer server, List<RealZone.SerializedZone> zones) {
		for (var zone : zones) {
			RealZone.deserialize(server, zone, markNeedsSave, !IsLoaded.LEUKOCYTE.isLoaded()).ifPresentOrElse(
					this::addZoneInternal, () -> {
						if (IsLoaded.LEUKOCYTE.isLoaded()) {
							leukocyteFixes.add(() -> {
								RealZone.deserialize(server, zone, markNeedsSave, true).ifPresent(z -> {
									this.addZoneInternal(z);
									z.fixDimensionLeukocyte();
								});
							});
						}
					}
			);
		}
	}

	public void fixLeukocyteLoading() {
		leukocyteFixes.forEach(Runnable::run);
		leukocyteFixes.clear();
	}

}
