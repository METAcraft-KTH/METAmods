package se.datasektionen.mc.portable_jukebox;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.mixin.AccessorDoubleInventory;
import se.datasektionen.mc.portable_jukebox.mixin.AccessorEnderChestInventory;
import se.datasektionen.mc.portable_jukebox.mixin.AccessorSimpleInventory;

import java.util.Optional;

public class InventoryHelper {

	public static Optional<World> getWorldFromInventory(Inventory inventory) {
		var ref = getEntityFromInventory(inventory);
		return ref.map(EntityRef::getWorld);
	}

	public static Optional<EntityRef> getEntityFromInventory(Inventory inventory) {
		return switch (inventory) {
			case PlayerInventory playerInv -> Optional.of(EntityRef.fromEntity(playerInv.player));
			case BlockEntity blockEntity -> Optional.of(EntityRef.fromBlock(blockEntity));
			case Entity entity -> Optional.of(EntityRef.fromEntity(entity));
			case AccessorDoubleInventory doubleInv -> Optional.ofNullable(
					getEntityFromInventory(doubleInv.getFirst())
			).orElse(
					getEntityFromInventory(doubleInv.getSecond())
			);
			case AccessorEnderChestInventory enderChest -> Optional.ofNullable(enderChest.getActiveBlockEntity()).map(EntityRef::fromBlock);
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
