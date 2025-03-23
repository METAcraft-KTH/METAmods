package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.Ownable;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Optional;
import java.util.UUID;

public class TamedHelper {

	public static Optional<UUID> getRelevantPlayer(Entity entity) {
		if (entity instanceof PlayerEntity p) {
			return Optional.of(p.getUuid());
		}
		if (entity instanceof Tameable tameable) {
			var owner = tameable.getTopLevelOwner();
			if (owner == null) {
				return Optional.ofNullable(tameable.getOwnerReference()).map(LazyEntityReference::getUuid);
			}
			return getRelevantPlayer(owner);
		}
		if (entity instanceof Ownable ownable) {
			var owner = ownable.getOwner();
			return getRelevantPlayer(owner);
		}
		return Optional.empty();
	}

}
