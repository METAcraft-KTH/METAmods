package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.entity.Entity;
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
			return Optional.ofNullable(tameable.getOwnerUuid());
		}
		if (entity instanceof Ownable ownable) {
			var owner = ownable.getOwner();
			return getRelevantPlayer(owner);
		}
		return Optional.empty();
	}

}
