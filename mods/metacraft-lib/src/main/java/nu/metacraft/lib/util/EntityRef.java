package nu.metacraft.lib.util;

import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface EntityRef {

	public static final class E implements EntityRef {
		private final Entity entity;

		public E(Entity e) {
			this.entity = e;
		}

		@Override
		public AABB getBoundingBox() {
			return entity.getBoundingBox();
		}

		@Override
		public Vec3 getPos() {
			return entity.position();
		}

		@Override
		public BlockPos getBlockPos() {
			return entity.blockPosition();
		}

		@Override
		public Level getWorld() {
			return entity.level();
		}

		@Override
		public String getBackendName() {
			return entity.getStringUUID();
		}

		@Override
		public float getHeight() {
			return entity.getBbHeight();
		}

		@Override
		public boolean isRemoved() {
			return entity.isRemoved();
		}

		@Override
		public Optional<Entity> getEntity() {
			return Optional.of(entity);
		}

		@Override
		public Optional<BlockEntity> getBlock() {
			return Optional.empty();
		}

		@Override
		public Object get() {
			return entity;
		}

		@Override
		public EntityRef mapEntity(UnaryOperator<Entity> entityModifier) {
			return EntityRef.fromEntity(entityModifier.apply(entity));
		}

		@Override
		public EntityRef mapBlock(UnaryOperator<BlockEntity> blockModifier) {
			return this;
		}

		@Override
		public EntityRef map(UnaryOperator<Entity> entityModifier, UnaryOperator<BlockEntity> blockModifier) {
			return mapEntity(entityModifier);
		}
	}

	public static final class B implements EntityRef {
		private final BlockEntity blockEntity;

		public B(BlockEntity blockEntity) {
			this.blockEntity = blockEntity;
		}

		public AABB getBoundingBox() {
			return blockEntity.getBlockState().getCollisionShape(
					blockEntity.getLevel(), blockEntity.getBlockPos()
			).bounds().move(blockEntity.getBlockPos());
		}

		@Override
		public BlockPos getBlockPos() {
			return blockEntity.getBlockPos();
		}

		@Override
		public Level getWorld() {
			return blockEntity.getLevel();
		}

		@Override
		public String getBackendName() {
			return blockEntity.getBlockPos().toShortString();
		}

		@Override
		public boolean isRemoved() {
			return blockEntity.isRemoved();
		}

		@Override
		public void onUpdate() {
			blockEntity.setChanged();
		}

		@Override
		public Optional<Entity> getEntity() {
			return Optional.empty();
		}

		@Override
		public Optional<BlockEntity> getBlock() {
			return Optional.of(blockEntity);
		}

		@Override
		public Object get() {
			return blockEntity;
		}

		@Override
		public EntityRef mapEntity(UnaryOperator<Entity> entityModifier) {
			return this;
		}

		@Override
		public EntityRef mapBlock(UnaryOperator<BlockEntity> blockModifier) {
			return EntityRef.fromBlock(blockModifier.apply(blockEntity));
		}

		@Override
		public EntityRef map(UnaryOperator<Entity> entityModifier, UnaryOperator<BlockEntity> blockModifier) {
			return mapBlock(blockModifier);
		}
	}

	default RegistryAccess getRegistryManager() {
		return getWorld().registryAccess();
	}

	public static EntityRef fromBlock(BlockEntity block) {
		return new B(block);
	}

	public static EntityRef fromEntity(Entity entity) {
		return new E(entity);
	}

	public abstract AABB getBoundingBox();

	default float getHeight() {
		return (float) getBoundingBox().getYsize();
	}

	default Vec3 getPos() {
		return getBoundingBox().getBottomCenter();
	}

	public abstract BlockPos getBlockPos();

	public abstract Level getWorld();

	default ServerLevel getServerWorld() {
		return (ServerLevel) getWorld();
	}

	default boolean isReceivingRedstonePower() {
		return getWorld().hasNeighborSignal(getBlockPos());
	}

	public abstract String getBackendName();

	public abstract boolean isRemoved();

	default void onUpdate() {}

	public abstract Optional<Entity> getEntity();

	public abstract Optional<BlockEntity> getBlock();

	public abstract Object get();

	default  <T> Optional<T> getCasted(Class<T> clazz) {
		var object = get();
		if (clazz.isInstance(object)) {
			return Optional.of(clazz.cast(object));
		}
		return Optional.empty();
	}

	public abstract EntityRef mapEntity(UnaryOperator<Entity> entityModifier);
	public abstract EntityRef mapBlock(UnaryOperator<BlockEntity> blockModifier);
	public abstract EntityRef map(UnaryOperator<Entity> entityModifier, UnaryOperator<BlockEntity> blockModifier);

	public static Optional<EntityRef> fromContext(LootContext context) {
		if (context.hasParameter(LootContextParams.THIS_ENTITY)) {
			return Optional.of(EntityRef.fromEntity(context.getOptional(LootContextParams.THIS_ENTITY)));
		}
		if (context.hasParameter(LootContextParams.BLOCK_ENTITY)) {
			return Optional.of(EntityRef.fromBlock(context.getOptional(LootContextParams.BLOCK_ENTITY)));
		}
		return Optional.empty();
	}

}
