package nu.metacraft.lib.util;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.UnaryOperator;

public abstract sealed class EntityRef permits EntityRef.E, EntityRef.B {

	public static final class E extends EntityRef {
		private final Entity entity;

		public E(Entity e) {
			this.entity = e;
		}

		@Override
		public Box getBoundingBox() {
			return entity.getBoundingBox();
		}

		@Override
		public Vec3d getPos() {
			return entity.getPos();
		}

		@Override
		public BlockPos getBlockPos() {
			return entity.getBlockPos();
		}

		@Override
		public World getWorld() {
			return entity.getEntityWorld();
		}

		@Override
		public String getBackendName() {
			return entity.getUuidAsString();
		}

		@Override
		public float getHeight() {
			return entity.getHeight();
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

	public static final class B extends EntityRef {
		private final BlockEntity blockEntity;

		public B(BlockEntity blockEntity) {
			this.blockEntity = blockEntity;
		}

		public Box getBoundingBox() {
			return blockEntity.getCachedState().getCollisionShape(
					blockEntity.getWorld(), blockEntity.getPos()
			).getBoundingBox().offset(blockEntity.getPos());
		}

		@Override
		public BlockPos getBlockPos() {
			return blockEntity.getPos();
		}

		@Override
		public World getWorld() {
			return blockEntity.getWorld();
		}

		@Override
		public String getBackendName() {
			return blockEntity.getPos().toShortString();
		}

		@Override
		public boolean isRemoved() {
			return blockEntity.isRemoved();
		}

		@Override
		public void onUpdate() {
			blockEntity.markDirty();
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

	public DynamicRegistryManager getRegistryManager() {
		return getWorld().getRegistryManager();
	}

	public static EntityRef fromBlock(BlockEntity block) {
		return new B(block);
	}

	public static EntityRef fromEntity(Entity entity) {
		return new E(entity);
	}

	public abstract Box getBoundingBox();

	public float getHeight() {
		return (float) getBoundingBox().getLengthY();
	}

	public Vec3d getPos() {
		return getBoundingBox().getHorizontalCenter();
	}

	public abstract BlockPos getBlockPos();

	public abstract World getWorld();

	public ServerWorld getServerWorld() {
		return (ServerWorld) getWorld();
	}

	public boolean isReceivingRedstonePower() {
		return getWorld().isReceivingRedstonePower(getBlockPos());
	}

	public abstract String getBackendName();

	public abstract boolean isRemoved();

	public void onUpdate() {}

	public abstract Optional<Entity> getEntity();

	public abstract Optional<BlockEntity> getBlock();

	public abstract Object get();

	public <T> Optional<T> getCasted(Class<T> clazz) {
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
		if (context.hasParameter(LootContextParameters.THIS_ENTITY)) {
			return Optional.of(EntityRef.fromEntity(context.get(LootContextParameters.THIS_ENTITY)));
		}
		if (context.hasParameter(LootContextParameters.BLOCK_ENTITY)) {
			return Optional.of(EntityRef.fromBlock(context.get(LootContextParameters.BLOCK_ENTITY)));
		}
		return Optional.empty();
	}

}
