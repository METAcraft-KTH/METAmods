package se.datasektionen.mc.loot_containers.containers;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import se.datasektionen.mc.loot_containers.mixin.AccessorLootTable;
import se.datasektionen.mc.loot_containers.util.EntityOrBlockEntity;

import java.util.Optional;

public interface LootAccess {
	Vec3d getPos();

	ServerWorld getWorld();

	default MinecraftServer getServer() {
		return getWorld().getServer();
	}

	Inventory getInventory();

	EntityOrBlockEntity getEntity();

	default IntList getPossibleSlots(ItemStack stack) {
		IntList possibleSlots = new IntArrayList(getInventory().size());
		for (int i = 0; i < getInventory().size(); i++) {
			var stackInSlot = getInventory().getStack(i);
			if (stackInSlot.isEmpty() || (ItemStack.areItemsAndComponentsEqual(stack, stackInSlot) && stackInSlot.getCount() < stackInSlot.getMaxCount())) {
				possibleSlots.add(i);
			}
		}
		return possibleSlots;
	}

	default ItemStack insertStack(ItemStack stack, Random random) {
		if (stack.isEmpty()) return stack;
		var possibleSlots = getPossibleSlots(stack);
		if (possibleSlots.isEmpty()) return stack;
		int slot = possibleSlots.getInt(random.nextInt(possibleSlots.size()));
		var previousStack = getInventory().getStack(slot);
		if (previousStack.isEmpty()) {
			getInventory().setStack(slot, stack);
			return ItemStack.EMPTY;
		} else {
			boolean insertedEntireStack = true;
			previousStack.increment(stack.getCount());
			if (previousStack.getCount() > previousStack.getMaxCount()) {
				int overFlowAmount = previousStack.getCount() - previousStack.getMaxCount();
				previousStack.setCount(previousStack.getMaxCount());
				stack.setCount(overFlowAmount);
				insertedEntireStack = false;
			}
			getInventory().markDirty();
			return insertedEntireStack ? ItemStack.EMPTY : stack;
		}
	}

	default void generateLootTable(
			RegistryKey<LootTable> id, ServerPlayerEntity player
	) {
		Random random = getWorld().getRandom();

		LootTable lootTable = getServer().getReloadableRegistries().getLootTable(id);
		if (player != null) {
			Criteria.PLAYER_GENERATES_CONTAINER_LOOT.trigger(player, id);
		}
		LootWorldContext.Builder builder = new LootWorldContext.Builder(getWorld()).add(LootContextParameters.ORIGIN, getPos());
		if (player != null) {
			builder.luck(player.getLuck()).add(LootContextParameters.THIS_ENTITY, player);
		}

		var params = builder.build(LootContextTypes.CHEST);

		var freeSlots = ((AccessorLootTable) lootTable).callGetFreeSlots(getInventory(), random);
		var loot = lootTable.generateLoot(params);
		((AccessorLootTable) lootTable).callShuffle(loot, freeSlots.size(), random);

		for (var stack : loot) {
			insertStack(stack, random);
		}
	}

	static <T extends Entity & Inventory> LootAccess entity(T entity) {
		return new EntityLootAccess(entity);
	}

	static <T extends BlockEntity & Inventory> LootAccess block(T block) {
		return new BlockEntityLootAccess(block);
	}

	static Optional<Inventory> getInventoryFromEntity(Entity entity) {
		if (entity instanceof Inventory) {
			return Optional.of((Inventory) entity);
		}
		return Optional.empty();
	}

	static Optional<Inventory> getInventoryFromBlockEntity(BlockEntity blockEntity) {
		if (blockEntity instanceof Inventory) {
			return Optional.of((Inventory) blockEntity);
		}
		return Optional.empty();
	}

	static Optional<Inventory> getInventory(EntityOrBlockEntity entity) {
		return entity.map(LootAccess::getInventoryFromEntity, LootAccess::getInventoryFromBlockEntity);
	}

	class EntityLootAccess implements LootAccess {

		private final Entity entity;

		public EntityLootAccess(Entity entity) {
			this.entity = entity;
		}

		@Override
		public Vec3d getPos() {
			return entity.getPos();
		}

		@Override
		public ServerWorld getWorld() {
			return (ServerWorld) entity.getWorld();
		}

		@Override
		public Inventory getInventory() {
			return getInventoryFromEntity(entity).orElse(null);
		}

		@Override
		public EntityOrBlockEntity getEntity() {
			return new EntityOrBlockEntity(entity);
		}
	}

	class BlockEntityLootAccess implements LootAccess {

		private final BlockEntity blockEntity;

		public BlockEntityLootAccess(BlockEntity blockEntity) {
			this.blockEntity = blockEntity;
		}

		@Override
		public Vec3d getPos() {
			return Vec3d.ofCenter(blockEntity.getPos());
		}

		@Override
		public ServerWorld getWorld() {
			return (ServerWorld) blockEntity.getWorld();
		}

		@Override
		public Inventory getInventory() {
			return getInventoryFromBlockEntity(blockEntity).orElse(null);
		}

		@Override
		public EntityOrBlockEntity getEntity() {
			return new EntityOrBlockEntity(blockEntity);
		}
	}
}
