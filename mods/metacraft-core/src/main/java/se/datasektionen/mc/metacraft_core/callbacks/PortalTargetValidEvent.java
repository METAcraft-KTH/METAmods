package se.datasektionen.mc.metacraft_core.callbacks;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;

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

	boolean isValid(ServerWorld targetDim, BlockPos targetPos, PortalEntity portal, Entity teleporting, boolean isCurrentlyValid);

}
