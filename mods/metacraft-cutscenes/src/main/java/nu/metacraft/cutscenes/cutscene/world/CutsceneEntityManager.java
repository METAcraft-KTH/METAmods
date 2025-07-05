package nu.metacraft.cutscenes.cutscene.world;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import net.minecraft.entity.Entity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.entity.*;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.mixin.AccesorServerEntityManager;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class CutsceneEntityManager {

	private final Multimap<String, EntityEntry> entities = HashMultimap.create();
	private final Map<UUID, String> idLookup = new HashMap<>();
	private final Set<UUID> removalSkips = new HashSet<>();

	private final List<Pair<String, Entity>> addQueue = new ArrayList<>();

	private final CutsceneWorld world;
	private final SectionedEntityCache<Entity> cache = new SectionedEntityCache<>(Entity.class, i -> EntityTrackingStatus.TICKING);
	private final EntityIndex<Entity> index = new EntityIndex<>();
	private final EntityLookup<Entity> lookup = new SimpleEntityLookup<>(index, cache);
	private CutsceneChunkLoadingManager chunkLoadingManager = null;
	private boolean iteratingEntities = false;

	private final Set<UUID> entitiesToHide = new HashSet<>();

	public CutsceneEntityManager(CutsceneWorld world) {
		this.world = world;
		this.chunkLoadingManager = world.getChunkManager().cutsceneChunkLoadingManager;
		if (chunkLoadingManager != null) {
			for (var e : entities.values()) {
				chunkLoadingManager.addEntity(e.entity, e.tracker);
			}
		}
	}

	void entityLeftSection(long sectionPos, EntityTrackingSection<Entity> section) {
		if (section.isEmpty()) {
			this.cache.removeSection(sectionPos);
		}
	}

	public void onAddPlayer(ServerPlayerEntity player) {
		entities.values().forEach(entity -> {
			entity.tracker.startTracking(player);
		});
	}

	public void onRemovePlayer(ServerPlayerEntity player) {
		entities.values().forEach(entity -> {
			entity.tracker.stopTracking(player);
		});
	}

	public void addEntityToHide(UUID id) {
		this.entitiesToHide.add(id);
	}

	public boolean isHidden(UUID id) {
		return entitiesToHide.contains(id);
	}

	public static class CutsceneTrackerEntry extends EntityTrackerEntry {

		public CutsceneTrackerEntry(ServerWorld world, Entity entity) {
			super(
					world, entity, entity.getType().getTrackTickInterval(), entity.getType().alwaysUpdateVelocity(),
					packet -> {
						if (entity instanceof PolymerEntity) {
							EntityAttachedPacket.setIfEmpty(packet, entity);
						}
						world.getPlayers().forEach(p -> p.networkHandler.sendPacket(packet));
					},
					(packet, skip) -> {
						if (entity instanceof PolymerEntity) {
							EntityAttachedPacket.setIfEmpty(packet, entity);
						}
						world.getPlayers().forEach(p -> {
							if (!skip.contains(p.getUuid())) {
								p.networkHandler.sendPacket(packet);
							}
						});
					}
			);
		}
	}

	public static class NoOpTrackerEntry extends CutsceneTrackerEntry {

		public NoOpTrackerEntry(ServerWorld world, Entity entity) {
			super(world, entity);
		}

		@Override
		public void startTracking(ServerPlayerEntity player) {

		}

		@Override
		public void stopTracking(ServerPlayerEntity player) {

		}

		@Override
		public void sendPackets(ServerPlayerEntity player, Consumer<Packet<ClientPlayPacketListener>> sender) {

		}
	}

	private EntityTrackerEntry create(Entity entity) {
		if (entity.getType().getMaxTrackDistance() == 0) {
			return new NoOpTrackerEntry(world, entity);
		}
		return new CutsceneTrackerEntry(world, entity);
	}

	public void addEntity(String id, Entity entity) {
		if (iteratingEntities) {
			addQueue.add(Pair.of(id, entity));
			return;
		}
		var tracker = create(entity);
		if (chunkLoadingManager != null) {
			chunkLoadingManager.addEntity(entity, tracker);
		}
		entities.put(id, new EntityEntry(entity, tracker));
		var prev = idLookup.put(entity.getUuid(), id);
		if (prev != null) {
			removalSkips.add(entity.getUuid());
		}
		index.add(entity);
		world.getPlayers().forEach(tracker::startTracking);
		var pos = ChunkSectionPos.toLong(entity.getBlockPos());
		var section = this.cache.getTrackingSection(pos);
		section.add(entity);
		entity.setChangeListener(new Listener(entity, pos, section));
	}

	public Optional<Entity> getRootEntity(String id) {
		return entities.get(id).stream().findFirst().map(EntityEntry::entity);
	}

	public Stream<Entity> getEntities(String id) {
		return entities.get(id).stream().map(EntityEntry::entity);
	}

	public Optional<String> getIDForEntity(Entity entity) {
		return Optional.ofNullable(idLookup.get(entity.getUuid()));
	}

	public EntityLookup<Entity> getLookup() {
		return lookup;
	}

	public record SaveState(
			List<CutsceneWorldData.SerialisedEntity> entities,
			Set<UUID> entitiesToHide
	) {
		public static final MapCodec<SaveState> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						CutsceneWorldData.SerialisedEntity.CODEC.listOf().fieldOf("entities").forGetter(d -> d.entities),
						Uuids.SET_CODEC.optionalFieldOf("entities_to_hide").xmap(
								opt -> opt.orElseGet(HashSet::new), Optional::of
						).forGetter(d -> d.entitiesToHide)
				).apply(instance, SaveState::new)
		);
	}

	public SaveState save() {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:CutsceneEntityManager#save", Cutscenes.LOGGER)) {
			return new SaveState(
					entities.entries().stream().filter(e -> e.getValue().entity.shouldSave()).map(entry -> {
						List<String> ids = new ArrayList<>();
						ids.add(entry.getKey());
						if (entry.getValue().entity.hasPassengers()) {
							entry.getValue().entity.getPassengersDeep().forEach(passenger -> {
								getIDForEntity(passenger).ifPresent(ids::add);
							});
						}

						var writeView = NbtWriteView.create(logging, entry.getValue().entity.getRegistryManager());
						if (entry.getValue().entity.saveSelfData(writeView)) {
							return new CutsceneWorldData.SerialisedEntity(ids, writeView.getNbt());
						} else {
							return null;
						}
					}).filter(Objects::nonNull).toList(),
					entitiesToHide
			);
		}
	}

	public void tick() {
		iteratingEntities = true;
		entities.values().removeIf(e -> {
			e.tracker.tick();
			if (e.entity.isRemoved()) {
				onEntityRemove(e);
				return true;
			} else {
				var vehicle = e.entity.getVehicle();
				if (vehicle != null && (vehicle.isRemoved() || !vehicle.hasPassenger(e.entity))) {
					e.entity.stopRiding();
				}
				world.tickEntity(world::tickEntity, e.entity);
				return false;
			}
		});
		iteratingEntities = false;
		addQueue.forEach(pair -> addEntity(pair.getFirst(), pair.getSecond()));
		addQueue.clear();
		removalSkips.clear();
	}

	private void onEntityRemove(EntityEntry entity) {
		world.getPlayers().forEach(entity.tracker::stopTracking);
		index.remove(entity.entity);
		if (chunkLoadingManager != null) {
			chunkLoadingManager.removeEntity(entity.entity);
		}
		if (!removalSkips.contains(entity.entity.getUuid())) {
			idLookup.remove(entity.entity.getUuid());
		}
		world.onEntityRemoved(entity.entity);
	}

	public void clear() {
		iteratingEntities = true;
		entities.forEach((id, e) -> {
			e.entity.discard();
			onEntityRemove(e);
		});
		iteratingEntities = false;
		entities.clear();
		addQueue.clear();
		idLookup.clear();
	}

	public record EntityEntry(Entity entity, EntityTrackerEntry tracker) {}

	public class Listener implements EntityChangeListener {

		private final Entity entity;
		private long sectionPos;
		private EntityTrackingSection<Entity> section;

		public Listener(Entity entity, long sectionPos, EntityTrackingSection<Entity> section) {
			this.entity = entity;
			this.sectionPos = sectionPos;
			this.section = section;
		}

		@Override
		public void updateEntityPosition() {
			var newPos = ChunkSectionPos.toLong(entity.getBlockPos());
			if (newPos != sectionPos) {
				section.remove(entity);
				entityLeftSection(sectionPos, section);
				sectionPos = newPos;
				section = cache.getTrackingSection(sectionPos);
				section.add(entity);
			}
		}

		@Override
		public void remove(Entity.RemovalReason reason) {
			section.remove(entity);
			entity.setChangeListener(EntityChangeListener.NONE);
			entityLeftSection(sectionPos, section);
		}
	}

	public Dummy createDummyEntityManager() {
		return new Dummy();
	}

	public class Dummy extends ServerEntityManager<Entity> {

		public Dummy() {
			super(Entity.class, new EntityHandler<>() {
				@Override
				public void create(Entity entity) {

				}

				@Override
				public void destroy(Entity entity) {

				}

				@Override
				public void startTicking(Entity entity) {

				}

				@Override
				public void stopTicking(Entity entity) {

				}

				@Override
				public void startTracking(Entity entity) {

				}

				@Override
				public void stopTracking(Entity entity) {

				}

				@Override
				public void updateLoadStatus(Entity entity) {

				}
			}, null);

			((AccesorServerEntityManager<Entity>) this).setCache(CutsceneEntityManager.this.cache);
			((AccesorServerEntityManager<Entity>) this).setIndex(CutsceneEntityManager.this.index);
		}

		@Override
		public void tick() {

		}

		@Override
		public void updateTrackingStatus(ChunkPos chunkPos, EntityTrackingStatus trackingStatus) {

		}

		@Override
		public void save() {

		}

		@Override
		public void flush() {

		}

		@Override
		public void close() throws IOException {

		}

		@Override
		public EntityLookup<Entity> getLookup() {
			return lookup;
		}

		@Override
		public boolean has(UUID uuid) {
			return idLookup.containsKey(uuid);
		}

		@Override
		public boolean shouldTick(BlockPos pos) {
			return true;
		}

		@Override
		public boolean shouldTick(ChunkPos pos) {
			return true;
		}

		@Override
		public boolean isLoaded(long chunkPos) {
			return true;
		}

	}

}
