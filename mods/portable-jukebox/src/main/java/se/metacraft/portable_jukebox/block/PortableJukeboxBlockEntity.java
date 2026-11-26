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
import se.metacraft.portable_jukebox.item.PortableJukeboxItem;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.item.components.PortableJukeboxInvData;

public class PortableJukeboxBlockEntity extends BlockEntity implements ContainerSingleItem.BlockContainerSingleItem {

	private static final String JUKEBOX = "Jukebox"; //Careful, this is used by a datafixer!

	private ItemStack jukebox = ItemStack.EMPTY;

	public PortableJukeboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public PortableJukeboxBlockEntity(BlockPos pos, BlockState state) {
		super(Blocks.BlockEntities.PORTABLE_JUKEBOX, pos, state);
	}

	public void setJukebox(ItemStack jukebox) {
		this.jukebox = jukebox;
		fixModel();
	}

	public ItemStack getJukebox() {
		return jukebox;
	}

	@Override
	public void loadAdditional(ValueInput nbt) {
		super.loadAdditional(nbt);
		jukebox = nbt.read(JUKEBOX, ItemStack.CODEC).orElse(ItemStack.EMPTY);
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
		if (level == null || jukebox.isEmpty() || level.isClientSide()) return;
		var attachment = BlockBoundAttachment.get(level, getBlockPos());
		if (attachment == null) return;
		attachment.holder().getElements().stream().filter(
			element -> element instanceof ItemDisplayElement
		).findAny().ifPresent(element -> {
			var e = ((ItemDisplayElement) element);
			e.getItem().set(DataComponents.ITEM_MODEL, jukebox.get(DataComponents.ITEM_MODEL));
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
		if (!jukebox.isEmpty()) {
			nbt.store(JUKEBOX, ItemStack.CODEC, jukebox);
		}
	}

	@Override
	public BlockEntity getContainerBlockEntity() {
		return this;
	}

	@Override
	public ItemStack getTheItem() {
		return jukebox.getOrDefault(
			Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY
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
	public void setTheItem(ItemStack stack) {
		var prev = this.jukebox.update(
			Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY,
			jukebox -> jukebox.withItem(stack)
		);
		PortableJukeboxItem.updateStackChange(
			EntityRef.fromBlock(this), prev != null ? prev.getItem() : ItemStack.EMPTY, jukebox
		);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
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
