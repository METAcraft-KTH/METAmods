package nu.metacraft.cutscenes.cutscene.world;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.storage.TagValueOutput;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.mixin.PersistentEntitySectionManagerAccessor;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class CutsceneEntityManager {

	private final Multimap<String, EntityEntry> entities = HashMultimap.create();
	private final Map<UUID, String> idLookup = new HashMap<>();
	private final Set<UUID> removalSkips = new HashSet<>();

	private final List<Pair<String, Entity>> addQueue = new ArrayList<>();

	private final CutsceneWorld world;
	private final EntitySectionStorage<Entity> cache = new EntitySectionStorage<>(Entity.class, i -> Visibility.TICKING);
	private final EntityLookup<Entity> index = new EntityLookup<>();
	private final LevelEntityGetter<Entity> lookup = new LevelEntityGetterAdapter<>(index, cache);
	private CutsceneChunkLoadingManager chunkLoadingManager = null;
	private boolean iteratingEntities = false;

	private final Set<UUID> entitiesToHide = new HashSet<>();

	public CutsceneEntityManager(CutsceneWorld world) {
		this.world = world;
		this.chunkLoadingManager = world.getChunkSource().cutsceneChunkLoadingManager;
		if (chunkLoadingManager != null) {
			for (var e : entities.values()) {
				chunkLoadingManager.addEntity(e.entity, e.tracker);
			}
		}
	}

	void entityLeftSection(long sectionPos, EntitySection<Entity> section) {
		if (section.isEmpty()) {
			this.cache.remove(sectionPos);
		}
	}

	public void onAddPlayer(ServerPlayer player) {
		entities.values().forEach(entity -> {
			entity.tracker.addPairing(player);
		});
	}

	public void onRemovePlayer(ServerPlayer player) {
		entities.values().forEach(entity -> {
			entity.tracker.removePairing(player);
		});
	}

	public void addEntityToHide(UUID id) {
		this.entitiesToHide.add(id);
	}

	public boolean isHidden(UUID id) {
		return entitiesToHide.contains(id);
	}

	public static class CutsceneTrackerEntry extends ServerEntity {

		public CutsceneTrackerEntry(ServerLevel world, Entity entity) {
			super(
					world, entity, entity.getType().updateInterval(), entity.getType().trackDeltas(),
					new Synchronizer() {
						@Override
						public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
							if (entity instanceof PolymerEntity) {
								EntityAttachedPacket.setIfEmpty(packet, entity);
							}
							world.players().forEach(p -> p.connection.send(packet));
						}

						@Override
						public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
							this.sendToTrackingPlayers(packet);
						}

						@Override
						public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> predicate) {
							if (entity instanceof PolymerEntity) {
								EntityAttachedPacket.setIfEmpty(packet, entity);
							}
							world.players().forEach(p -> {
								if (predicate.test(p)) {
									p.connection.send(packet);
								}
							});
						}
					}
			);
		}
	}

	public static class NoOpTrackerEntry extends CutsceneTrackerEntry {

		public NoOpTrackerEntry(ServerLevel world, Entity entity) {
			super(world, entity);
		}

		@Override
		public void addPairing(ServerPlayer player) {

		}

		@Override
		public void removePairing(ServerPlayer player) {

		}

		@Override
		public void sendPairingData(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> sender) {

		}
	}

	private ServerEntity create(Entity entity) {
		if (entity.getType().clientTrackingRange() == 0) {
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
		var prev = idLookup.put(entity.getUUID(), id);
		if (prev != null) {
			removalSkips.add(entity.getUUID());
		}
		index.add(entity);
		world.players().forEach(tracker::addPairing);
		var pos = SectionPos.asLong(entity.blockPosition());
		var section = this.cache.getOrCreateSection(pos);
		section.add(entity);
		entity.setLevelCallback(new Listener(entity, pos, section));
	}

	public Optional<Entity> getRootEntity(String id) {
		return entities.get(id).stream().findFirst().map(EntityEntry::entity);
	}

	public Stream<Entity> getEntities(String id) {
		return entities.get(id).stream().map(EntityEntry::entity);
	}

	public Optional<String> getIDForEntity(Entity entity) {
		return Optional.ofNullable(idLookup.get(entity.getUUID()));
	}

	public LevelEntityGetter<Entity> getLookup() {
		return lookup;
	}

	public record SaveState(
			List<CutsceneWorldData.SerialisedEntity> entities,
			Set<UUID> entitiesToHide
	) {
		public static final MapCodec<SaveState> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						CutsceneWorldData.SerialisedEntity.CODEC.listOf().fieldOf("entities").forGetter(d -> d.entities),
						UUIDUtil.CODEC_SET.optionalFieldOf("entities_to_hide").xmap(
								opt -> opt.orElseGet(HashSet::new), Optional::of
						).forGetter(d -> d.entitiesToHide)
				).apply(instance, SaveState::new)
		);
	}

	public SaveState save() {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:CutsceneEntityManager#save", Cutscenes.LOGGER)) {
			return new SaveState(
					entities.entries().stream().filter(e -> e.getValue().entity.shouldBeSaved()).map(entry -> {
						List<String> ids = new ArrayList<>();
						ids.add(entry.getKey());
						if (entry.getValue().entity.isVehicle()) {
							entry.getValue().entity.getIndirectPassengers().forEach(passenger -> {
								getIDForEntity(passenger).ifPresent(ids::add);
							});
						}

						var writeView = TagValueOutput.createWithContext(logging, entry.getValue().entity.registryAccess());
						if (entry.getValue().entity.saveAsPassenger(writeView)) {
							return new CutsceneWorldData.SerialisedEntity(ids, writeView.buildResult());
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
			e.tracker.sendChanges();
			if (e.entity.isRemoved()) {
				onEntityRemove(e);
				return true;
			} else {
				var vehicle = e.entity.getVehicle();
				if (vehicle != null && (vehicle.isRemoved() || !vehicle.hasPassenger(e.entity))) {
					e.entity.stopRiding();
				}
				world.guardEntityTick(world::tickNonPassenger, e.entity);
				return false;
			}
		});
		iteratingEntities = false;
		addQueue.forEach(pair -> addEntity(pair.getFirst(), pair.getSecond()));
		addQueue.clear();
		removalSkips.clear();
	}

	private void onEntityRemove(EntityEntry entity) {
		world.players().forEach(entity.tracker::removePairing);
		index.remove(entity.entity);
		if (chunkLoadingManager != null) {
			chunkLoadingManager.removeEntity(entity.entity);
		}
		if (!removalSkips.contains(entity.entity.getUUID())) {
			idLookup.remove(entity.entity.getUUID());
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

	public record EntityEntry(Entity entity, ServerEntity tracker) {}

	public class Listener implements EntityInLevelCallback {

		private final Entity entity;
		private long sectionPos;
		private EntitySection<Entity> section;

		public Listener(Entity entity, long sectionPos, EntitySection<Entity> section) {
			this.entity = entity;
			this.sectionPos = sectionPos;
			this.section = section;
		}

		@Override
		public void onMove() {
			var newPos = SectionPos.asLong(entity.blockPosition());
			if (newPos != sectionPos) {
				section.remove(entity);
				entityLeftSection(sectionPos, section);
				sectionPos = newPos;
				section = cache.getOrCreateSection(sectionPos);
				section.add(entity);
			}
		}

		@Override
		public void onRemove(Entity.RemovalReason reason) {
			section.remove(entity);
			entity.setLevelCallback(EntityInLevelCallback.NULL);
			entityLeftSection(sectionPos, section);
		}
	}

	public Dummy createDummyEntityManager() {
		return new Dummy();
	}

	public class Dummy extends PersistentEntitySectionManager<Entity> {

		public Dummy() {
			super(Entity.class, new LevelCallback<>() {
				@Override
				public void onCreated(Entity entity) {

				}

				@Override
				public void onDestroyed(Entity entity) {

				}

				@Override
				public void onTickingStart(Entity entity) {

				}

				@Override
				public void onTickingEnd(Entity entity) {

				}

				@Override
				public void onTrackingStart(Entity entity) {

				}

				@Override
				public void onTrackingEnd(Entity entity) {

				}

				@Override
				public void onSectionChange(Entity entity) {

				}
			}, null);

			((PersistentEntitySectionManagerAccessor<Entity>) this).setSectionStorage(CutsceneEntityManager.this.cache);
			((PersistentEntitySectionManagerAccessor<Entity>) this).setVisibleEntityStorage(CutsceneEntityManager.this.index);
		}

		@Override
		public void tick() {

		}

		@Override
		public void updateChunkStatus(ChunkPos chunkPos, Visibility trackingStatus) {

		}

		@Override
		public void autoSave() {

		}

		@Override
		public void saveAll() {

		}

		@Override
		public void close() throws IOException {

		}

		@Override
		public LevelEntityGetter<Entity> getEntityGetter() {
			return lookup;
		}

		@Override
		public boolean isLoaded(UUID uuid) {
			return idLookup.containsKey(uuid);
		}

		@Override
		public boolean canPositionTick(BlockPos pos) {
			return true;
		}

		@Override
		public boolean canPositionTick(ChunkPos pos) {
			return true;
		}

		@Override
		public boolean areEntitiesLoaded(long chunkPos) {
			return true;
		}

	}

}
