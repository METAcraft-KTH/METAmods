package se.datasektionen.mc.cutscenes.cutscene;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.entity.*;

import java.util.*;
import java.util.stream.Stream;

public class CutsceneEntityManager {

	private final Multimap<String, EntityEntry> entities = HashMultimap.create();
	private final Map<UUID, String> idLookup = new HashMap<>();
	private final Set<UUID> removalSkips = new HashSet<>();

	private final List<Pair<String, Entity>> addQueue = new ArrayList<>();

	private ServerWorld world;
	private final Set<ServerPlayerEntity> players = new HashSet<>();
	private final SectionedEntityCache<Entity> cache = new SectionedEntityCache<>(Entity.class, i -> EntityTrackingStatus.TICKING);
	private final EntityIndex<Entity> index = new EntityIndex<>();
	private final EntityLookup<Entity> lookup = new SimpleEntityLookup<>(index, cache);
	private CutsceneWorld.CutsceneChunkLoadingManager chunkLoadingManager = null;
	private boolean iteratingEntities = false;

	public CutsceneEntityManager() {

	}

	void entityLeftSection(long sectionPos, EntityTrackingSection<Entity> section) {
		if (section.isEmpty()) {
			this.cache.removeSection(sectionPos);
		}
	}

	public void setWorld(CutsceneWorld world) {
		this.world = world;
		this.chunkLoadingManager = world.getChunkManager().cutsceneChunkLoadingManager;
		if (chunkLoadingManager != null) {
			for (var e : entities.values()) {
				chunkLoadingManager.addEntity(e.entity, e.tracker);
			}
		}
	}

	public void addPlayer(ServerPlayerEntity player) {
		players.add(player);
		entities.values().forEach(entity -> {
			entity.tracker.startTracking(player);
		});
	}

	public void removePlayer(ServerPlayerEntity player) {
		players.remove(player);
		entities.values().forEach(entity -> {
			entity.tracker.stopTracking(player);
		});
	}

	public void addEntity(String id, Entity entity) {
		if (iteratingEntities) {
			addQueue.add(Pair.of(id, entity));
			return;
		}
		var tracker = new EntityTrackerEntry(world, entity, 1, false, packet -> players.forEach(p -> p.networkHandler.sendPacket(packet)));
		if (chunkLoadingManager != null) {
			chunkLoadingManager.addEntity(entity, tracker);
		}
		entities.put(id, new EntityEntry(entity, tracker));
		var prev = idLookup.put(entity.getUuid(), id);
		if (prev != null) {
			removalSkips.add(entity.getUuid());
		}
		index.add(entity);
		players.forEach(tracker::startTracking);
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

	public Stream<CutsceneInstance.CutsceneWorldData.SerialisedEntity> save() {
		return entities.entries().stream().filter(e -> e.getValue().entity.shouldSave()).map(entry -> {
			List<String> ids = new ArrayList<>();
			ids.add(entry.getKey());
			if (entry.getValue().entity.hasPassengers()) {
				entry.getValue().entity.getPassengersDeep().forEach(passenger -> {
					getIDForEntity(passenger).ifPresent(ids::add);
				});
			}

			var nbt = new NbtCompound();
			if (entry.getValue().entity.saveSelfNbt(nbt)) {
				return new CutsceneInstance.CutsceneWorldData.SerialisedEntity(ids, nbt);
			} else {
				return null;
			}
		}).filter(Objects::nonNull);
	}

	public void tick() {
		iteratingEntities = true;
		entities.values().removeIf(e -> {
			e.tracker.tick();
			e.entity.tick();
			if (e.entity.isRemoved()) {
				onEntityRemove(e);
				return true;
			} else {
				return false;
			}
		});
		iteratingEntities = false;
		addQueue.forEach(pair -> addEntity(pair.getFirst(), pair.getSecond()));
		addQueue.clear();
		removalSkips.clear();
	}

	private void onEntityRemove(EntityEntry entity) {
		players.forEach(entity.tracker::stopTracking);
		index.remove(entity.entity);
		if (chunkLoadingManager != null) {
			chunkLoadingManager.removeEntity(entity.entity);
		}
		if (!removalSkips.contains(entity.entity.getUuid())) {
			idLookup.remove(entity.entity.getUuid());
		}
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

}
