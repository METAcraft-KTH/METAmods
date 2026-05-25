package nu.metacraft.portable_jukebox;

import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.mixin.CompoundContainerAccessor;
import nu.metacraft.portable_jukebox.mixin.PlayerEnderChestContainerAccessor;

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
			case CompoundContainerAccessor doubleInv -> Optional.ofNullable(
					getEntityFromInventory(doubleInv.getContainer1())
			).orElse(
					getEntityFromInventory(doubleInv.getContainer2())
			);
			case PlayerEnderChestContainerAccessor enderChest -> Optional.ofNullable(enderChest.getActiveChest()).map(EntityRef::fromBlock);
			case null, default -> Optional.empty();
		};
	}

}
