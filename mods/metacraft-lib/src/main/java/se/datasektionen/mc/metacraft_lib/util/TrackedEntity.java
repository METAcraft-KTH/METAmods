package se.datasektionen.mc.metacraft_lib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.Optional;
import java.util.UUID;

public class TrackedEntity<T extends Entity> {

	public static final Codec<TrackedEntity<Entity>> ENTITY_CODEC = getCodec(Entity.class);

	public static <T extends Entity> Codec<TrackedEntity<T>> getCodec(
			Class<T> entityClass
	) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						Uuids.CODEC.fieldOf("uuid").forGetter(p -> p.uuid),
						World.CODEC.fieldOf("dim").forGetter(TrackedEntity::getDim),
						ChunkPos.CODEC.fieldOf("pos").forGetter(TrackedEntity::getPos)
				).apply(instance, (uuid, dim, pos) -> new TrackedEntity<>(
						entityClass, uuid, dim, pos
				))
		);
	}

	private final TypeFilter<Entity, T> filter;
	private final UUID uuid;
	private RegistryKey<World> dim;
	private ChunkPos pos;
	private T entity;

	public TrackedEntity(Class<T> entityClass, UUID id, RegistryKey<World> dim, ChunkPos pos) {
		this.filter = TypeFilter.instanceOf(entityClass);
		this.uuid = id;
		this.dim = dim;
		this.pos = pos;
	}

	public TrackedEntity(T entity) {
		this(
				(Class<T>) entity.getClass(),
				entity.getUuid(),
				entity.getWorld().getRegistryKey(),
				entity.getChunkPos()
		);
		this.entity = entity;
	}

	public static <T extends Entity> TrackedEntity<T> of(T entity) {
		return new TrackedEntity<>(entity);
	}

	private RegistryKey<World> getDim() {
		return entity != null ? entity.getWorld().getRegistryKey() : dim;
	}

	private ChunkPos getPos() {
		return entity != null ? entity.getChunkPos() : pos;
	}

	public EntityResult<T> getEntity(MinecraftServer server) {
		if (entity == null || entity.isRemoved()) {
			var world = server.getWorld(dim);
			if (world == null) {
				return EntityResult.from(null, EntityResult.EntityState.CHUNK_NOT_LOADED);
			}
			entity = filter.downcast(world.getEntity(uuid));
			if (entity == null) {
				if (!world.isChunkLoaded(pos.toLong())) {
					world.getChunk(pos.x, pos.z, ChunkStatus.FULL);
					return EntityResult.from(null, EntityResult.EntityState.CHUNK_NOT_LOADED);
				}
			}
		}
		return EntityResult.from(entity, entity != null ? EntityResult.EntityState.PRESENT : EntityResult.EntityState.ABSENT);
	}

	public void tick() {
		if (entity != null) {
			pos = entity.getChunkPos();
			dim = entity.getWorld().getRegistryKey();

			if (entity.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION) {
				for (var dim : entity.getServer().getWorlds()) {
					entity = filter.downcast(dim.getEntity(uuid));
					if (entity != null) {
						break;
					}
				}
			}
		}
	}

	public record EntityResult<T extends Entity>(
			T entity, EntityState entityState
	) {
		public static <T extends Entity> EntityResult<T> from(
				T entity, EntityState entityState
		) {
			return new EntityResult<>(entity, entityState);
		}

		public Optional<T> optional() {
			return Optional.ofNullable(entity);
		}

		public boolean isPresent() {
			return entity != null;
		}

		public enum EntityState {
			CHUNK_NOT_LOADED,
			ABSENT,
			PRESENT
		}
	}

}
