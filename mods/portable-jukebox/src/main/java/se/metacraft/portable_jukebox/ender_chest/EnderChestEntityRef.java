package se.metacraft.portable_jukebox.ender_chest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import se.metacraft.lib.util.EntityRef;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class EnderChestEntityRef implements EntityRef {

	private final Player player;

	private Optional<EntityRef> getEnderChest(Player player) {
		if (player.isRemoved()) return Optional.empty(); // Important to check that the player still exists. Otherwise, the entire server may crash when the player disconnects.
		return ChunkPos.rangeClosed(player.chunkPosition(), 5).flatMap(
			chunk -> player.level().getChunk(chunk.x(), chunk.z()).getBlockEntities().values().stream()
		).filter(blockEntity -> blockEntity instanceof EnderChestBlockEntity).map(e -> (EnderChestBlockEntity) e).min(
			Comparator.comparing(e -> e.getBlockPos().distToCenterSqr(player.position()))
		).map(EntityRef::fromBlock);
	}

	public EnderChestEntityRef(Player player) {
		this.player = player;
	}

	@Override
	public AABB getBoundingBox() {
		return getEnderChest(player).map(EntityRef::getBoundingBox).orElseGet(
			() -> AABB.ofSize(new Vec3(player.getX(), player.level().getMaxY()+64, player.getZ()), 1, 1, 1)
		);
	}

	@Override
	public BlockPos getBlockPos() {
		return getEnderChest(player).map(EntityRef::getBlockPos).orElseGet(
			() -> new BlockPos(player.blockPosition().getX(), player.level().getMaxY()+64, player.blockPosition().getZ())
		);
	}

	@Override
	public Level getWorld() {
		return getEnderChest(player).map(EntityRef::getWorld).orElseGet(
			player::level
		);
	}

	@Override
	public String getBackendName() {
		return getEnderChest(player).map(EntityRef::getBackendName).orElseGet(
			() -> player.getPlainTextName() + "'s EnderChest"
		);
	}

	@Override
	public boolean isRemoved() {
		return player.isRemoved();
	}

	@Override
	public Optional<Entity> getEntity() {
		return Optional.empty();
	}

	@Override
	public Optional<BlockEntity> getBlock() {
		return getEnderChest(player).flatMap(EntityRef::getBlock);
	}

	@Override
	public Object get() {
		return getEnderChest(player).map(EntityRef::get).orElse(null);
	}

	@Override
	public EntityRef mapEntity(UnaryOperator<Entity> entityModifier) {
		return new EnderChestEntityRef(player);
	}

	@Override
	public EntityRef mapBlock(UnaryOperator<BlockEntity> blockModifier) {
		return new EnderChestEntityRef(player);
	}

	@Override
	public EntityRef map(UnaryOperator<Entity> entityModifier, UnaryOperator<BlockEntity> blockModifier) {
		return new EnderChestEntityRef(player);
	}
}
