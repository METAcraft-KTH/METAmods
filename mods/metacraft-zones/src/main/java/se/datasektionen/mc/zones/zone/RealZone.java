package se.datasektionen.mc.zones.zone;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
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
	public boolean isPosWithinZoneBoundsNoDimCheck(BlockPos pos) {
		return zone.contains(pos);
	}

	public boolean contains(RegistryKey<World> dim, BlockPos pos) {
		if (this.world.getRegistryKey() == dim) {
			return isPosWithinZoneBoundsNoDimCheck(pos);
		} else if (remoteDimensions.containsKey(dim)) {
			return remoteDimensions.get(dim).isPosWithinZoneBoundsNoDimCheck(pos);
		} else {
			return false;
		}
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

	public SerializedZone serialize() {
		return new SerializedZone(
				name, getDim(), remoteDimensions.keySet().stream().toList(), zone,
				zoneData.values().stream().toList(), priority
		);
	}

	public static Optional<RealZone> deserialize(
			MinecraftServer server, SerializedZone zone, Runnable markNeedsSave, boolean printDimensionErrors
	) {
		var name = zone.name;
		World world = server.getWorld(zone.dim);
		if (world == null) {
			if (printDimensionErrors) {
				METAcraftZones.LOGGER.error("Root dimension invalid, deleting zone " + name);
			}
			return Optional.empty();
		}
		Map<ZoneDataType<?>, ZoneData> dataTypes = new HashMap<>();
		for (var data : zone.data) {
			dataTypes.put(data.getType(), data);
		}

		RealZone container = new RealZone(
				name, world, zone.zone, dataTypes, zone.priority, markNeedsSave
		);

		for (var remoteDim : zone.remoteDims) {
			var remoteWorld = server.getWorld(remoteDim);
			if (remoteWorld != null) {
				container.remoteDimensions.put(remoteWorld.getRegistryKey(), new RemoteZone(remoteWorld, container));
			} else {
				container.remoteWorldsToCheck.add(remoteDim);
			}
		}
		return Optional.of(container);
	}

	public record SerializedZone(
			String name, RegistryKey<World> dim, List<RegistryKey<World>> remoteDims,
			ZoneType zone, List<ZoneData> data, int priority
	) {

		public static final Codec<SerializedZone> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.STRING.fieldOf("name").forGetter(SerializedZone::name),
						World.CODEC.fieldOf("dim").forGetter(SerializedZone::dim),
						World.CODEC.listOf().fieldOf("remote_dims").forGetter(SerializedZone::remoteDims),
						ZoneType.REGISTRY_CODEC.fieldOf("zone").mapResult(new MapCodec.ResultFunction<>() {
							@Override
							public <T> DataResult<ZoneType> apply(DynamicOps<T> ops, MapLike<T> input, DataResult<ZoneType> a) {
								if (!a.hasResultOrPartial()) {
									return a.mapError(
											e -> DataResult.appendMessages("Unable to parse zone type, setting zone type to empty", e)
									).setPartial(EmptyZone.INSTANCE);
								}
								return a;
							}

							@Override
							public <T> RecordBuilder<T> coApply(DynamicOps<T> ops, ZoneType input, RecordBuilder<T> t) {
								return t;
							}
						}).forGetter(SerializedZone::zone),
						ZoneData.REGISTRY_CODEC.listOf().fieldOf("data").forGetter(SerializedZone::data),
						Codec.INT.fieldOf("priority").forGetter(SerializedZone::priority)
				).apply(instance, SerializedZone::new)
		);
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

	public void fixDimensionLeukocyte() {
		if (IsLoaded.LEUKOCYTE.isLoaded()) {
			LeukocyteZoneManager.updateZoneDimensions(world.getServer(), this);
		}
	}

}
