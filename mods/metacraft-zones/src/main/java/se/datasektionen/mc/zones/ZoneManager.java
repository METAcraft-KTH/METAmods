package se.datasektionen.mc.zones;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.zones.compat.leukocyte.LeukocyteZoneManager;
import se.datasektionen.mc.zones.zone.RealZone;
import se.datasektionen.mc.zones.zone.Zone;
import se.datasektionen.mc.zones.zone.ZoneMap;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class ZoneManager extends PersistentState {

	private static boolean loading = false;

	public static ZoneManager getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
	}

	public static Optional<ZoneManager> getInstanceNoStackOverflow(MinecraftServer server) {
		if (loading) {
			return Optional.empty();
		}
		return Optional.of(getInstance(server));
	}

	private static final PersistentStateType<ZoneManager> TYPE = new PersistentStateType<>(
			"metacraft-zones", ctx -> createNew(ctx.getWorldOrThrow().getServer()),
			ctx -> createCodec(ctx.getWorldOrThrow().getServer()), null
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
	private final ZoneMap zones = new ZoneMap(this::markDirty, this::onZoneAdd, this::onZoneRemove);

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

	public List<Zone> getZonesMatching(RegistryKey<World> dim, Predicate<Zone> zonePredicate) {
		return zones.getZones(dim, zonePredicate);
	}

	public Optional<Zone> getFirstZoneMatching(RegistryKey<World> dim, Predicate<Zone> zonePredicate) {
		return zones.getFirstZoneMatching(dim, zonePredicate);
	}

	public Optional<Zone> getZoneAt(RegistryKey<World> dim, BlockPos pos, Predicate<Zone> zonePredicate) {
		return getFirstZoneMatching(dim, zone -> zone.isPosWithinZoneBoundsNoDimCheck(pos) && zonePredicate.test(zone));
	}

	public Optional<Zone> getZoneAt(RegistryKey<World> dim, BlockPos pos) {
		return getFirstZoneMatching(dim, zone -> zone.isPosWithinZoneBoundsNoDimCheck(pos));
	}

	public List<Zone> getZonesAt(RegistryKey<World> dim, BlockPos pos, Predicate<Zone> additionaLPredicate) {
		return getZonesMatching(dim, zone -> zone.isPosWithinZoneBoundsNoDimCheck(pos) && additionaLPredicate.test(zone));
	}

	public <T> Optional<T> getValueForPrimaryZone(RegistryKey<World> dim, BlockPos pos, Function<Zone, Optional<T>> valueGetter) {
		return getValueForPrimaryZone(dim, zone -> {
			if (!zone.isPosWithinZoneBoundsNoDimCheck(pos)) {
				return Optional.empty();
			}
			return valueGetter.apply(zone);
		});
	}

	public <T> Optional<T> getValueForPrimaryZone(RegistryKey<World> dim, Function<Zone, Optional<T>> valueGetter) {
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
