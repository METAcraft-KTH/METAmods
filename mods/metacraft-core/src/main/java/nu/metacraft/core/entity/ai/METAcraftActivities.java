package nu.metacraft.core.entity.ai;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.schedule.Activity;
import nu.metacraft.core.METAcraftCore;

public class METAcraftActivities {

	public static void init() {

	}

	private static Activity register(String id) {
		return Registry.register(BuiltInRegistries.ACTIVITY, METAcraftCore.getID(id), new Activity(id));
	}

}
