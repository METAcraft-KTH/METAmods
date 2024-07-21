package se.datasektionen.mc.metacraft_core.extensions;

import net.minecraft.nbt.NbtCompound;
import se.datasektionen.mc.metacraft_core.music.ServerBossBarWithMusic;

import java.util.Optional;

public interface EntityExtensions {

	Optional<ServerBossBarWithMusic> metacraft_lib$getBossBar();

	void metacraft_lib$setBossBar(ServerBossBarWithMusic bossBar);

	void metacraft_lib$loadBossBar(NbtCompound nbt);

	void metacraft_lib$saveBossBar(NbtCompound nbt);

}
