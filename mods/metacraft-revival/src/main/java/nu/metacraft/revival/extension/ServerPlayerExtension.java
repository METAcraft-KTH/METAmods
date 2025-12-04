package nu.metacraft.revival.extension;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public interface ServerPlayerExtension {

	void metacraft$resetRevivalState();

	void metacraft$setUnconscious(boolean unconscious);

	void metacraft$setReviver(Player entity);

	Player metacraft$getReviver();

	boolean metacraft$isUnconscious();

	void metacraft$setRevivalMenuOpen(boolean open);

	boolean metacraft$isRevivalMenuOpen();

	Component metacraft$getDeathMessage();

	Component metacraft$getRevivalStatus();
}
