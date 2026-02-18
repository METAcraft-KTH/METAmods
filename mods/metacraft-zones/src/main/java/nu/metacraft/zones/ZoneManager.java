package nu.metacraft.zones;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.lib.util.SavedDataTypeCache;
import nu.metacraft.zones.compat.leukocyte.LeukocyteZoneManager;
import nu.metacraft.zones.zone.RealZone;
import nu.metacraft.zones.zone.Zone;
import nu.metacraft.zones.zone.ZoneMap;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class ZoneManager extends SavedData {

	private static boolean loading = false;

	public static ZoneManager getInstance(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(SavedDataTypeCache.get(server, TYPE));
	}

	public static Optional<ZoneManager> getInstanceNoStackOverflow(MinecraftServer server) {
		if (loading) {
			return Optional.empty();
		}
		return Optional.of(getInstance(server));
	}

	//FIXME Datafixer
	private static final SavedDataTypeCache.Type<ZoneManager> TYPE = new SavedDataTypeCache.Type<>(
			level -> new SavedDataType<>(
					METAcraftZones.getID("zones"), () -> createNew(level.getServer()),
					createCodec(level.getServer()), null
			)
	);

	private static Codec<ZoneManager> createCodec(MinecraftServer server) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						RealZone.SerializedZone.CODEC.listOf().fieldOf("zones").forGetter(t -> t.zones.serialize())
				).apply(instance, (zones) -> ZoneManager.fromData(server, zones))
		);
	}

	private static ZoneManager createNew(MinecraftServer server) {
		METAcraftZones.LOGGER.info("No previous state found, setting default values");
		return new ZoneManager(server);
	}

	private static ZoneManager fromData(MinecraftServer server, List<RealZone.SerializedZone> zones) {
		loading = true;
		METAcraftZones.LOGGER.info("Previous state found, loading values");
		ZoneManager settings = new ZoneManager(server);
		settings.zones.deserialize(server, zones);
		loading = false;
		return settings;
	}

	protected final MinecraftServer server;

	protected ZoneManager(MinecraftServer server) {
		this.server = server;
	}
	private final ZoneMap zones = new ZoneMap(this::setDirty, this::onZoneAdd, this::onZoneRemove);

	protected void onZoneAdd(Zone zone) {
		if (IsLoaded.LEUKOCYTE.isLoaded()) {
			LeukocyteZoneManager.onZoneAdd(server, zone);
		}
	}

	protected void onZoneRemove(Zone zone) {
		if (IsLoaded.LEUKOCYTE.isLoaded()) {
			LeukocyteZoneManager.onZoneRemove(server, zone);
		}
	}

	public List<Zone> getZonesMatching(ResourceKey<Level> dim, Predicate<Zone> zonePredicate) {
		return zones.getZones(dim, zonePredicate);
	}

	public Optional<Zone> getFirstZoneMatching(ResourceKey<Level> dim, Predicate<Zone> zonePredicate) {
		return zones.getFirstZoneMatching(dim, zonePredicate);
	}

	public Optional<Zone> getZoneAt(ResourceKey<Level> dim, BlockPos pos, Predicate<Zone> zonePredicate) {
		return getFirstZoneMatching(dim, zone -> zone.isPosWithinZoneBoundsNoDimCheck(pos) && zonePredicate.test(zone));
	}

	public Optional<Zone> getZoneAt(ResourceKey<Level> dim, BlockPos pos) {
		return getFirstZoneMatching(dim, zone -> zone.isPosWithinZoneBoundsNoDimCheck(pos));
	}

	public List<Zone> getZonesAt(ResourceKey<Level> dim, BlockPos pos, Predicate<Zone> additionaLPredicate) {
		return getZonesMatching(dim, zone -> zone.isPosWithinZoneBoundsNoDimCheck(pos) && additionaLPredicate.test(zone));
	}

	public <T> Optional<T> getValueForPrimaryZone(ResourceKey<Level> dim, BlockPos pos, Function<Zone, Optional<T>> valueGetter) {
		return getValueForPrimaryZone(dim, zone -> {
			if (!zone.isPosWithinZoneBoundsNoDimCheck(pos)) {
				return Optional.empty();
			}
			return valueGetter.apply(zone);
		});
	}

	public <T> Optional<T> getValueForPrimaryZone(ResourceKey<Level> dim, Function<Zone, Optional<T>> valueGetter) {
		return zones.getValueForPrimaryZone(dim, valueGetter);
	}

	public ZoneMap getZones() {
		return zones;
	}

	public void addZone(Zone zone) {
		zones.addZone(zone);
	}

	public void removeZone(Zone zone) {
		zones.removeZone(zone);
	}

	public boolean removeZone(String zone) {
		return zones.removeZone(zone);
	}
	
	public RealZone getZone(String name) {
		return zones.getZone(name);
	}

	public void updatePriority(RealZone zone) {
		zones.updatePriority(zone);
	}

	public Collection<String> getZoneNames() {
		return zones.getZoneNames();
	}

	public boolean containsZone(String name) {
		return zones.containsZone(name);
	}


	public void fixLeukocyteLoading() {
		zones.fixLeukocyteLoading();
	}

}
