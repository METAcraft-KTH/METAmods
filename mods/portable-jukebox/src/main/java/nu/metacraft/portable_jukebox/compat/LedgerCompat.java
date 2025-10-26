package nu.metacraft.portable_jukebox.compat;

import com.github.quiltservertools.ledger.callbacks.ItemPickUpCallback;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;
import nu.metacraft.portable_jukebox.item.components.Components;

public class LedgerCompat {

	public static void init() {
		ItemPickUpCallback.EVENT.register((itemEntity, playerEntity) -> {
			if (itemEntity.getStack().contains(Components.PORTABLE_JUKEBOX_ENTITY)) {
				var stack = playerEntity.getInventory().getStack(playerEntity.getInventory().getSlotWithStack(itemEntity.getStack()));
				if (!stack.isEmpty()) {
					PortableJukeboxEntity.transfer(
							EntityRef.fromEntity(itemEntity),
							EntityRef.fromEntity(playerEntity),
							stack
					);
				}
			}
		});
	}

}
