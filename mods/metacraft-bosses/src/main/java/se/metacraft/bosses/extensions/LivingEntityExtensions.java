package se.metacraft.bosses.extensions;

import net.minecraft.entity.Entity;
import se.metacraft.bosses.util.DoubleTeamHandler;

public interface LivingEntityExtensions {

	void metacraft$setPhantomEntity(boolean phantom);

	void metacraft$setDoubleTeamHandler(DoubleTeamHandler handler);

	void metacraft$setSoulboundEntity(Entity soulboundEntity);

	Entity metacraft$getNonSoulboundMaster();

}
