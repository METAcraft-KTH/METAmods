package nu.metacraft.zones.zone;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.zones.METAcraftZones;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.compat.leukocyte.LeukocyteZoneManager;
import nu.metacraft.zones.zone.data.ZoneData;
import nu.metacraft.zones.zone.data.ZoneDataRegistry;
import nu.metacraft.zones.zone.data.ZoneDataType;
import nu.metacraft.zones.zone.types.EmptyZone;
import nu.metacraft.zones.zone.types.ZoneType;

import java.util.*;

public class RealZone extends Zone {
	private static final String NAME = "name";
	private static final String DIM = "dim";
	private static final String REMOTE_DIMS = "remote_dims";
	private static final String ZONE = "zone";
	private static final String DATA = "data";
	private static final String PRIORITY = "priority";

	protected final Level world;

	protected String name;
	protected ZoneType zone;
	protected final Map<ResourceKey<Level>, Zone> remoteDimensions = new HashMap<>();
	private final List<ResourceKey<Level>> remoteWorldsToCheck = new ArrayList<>();
	protected int priority;
	protected final Map<ZoneDataType<?>, ZoneData> zoneData;
	protected final Runnable markNeedsSave;

	public RealZone(String name, Level world, ZoneType zone, Map<ZoneDataType<?>, ZoneData> zoneData, int priority, Runnable markNeedsSave) {
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

	public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
		if (this.world.dimension() == dim) {
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
			if (ZoneDataRegistry.REGISTRY.getResourceKey(data).isEmpty()) {
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
	public ResourceKey<Level> getDim() {
		return world.dimension();
	}

	@Override
	public Level getWorld() {
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
		Level world = server.getLevel(zone.dim);
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
			var remoteWorld = server.getLevel(remoteDim);
			if (remoteWorld != null) {
				container.remoteDimensions.put(remoteWorld.dimension(), new RemoteZone(remoteWorld, container));
			} else {
				container.remoteWorldsToCheck.add(remoteDim);
			}
		}
		return Optional.of(container);
	}

	public record SerializedZone(
			String name, ResourceKey<Level> dim, List<ResourceKey<Level>> remoteDims,
			ZoneType zone, List<ZoneData> data, int priority
	) {

		public static final Codec<SerializedZone> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.STRING.fieldOf("name").forGetter(SerializedZone::name),
						Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(SerializedZone::dim),
						Level.RESOURCE_KEY_CODEC.listOf().fieldOf("remote_dims").forGetter(SerializedZone::remoteDims),
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
			var world = server.getLevel(dim);
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

	public boolean hasRemoteZone(ResourceKey<Level> dim) {
		return remoteDimensions.containsKey(dim);
	}

	public Collection<Zone> getRemoteZones() {
		return remoteDimensions.values();
	}

	public Zone getRemoteDimension(ResourceKey<Level> dim) {
		return remoteDimensions.get(dim);
	}

	public void addRemoteDimension(Level remoteDim) {
		if (remoteDim.dimension() != getDim()) {
			addRemoteDimensionInternal(remoteDim);
			markDirty();
		}
	}

	private void addRemoteDimensionInternal(Level remoteDim) {
		var newContainer = new RemoteZone(remoteDim, this);
		if (world.getServer() != null) {
			ZoneManager.getInstance(world.getServer()).addZone(newContainer);
		}
		remoteDimensions.put(remoteDim.dimension(), newContainer);
		fixDimensionLeukocyte();
	}

	public void removeRemoteDimension(Level remoteDim) {
		if (world.getServer() != null) {
			ZoneManager.getInstance(world.getServer()).removeZone(remoteDimensions.remove(remoteDim.dimension()));
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
