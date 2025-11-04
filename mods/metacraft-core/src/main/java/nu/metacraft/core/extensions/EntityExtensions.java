package nu.metacraft.core.extensions;

import nu.metacraft.core.music.ManageableServerBossBar;

import java.util.Optional;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public interface EntityExtensions {

	Optional<ManageableServerBossBar> metacraft_lib$getBossBar();

	void metacraft_lib$setBossBar(ManageableServerBossBar bossBar);

	void metacraft_lib$setBossBarNoUpdate(ManageableServerBossBar bossBar);

	void metacraft_lib$updateBossBarReplaced();

	void metacraft_lib$loadBossBar(ValueInput nbt);

	void metacraft_lib$saveBossBar(ValueOutput nbt);


	void metacraft$setLastMovedByMovingBlockTick(long tick);

	long metacraft$getLastMovedByMovingBlockTick();

}
