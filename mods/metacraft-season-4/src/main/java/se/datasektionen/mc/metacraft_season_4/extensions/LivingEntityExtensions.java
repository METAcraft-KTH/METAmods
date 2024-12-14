package se.datasektionen.mc.metacraft_season_4.extensions;

import net.minecraft.entity.Entity;
import se.datasektionen.mc.metacraft_season_4.util.DoubleTeamHandler;

public interface LivingEntityExtensions {

	void metacraft$setPhantomEntity(boolean phantom);

	void metacraft$setDoubleTeamHandler(DoubleTeamHandler handler);

	void metacraft$setSoulboundEntity(Entity soulboundEntity);

	Entity metacraft$getNonSoulboundMaster();

}
