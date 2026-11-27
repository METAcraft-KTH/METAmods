package se.metacraft.portable_jukebox.block;

import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.data.DisplayEntityData;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jspecify.annotations.NonNull;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.lib.util.TaskScheduler;
import se.metacraft.portable_jukebox.gui.BoomboxStorageInventory;
import se.metacraft.portable_jukebox.item.BoomboxItem;
import se.metacraft.portable_jukebox.item.components.BoomboxComponents;
import se.metacraft.portable_jukebox.item.components.BoomboxInvData;

public class BoomboxBlockEntity extends BlockEntity implements ContainerSingleItem.BlockContainerSingleItem {

	private static final String BOOMBOX = "Jukebox"; //Careful, this is used by a datafixer!

	private ItemStack boombox = ItemStack.EMPTY;

	public BoomboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public BoomboxBlockEntity(BlockPos pos, BlockState state) {
		super(BoomboxBlocks.BlockEntities.BOOMBOX, pos, state);
	}

	public void setBoombox(ItemStack boombox) {
		this.boombox = boombox;
		fixModel();
	}

	public ItemStack getBoombox() {
		return boombox;
	}

	@Override
	public void loadAdditional(ValueInput nbt) {
		super.loadAdditional(nbt);
		boombox = nbt.read(BOOMBOX, ItemStack.CODEC).orElse(ItemStack.EMPTY);
		fixModel();
	}

	@Override
	public void setLevel(@NonNull Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			TaskScheduler.scheduleImmediately(level.getServer(), this::fixModel);
		}
	}

	private void fixModel() {
		if (level == null || boombox.isEmpty() || level.isClientSide()) return;
		var attachment = BlockBoundAttachment.get(level, getBlockPos());
		if (attachment == null) return;
		attachment.holder().getElements().stream().filter(
			element -> element instanceof ItemDisplayElement
		).findAny().ifPresent(element -> {
			var e = ((ItemDisplayElement) element);
			e.getItem().set(DataComponents.ITEM_MODEL, boombox.get(DataComponents.ITEM_MODEL));
			e.getSyncedData().set(DisplayEntityData.Item.ITEM, e.getItem(), true);
			var dirty = e.getSyncedData().getDirtyEntries();
			if (dirty != null) {
				attachment.holder().sendPacket(
					new ClientboundSetEntityDataPacket(e.getEntityId(), dirty)
				);
			}
		});
	}

	@Override
	public void saveAdditional(ValueOutput nbt) {
		super.saveAdditional(nbt);
		if (!boombox.isEmpty()) {
			nbt.store(BOOMBOX, ItemStack.CODEC, boombox);
		}
	}

	@Override
	public BlockEntity getContainerBlockEntity() {
		return this;
	}

	@Override
	public ItemStack getTheItem() {
		return boombox.getOrDefault(
			BoomboxComponents.BOOMBOX, BoomboxInvData.EMPTY
		).getItem();
	}

	@Override
	public ItemStack splitTheItem(int count) {
		var stack = getTheItem();
		if (count < 1) return ItemStack.EMPTY;
		setTheItem(ItemStack.EMPTY); //It will never be more than a count of 1 in this thing anyway.
		return stack;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
		//Prevent the disc from being ejected when broken (the default behaviour of this function is to drop all items in the inventory).
	}

	@Override
	public void removeComponentsFromTag(
		final @NonNull ValueOutput output
	) {
		super.removeComponentsFromTag(output);
		output.discard(BOOMBOX);
	}

	@Override
	public void clearContent() {
		BlockContainerSingleItem.super.clearContent();
		storage().clearContent();
	}

	@Override
	public void setTheItem(ItemStack stack) {
		var prev = this.boombox.update(
			BoomboxComponents.BOOMBOX, BoomboxInvData.EMPTY,
			boombox -> boombox.withItem(stack)
		);
		BoomboxItem.updateStackChange(
			EntityRef.fromBlock(this), prev != null ? prev.getItem() : ItemStack.EMPTY, boombox
		);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	// Slot 0 is the disc, the rest storage, so hoppers reach both.
	private BoomboxStorageInventory storage() {
		return new BoomboxStorageInventory(boombox, this::setChanged);
	}

	@Override
	public int getContainerSize() {
		return 1 + storage().getContainerSize();
	}

	@Override
	public boolean isEmpty() {
		return getTheItem().isEmpty() && storage().isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot == 0 ? getTheItem() : storage().getItem(slot - 1);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return slot == 0 ? splitTheItem(count) : storage().removeItem(slot - 1, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return slot == 0 ? splitTheItem(1) : storage().removeItemNoUpdate(slot - 1);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot == 0) {
			setTheItem(stack);
		} else {
			storage().setItem(slot - 1, stack);
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.has(DataComponents.JUKEBOX_PLAYABLE) && this.getItem(slot).isEmpty();
	}

	@Override
	public boolean canTakeItem(Container hopperInventory, int slot, ItemStack stack) {
		return hopperInventory.hasAnyMatching(ItemStack::isEmpty);
	}
}
