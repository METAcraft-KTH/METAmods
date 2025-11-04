package nu.metacraft.cutscenes.extension;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;

public interface EntityExtension {

	Entity metacraft$teleportInCutscene(TeleportTransition target);

	boolean metacraft$canChangeWorldInCutscene();


	void metacraft$setHasAccurateMovement(boolean accurateMovement);
	boolean metacraft$hasAccurateMovement();

}
