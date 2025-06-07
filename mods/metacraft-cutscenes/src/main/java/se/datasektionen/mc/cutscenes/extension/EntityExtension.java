package se.datasektionen.mc.cutscenes.extension;

import net.minecraft.entity.Entity;
import net.minecraft.world.TeleportTarget;

public interface EntityExtension {

	Entity metacraft$teleportInCutscene(TeleportTarget target);

	boolean metacraft$canChangeWorldInCutscene();


	void metacraft$setHasAccurateMovement(boolean accurateMovement);
	boolean metacraft$hasAccurateMovement();

}
