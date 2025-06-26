package se.datasektionen.mc.metacraft_core.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import se.datasektionen.mc.metacraft_core.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_core.music.ManageableServerBossBar;

import java.util.Optional;

public class BossBarHelper {

	public static Optional<ManageableServerBossBar> getBossBar(Entity entity) {
		return ((EntityExtensions) entity).metacraft_lib$getBossBar();
	}

	public static void setBossBar(Entity entity, ManageableServerBossBar bossBar) {
		((EntityExtensions) entity).metacraft_lib$setBossBar(bossBar);
	}

	public static boolean transferBossBar(Entity source, Entity target) {
		var bossbar = getBossBar(source);
		if (bossbar.isPresent()) {
			if (source != target) {
				if (BossBarHelper.getBossBar(target).isPresent()) {
					BossBarHelper.removeBossBar(target);
				}
				((EntityExtensions) target).metacraft_lib$setBossBarNoUpdate(bossbar.get());
				((EntityExtensions) source).metacraft_lib$setBossBarNoUpdate(null);
				((EntityExtensions) target).metacraft_lib$updateBossBarReplaced();
				((EntityExtensions) source).metacraft_lib$updateBossBarReplaced();
			}
			return true;
		} else {
			return false;
		}
	}

	public static void removeBossBar(Entity entity) {
		setBossBar(entity, null);
	}

	public static void loadBossBar(Entity entity, ReadView nbt) {
		((EntityExtensions) entity).metacraft_lib$loadBossBar(nbt);
	}

	public static void saveBossBar(Entity entity, WriteView nbt) {
		((EntityExtensions) entity).metacraft_lib$saveBossBar(nbt);
	}

}
