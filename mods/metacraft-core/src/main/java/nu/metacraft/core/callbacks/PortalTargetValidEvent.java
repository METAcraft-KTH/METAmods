package nu.metacraft.core.callbacks;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.block.entities.PortalEntity;

public interface PortalTargetValidEvent {

	Identifier PRE = METAcraftCore.getID("pre");
	Identifier POST = METAcraftCore.getID("post");

	Event<PortalTargetValidEvent> EVENT = EventFactory.createWithPhases(
			PortalTargetValidEvent.class, callbacks -> (targetDim, targetPos, portal, teleporting, currentlyValid) -> {
				for (var callback : callbacks) {
					currentlyValid = callback.isValid(targetDim, targetPos, portal, teleporting, currentlyValid);
				}
				return currentlyValid;
			},
			PRE, Event.DEFAULT_PHASE, POST
	);

	boolean isValid(ServerLevel targetDim, BlockPos targetPos, PortalEntity portal, Entity teleporting, boolean isCurrentlyValid);

}
