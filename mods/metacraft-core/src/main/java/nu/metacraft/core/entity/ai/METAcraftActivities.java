package nu.metacraft.core.entity.ai;

import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import nu.metacraft.core.METAcraftCore;

public class METAcraftActivities {

	public static void init() {

	}

	private static Activity register(String id) {
		return Registry.register(Registries.ACTIVITY, METAcraftCore.getID(id), new Activity(id));
	}

}
