package nu.metacraft.portable_jukebox.data;

import net.minecraft.entity.Entity;

public interface RemovalAware {

	void onEntityRemoved(Entity.RemovalReason reason);
}
