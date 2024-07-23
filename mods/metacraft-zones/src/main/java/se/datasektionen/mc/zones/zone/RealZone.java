package se.datasektionen.mc.zones.zone;

import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.zones.METAcraftZones;
import se.datasektionen.mc.zones.ZoneManager;
import se.datasektionen.mc.zones.compat.leukocyte.LeukocyteZoneManager;
import se.datasektionen.mc.zones.zone.data.ZoneData;
import se.datasektionen.mc.zones.zone.data.ZoneDataRegistry;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;
import se.datasektionen.mc.zones.zone.types.EmptyZone;
import se.datasektionen.mc.zones.zone.types.ZoneType;

import java.util.*;

public class RealZone extends Zone {
	private static final String NAME = "name";
	private static final String DIM = "dim";
	private static final String REMOTE_DIMS = "remote_dims";
	private static final String ZONE = "zone";
	private static final String DATA = "data";
	private static final String PRIORITY = "priority";

	protected final World world;

	protected String name;
	protected ZoneType zone;
	protected final Map<RegistryKey<World>, Zone> remoteDimensions = new HashMap<>();
	private final List<RegistryKey<World>> remoteWorldsToCheck = new ArrayList<>();
	protected int priority;
	protected final Map<ZoneDataType<?>, ZoneData> zoneData;
	protected final Runnable markNeedsSave;

	public RealZone(String name, World world, ZoneType zone, Map<ZoneDataType<?>, ZoneData> zoneData, int priority, Runnable markNeedsSave) {
		this.name = name;
		this.world = world;
		this.zone = zone;
		zone.setZoneRef(this);
		this.zoneData = zoneData instanceof HashMap<ZoneDataType<?>, ZoneData> ? zoneData : new HashMap<>(zoneData);
		zoneData.values().forEach(data -> data.setZone(this));
		this.priority = priority;
		this.markNeedsSave = markNeedsSave;
	}

	@Override
	public boolean contains(BlockPos pos) {
		return zone.contains(pos);
	}

	public <T extends ZoneData> Optional<T> get(ZoneDataType<T> data) {
		return Optional.ofNullable((T) zoneData.get(data));
	}

	public Collection<ZoneData> getAllData() {
		return zoneData.values();
	}

	public <T extends ZoneData> T getOrCreate(ZoneDataType<T> data) {
		return get(data).orElseGet(() -> {
			if (ZoneDataRegistry.REGISTRY.getKey(data).isEmpty()) {
				throw new IllegalStateException("You need to register your zone data types!");
			}
			var newData = data.creator().get();
			newData.setZone(this);
			zoneData.put(data, newData);
			markNeedsSave.run();
			return (T) zoneData.get(data);
		});
	}

	public void removeZoneData(ZoneDataType<?> data) {
		zoneData.remove(data);
		markNeedsSave.run();
	}

	@Override
	protected Collection<ZoneData> getZoneDatas() {
		return zoneData.values();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public RegistryKey<World> getDim() {
		return world.getRegistryKey();
	}

	@Override
	public World getWorld() {
		return world;
	}

	@Override
	public void markDirty() {
		if (this.markNeedsSave != null) {
			this.markNeedsSave.run();
		}
	}

	@Override
	public ZoneType getZone() {
		return zone;
	}

	public void setZone(ZoneType zone) {
		zone.setZoneRef(this);
		this.zone = zone;
		markNeedsSave.run();
	}

	public void setPriority(int priority) {
		this.priority = priority;
		markNeedsSave.run();
	}

	@Override
	public int getPriority() {
		return priority;
	}

	public NbtCompound toNBT() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString(NAME, name);
		World.CODEC.encodeStart(NbtOps.INSTANCE, getDim()).resultOrPartial(
				METAcraftZones.LOGGER::error
		).ifPresent(dim -> {
			nbt.put(DIM, dim);
		});
		ZoneType.REGISTRY_CODEC.encodeStart(world.getRegistryManager().getOps(NbtOps.INSTANCE), zone).resultOrPartial(
				METAcraftZones.LOGGER::error
		).ifPresent(zone -> {
			nbt.put(ZONE, zone);
		});

		NbtList remoteDims = new NbtList();
		for (var dim : remoteDimensions.keySet()) {
			World.CODEC.encodeStart(NbtOps.INSTANCE, dim).resultOrPartial(
					METAcraftZones.LOGGER::error
			).ifPresent(remoteDims::add);
		}
		nbt.put(REMOTE_DIMS, remoteDims);

		NbtList data = new NbtList();
		zoneData.values().forEach(dataValue -> {
			ZoneData.REGISTRY_CODEC.encodeStart(
					world.getRegistryManager().getOps(NbtOps.INSTANCE), dataValue
			).resultOrPartial(METAcraftZones.LOGGER::error).ifPresent(data::add);
		});
		nbt.put(DATA, data);

		nbt.putInt(PRIORITY, priority);
		return nbt;
	}

	public static Optional<RealZone> fromNBT(
			MinecraftServer server, RegistryWrapper.WrapperLookup lookup, NbtCompound nbt, Runnable markNeedsSave
	) {
		return World.CODEC.parse(NbtOps.INSTANCE, nbt.get(DIM)).resultOrPartial(
				METAcraftZones.LOGGER::error
		).flatMap(dim -> {
			var name = nbt.getString(NAME);
			World world = server.getWorld(dim);
			if (world == null) {
				METAcraftZones.LOGGER.error("Root dimension invalid, deleting zone " + name);
				return Optional.empty();
			}
			var zone = ZoneType.REGISTRY_CODEC.parse(lookup.getOps(NbtOps.INSTANCE), nbt.get(ZONE)).resultOrPartial(
					METAcraftZones.LOGGER::error
			).orElseGet(() -> {
				METAcraftZones.LOGGER.error(
						"Unable to parse zone type for " + name + ", setting zone type to empty. " +
						"To make this zone work again, use /zone replace " + name + " <zone_type>"
				);
				return EmptyZone.INSTANCE;
			});
			Map<ZoneDataType<?>, ZoneData> dataTypes = new HashMap<>();
			NbtList data = nbt.getList(DATA, NbtElement.COMPOUND_TYPE);
			for (NbtElement element : data) {
				ZoneData.REGISTRY_CODEC.parse(
						lookup.getOps(NbtOps.INSTANCE), element
				).resultOrPartial(METAcraftZones.LOGGER::error).ifPresent(dataValue -> {
					dataTypes.put(dataValue.getType(), dataValue);
				});
			}

			RealZone container = new RealZone(
					name, world, zone, dataTypes, nbt.getInt(PRIORITY), markNeedsSave
			);

			NbtList remoteDims = nbt.getList(REMOTE_DIMS, NbtElement.STRING_TYPE);
			for (NbtElement element : remoteDims) {
				World.CODEC.parse(NbtOps.INSTANCE, element).resultOrPartial(
						METAcraftZones.LOGGER::error
				).map(
						otherDim -> {
							var remoteWorld = server.getWorld(otherDim);
							if (remoteWorld != null) {
								return Either.<World, RegistryKey<World>>left(remoteWorld);
							} else {
								return Either.<World, RegistryKey<World>>right(otherDim);
							}
						}
				).ifPresent(otherDim -> {
					otherDim.ifLeft(otherWorld -> {
						container.remoteDimensions.put(otherWorld.getRegistryKey(), new RemoteZone(otherWorld, container));
					});
					otherDim.ifRight(container.remoteWorldsToCheck::add);
				});
			}
			return Optional.of(container);
		});
	}

	public void onWorldLoad() {
		MinecraftServer server = getWorld().getServer();
		for (var dim : remoteWorldsToCheck) {
			var world = server.getWorld(dim);
			if (world != null) {
				addRemoteDimensionInternal(world);
			}
		}
		remoteWorldsToCheck.clear();
	}

	@Override
	public boolean isRealZone() {
		return true;
	}

	@Override
	public RealZone getRealZone() {
		return this;
	}

	public boolean hasRemoteZone(RegistryKey<World> dim) {
		return remoteDimensions.containsKey(dim);
	}

	public Collection<Zone> getRemoteZones() {
		return remoteDimensions.values();
	}

	public Zone getRemoteDimension(RegistryKey<World> dim) {
		return remoteDimensions.get(dim);
	}

	public void addRemoteDimension(World remoteDim) {
		if (remoteDim.getRegistryKey() != getDim()) {
			addRemoteDimensionInternal(remoteDim);
			markDirty();
		}
	}

	private void addRemoteDimensionInternal(World remoteDim) {
		var newContainer = new RemoteZone(remoteDim, this);
		if (world.getServer() != null) {
			ZoneManager.getInstance(world.getServer()).addZone(newContainer);
		}
		remoteDimensions.put(remoteDim.getRegistryKey(), newContainer);
		fixDimensionLeukocyte();
	}

	public void removeRemoteDimension(World remoteDim) {
		if (world.getServer() != null) {
			ZoneManager.getInstance(world.getServer()).removeZone(remoteDimensions.remove(remoteDim.getRegistryKey()));
			fixDimensionLeukocyte();
			markDirty();
		}
	}

	private void fixDimensionLeukocyte() {
		if (IsLoaded.LEUKOCYTE.isLoaded()) {
			LeukocyteZoneManager.updateZoneDimensions(world.getServer(), this);
		}
	}

}
