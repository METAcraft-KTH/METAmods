package se.datasektionen.mc.metacraft_core.entity.ai;

import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

public class METAcraftActivities {

	public static void init() {

	}

	private static Activity register(String id) {
		return Registry.register(Registries.ACTIVITY, METAcraftCore.getID(id), new Activity(id));
	}

}
