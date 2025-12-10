package nu.metacraft.lib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import java.util.Optional;
import java.util.UUID;

public class TrackedEntity<T extends Entity> {

	public static final Codec<TrackedEntity<Entity>> ENTITY_CODEC = getCodec(Entity.class);

	public static <T extends Entity> Codec<TrackedEntity<T>> getCodec(
			Class<T> entityClass
	) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						UUIDUtil.AUTHLIB_CODEC.fieldOf("uuid").forGetter(p -> p.uuid),
						Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(TrackedEntity::getDim),
						ChunkPos.CODEC.fieldOf("pos").forGetter(TrackedEntity::getPos)
				).apply(instance, (uuid, dim, pos) -> new TrackedEntity<>(
						entityClass, uuid, dim, pos
				))
		);
	}

	private final EntityTypeTest<Entity, T> filter;
	private final UUID uuid;
	private ResourceKey<Level> dim;
	private ChunkPos pos;
	private T entity;

	public TrackedEntity(Class<T> entityClass, UUID id, ResourceKey<Level> dim, ChunkPos pos) {
		this.filter = EntityTypeTest.forClass(entityClass);
		this.uuid = id;
		this.dim = dim;
		this.pos = pos;
	}

	public TrackedEntity(T entity) {
		this(
				(Class<T>) entity.getClass(),
				entity.getUUID(),
				entity.level().dimension(),
				entity.chunkPosition()
		);
		this.entity = entity;
	}

	public static <T extends Entity> TrackedEntity<T> of(T entity) {
		return new TrackedEntity<>(entity);
	}

	private ResourceKey<Level> getDim() {
		return entity != null ? entity.level().dimension() : dim;
	}

	private ChunkPos getPos() {
		return entity != null ? entity.chunkPosition() : pos;
	}

	public EntityResult<T> getEntity(MinecraftServer server) {
		if (entity == null || entity.isRemoved()) {
			var world = server.getLevel(dim);
			if (world == null) {
				return EntityResult.from(null, EntityResult.EntityState.CHUNK_NOT_LOADED);
			}
			entity = filter.tryCast(world.getEntity(uuid));
			if (entity == null) {
				if (!world.areEntitiesLoaded(pos.toLong())) {
					world.getChunk(pos.x, pos.z, ChunkStatus.FULL);
					return EntityResult.from(null, EntityResult.EntityState.CHUNK_NOT_LOADED);
				}
			}
		}
		return EntityResult.from(entity, entity != null ? EntityResult.EntityState.PRESENT : EntityResult.EntityState.ABSENT);
	}

	public void tick() {
		if (entity != null) {
			pos = entity.chunkPosition();
			dim = entity.level().dimension();

			if (entity.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION) {
				for (var dim : entity.level().getServer().getAllLevels()) {
					entity = filter.tryCast(dim.getEntity(uuid));
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
