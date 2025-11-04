package nu.metacraft.bosses.extensions;

import net.minecraft.world.entity.Entity;
import nu.metacraft.bosses.util.DoubleTeamHandler;

public interface LivingEntityExtensions {

	void metacraft$setPhantomEntity(boolean phantom);

	void metacraft$setDoubleTeamHandler(DoubleTeamHandler handler);

	void metacraft$setSoulboundEntity(Entity soulboundEntity);

	Entity metacraft$getNonSoulboundMaster();

}
