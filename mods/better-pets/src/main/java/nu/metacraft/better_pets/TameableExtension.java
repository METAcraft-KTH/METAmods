package nu.metacraft.better_pets;

import java.util.Collection;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public interface TameableExtension {

	void metacraft$setCurrentFollowTarget(ServerPlayer entity);
	LivingEntity metacraft$getCurrentFollowTarget();
	boolean metaraft$isTrusted(LivingEntity player);
	void metacraft$addTrustedPlayer(UUID player);
	void metacraft$removeTrustedPlayer(UUID player);
	Collection<UUID> metacraft$getTrustedPlayers();

	void metacraft$tick();

}
