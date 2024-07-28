package se.datasektionen.mc.better_pets;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;
import java.util.UUID;

public interface TameableExtension {

	void metacraft$setCurrentFollowTarget(ServerPlayerEntity entity);
	ServerPlayerEntity metacraft$getCurrentFollowTarget();
	boolean metaraft$isTrusted(LivingEntity player);
	void metacraft$addTrustedPlayer(UUID player);
	void metacraft$removeTrustedPlayer(UUID player);
	Collection<UUID> metacraft$getTrustedPlayers();

	void metacraft$tick();

}
