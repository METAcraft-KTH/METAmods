package nu.metacraft.loot_containers.containers;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.loot_containers.mixin.LootTableAccessor;
import nu.metacraft.loot_containers.util.EntityOrBlockEntity;

import java.util.Optional;

public interface LootAccess {
	Vec3 getPos();

	ServerLevel getWorld();

	default MinecraftServer getServer() {
		return getWorld().getServer();
	}

	Container getInventory();

	EntityOrBlockEntity getEntity();

	default IntList getPossibleSlots(ItemStack stack) {
		IntList possibleSlots = new IntArrayList(getInventory().getContainerSize());
		for (int i = 0; i < getInventory().getContainerSize(); i++) {
			var stackInSlot = getInventory().getItem(i);
			if (stackInSlot.isEmpty() || (ItemStack.isSameItemSameComponents(stack, stackInSlot) && stackInSlot.getCount() < stackInSlot.getMaxStackSize())) {
				possibleSlots.add(i);
			}
		}
		return possibleSlots;
	}

	default ItemStack insertStack(ItemStack stack, RandomSource random) {
		if (stack.isEmpty()) return stack;
		var possibleSlots = getPossibleSlots(stack);
		if (possibleSlots.isEmpty()) return stack;
		int slot = possibleSlots.getInt(random.nextInt(possibleSlots.size()));
		var previousStack = getInventory().getItem(slot);
		if (previousStack.isEmpty()) {
			getInventory().setItem(slot, stack);
			return ItemStack.EMPTY;
		} else {
			boolean insertedEntireStack = true;
			previousStack.grow(stack.getCount());
			if (previousStack.getCount() > previousStack.getMaxStackSize()) {
				int overFlowAmount = previousStack.getCount() - previousStack.getMaxStackSize();
				previousStack.setCount(previousStack.getMaxStackSize());
				stack.setCount(overFlowAmount);
				insertedEntireStack = false;
			}
			getInventory().setChanged();
			return insertedEntireStack ? ItemStack.EMPTY : stack;
		}
	}

	default void generateLootTable(
			ResourceKey<LootTable> id, ServerPlayer player
	) {
		RandomSource random = getWorld().getRandom();

		LootTable lootTable = getServer().reloadableRegistries().getLootTable(id);
		if (player != null) {
			CriteriaTriggers.GENERATE_LOOT.trigger(player, id);
		}
		LootParams.Builder builder = new LootParams.Builder(getWorld()).withParameter(LootContextParams.ORIGIN, getPos());
		if (player != null) {
			builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
		}

		var params = builder.create(LootContextParamSets.CHEST);

		var freeSlots = ((LootTableAccessor) lootTable).callGetAvailableSlots(getInventory(), random);
		var loot = lootTable.getRandomItems(params);
		((LootTableAccessor) lootTable).callShuffleAndSplitItems(loot, freeSlots.size(), random);

		for (var stack : loot) {
			insertStack(stack, random);
		}
	}

	static <T extends Entity & Container> LootAccess entity(T entity) {
		return new EntityLootAccess(entity);
	}

	static <T extends BlockEntity & Container> LootAccess block(T block) {
		return new BlockEntityLootAccess(block);
	}

	static Optional<Container> getInventoryFromEntity(Entity entity) {
		if (entity instanceof Container c) {
			return Optional.of(c);
		}
		return Optional.empty();
	}

	static Optional<Container> getInventoryFromBlockEntity(BlockEntity blockEntity) {
		if (blockEntity instanceof Container c) {
			return Optional.of(c);
		}
		return Optional.empty();
	}

	static Optional<Container> getInventory(EntityOrBlockEntity entity) {
		return entity.map(LootAccess::getInventoryFromEntity, LootAccess::getInventoryFromBlockEntity);
	}

	class EntityLootAccess implements LootAccess {

		private final Entity entity;

		public EntityLootAccess(Entity entity) {
			this.entity = entity;
		}

		@Override
		public Vec3 getPos() {
			return entity.position();
		}

		@Override
		public ServerLevel getWorld() {
			return (ServerLevel) entity.level();
		}

		@Override
		public Container getInventory() {
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
		public Vec3 getPos() {
			return Vec3.atCenterOf(blockEntity.getBlockPos());
		}

		@Override
		public ServerLevel getWorld() {
			return (ServerLevel) blockEntity.getLevel();
		}

		@Override
		public Container getInventory() {
			return getInventoryFromBlockEntity(blockEntity).orElse(null);
		}

		@Override
		public EntityOrBlockEntity getEntity() {
			return new EntityOrBlockEntity(blockEntity);
		}
	}
}
