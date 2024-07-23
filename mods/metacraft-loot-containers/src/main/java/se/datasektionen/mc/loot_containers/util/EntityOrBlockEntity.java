package se.datasektionen.mc.loot_containers.util;

import com.mojang.datafixers.util.Either;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;

import java.util.function.Consumer;
import java.util.function.Function;

public class EntityOrBlockEntity {

	private final Either<Entity, BlockEntity> entity;

	public EntityOrBlockEntity(Entity entity) {
		this.entity = Either.left(entity);
	}

	public EntityOrBlockEntity(BlockEntity blockEntity) {
		this.entity = Either.right(blockEntity);
	}

	public Either<Entity, BlockEntity> get() {
		return entity;
	}

	public <T> T map(Function<Entity, T> entityFunc, Function<BlockEntity, T> blockEntityFunc) {
		return entity.map(entityFunc, blockEntityFunc);
	}

	public void run(Consumer<Entity> ifEntity, Consumer<BlockEntity> ifBlock) {
		entity.ifLeft(ifEntity);
		entity.ifRight(ifBlock);
	}

}
