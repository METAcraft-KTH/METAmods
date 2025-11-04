package nu.metacraft.lib.util.helper;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;

public class TamedHelper {

	public static Optional<UUID> getRelevantPlayer(Entity entity) {
		if (entity instanceof Player p) {
			return Optional.of(p.getUUID());
		}
		if (entity instanceof OwnableEntity tameable) {
			var owner = tameable.getRootOwner();
			if (owner == null) {
				return Optional.ofNullable(tameable.getOwnerReference()).map(EntityReference::getUUID);
			}
			return getRelevantPlayer(owner);
		}
		if (entity instanceof TraceableEntity ownable) {
			var owner = ownable.getOwner();
			return getRelevantPlayer(owner);
		}
		return Optional.empty();
	}

}
