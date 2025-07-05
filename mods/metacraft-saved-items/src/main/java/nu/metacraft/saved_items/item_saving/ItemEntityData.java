package nu.metacraft.saved_items.item_saving;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public interface ItemEntityData {

	Text metacraft_saved_items$getSourcePlayerName();

	void metacraft_saved_items$setDroppedByDeadPlayer(PlayerEntity player);

}
