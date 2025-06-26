package se.datasektionen.mc.metacraft_core.extensions;

import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import se.datasektionen.mc.metacraft_core.music.ManageableServerBossBar;

import java.util.Optional;

public interface EntityExtensions {

	Optional<ManageableServerBossBar> metacraft_lib$getBossBar();

	void metacraft_lib$setBossBar(ManageableServerBossBar bossBar);

	void metacraft_lib$setBossBarNoUpdate(ManageableServerBossBar bossBar);

	void metacraft_lib$updateBossBarReplaced();

	void metacraft_lib$loadBossBar(ReadView nbt);

	void metacraft_lib$saveBossBar(WriteView nbt);


	void metacraft$setLastMovedByMovingBlockTick(long tick);

	long metacraft$getLastMovedByMovingBlockTick();

}
