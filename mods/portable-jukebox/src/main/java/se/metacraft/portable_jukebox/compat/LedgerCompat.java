package se.metacraft.portable_jukebox.compat;

import com.github.quiltservertools.ledger.callbacks.ItemPickUpCallback;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.entity.BoomboxEntity;
import se.metacraft.portable_jukebox.item.components.Components;

public class LedgerCompat {

	public static void init() {
		ItemPickUpCallback.EVENT.register((itemEntity, playerEntity) -> {
			if (itemEntity.getItem().has(Components.BOOMBOX_ENTITY)) {
				var stack = playerEntity.getInventory().getItem(playerEntity.getInventory().findSlotMatchingItem(itemEntity.getItem()));
				if (!stack.isEmpty()) {
					BoomboxEntity.transfer(
							EntityRef.fromEntity(itemEntity),
							EntityRef.fromEntity(playerEntity),
							stack
					);
				}
			}
		});
	}

}
