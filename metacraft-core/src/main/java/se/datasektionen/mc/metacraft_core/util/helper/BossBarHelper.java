package se.datasektionen.mc.metacraft_core.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import se.datasektionen.mc.metacraft_core.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_core.music.ServerBossBarWithMusic;

import java.util.Optional;

public class BossBarHelper {

	public static Optional<ServerBossBarWithMusic> getBossBar(Entity entity) {
		return ((EntityExtensions) entity).metacraft_lib$getBossBar();
	}

	public static void setBossBar(Entity entity, ServerBossBarWithMusic bossBar) {
		((EntityExtensions) entity).metacraft_lib$setBossBar(bossBar);
	}

	public static void removeBossBar(Entity entity) {
		setBossBar(entity, null);
	}

	public static void loadBossBar(Entity entity, NbtCompound nbt) {
		((EntityExtensions) entity).metacraft_lib$loadBossBar(nbt);
	}

	public static void saveBossBar(Entity entity, NbtCompound nbt) {
		((EntityExtensions) entity).metacraft_lib$saveBossBar(nbt);
	}

}
