package nu.metacraft.portable_jukebox;

import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.mixin.AccessorDoubleInventory;
import nu.metacraft.portable_jukebox.mixin.AccessorEnderChestInventory;
import nu.metacraft.portable_jukebox.mixin.AccessorSimpleInventory;

import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class InventoryHelper {

	public static Optional<Level> getWorldFromInventory(Container inventory) {
		var ref = getEntityFromInventory(inventory);
		return ref.map(EntityRef::getWorld);
	}

	public static Optional<EntityRef> getEntityFromInventory(Container inventory) {
		return switch (inventory) {
			case Inventory playerInv -> Optional.of(EntityRef.fromEntity(playerInv.player));
			case BlockEntity blockEntity -> Optional.of(EntityRef.fromBlock(blockEntity));
			case Entity entity -> Optional.of(EntityRef.fromEntity(entity));
			case AccessorDoubleInventory doubleInv -> Optional.ofNullable(
					getEntityFromInventory(doubleInv.getContainer1())
			).orElse(
					getEntityFromInventory(doubleInv.getContainer2())
			);
			case AccessorEnderChestInventory enderChest -> Optional.ofNullable(enderChest.getActiveChest()).map(EntityRef::fromBlock);
			case AccessorSimpleInventory simple -> Optional.ofNullable(simple.getListeners()).flatMap(listeners -> listeners.stream().filter(
					listener -> listener instanceof Entity || listener instanceof BlockEntity
			).map(listener -> {
				if (listener instanceof Entity e) {
					return EntityRef.fromEntity(e);
				} else {
					return EntityRef.fromBlock((BlockEntity) listener);
				}
			}).findAny());
			case null, default -> Optional.empty();
		};
	}

}
