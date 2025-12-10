package nu.metacraft.saved_items.item_saving;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public interface ItemEntityData {

	Component metacraft_saved_items$getSourcePlayerName();

	void metacraft_saved_items$setDroppedByDeadPlayer(Player player);

}
