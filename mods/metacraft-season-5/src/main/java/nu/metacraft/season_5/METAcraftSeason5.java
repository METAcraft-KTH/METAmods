package nu.metacraft.season_5;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.season_5.items.Season5Items;

public class METAcraftSeason5 implements ModInitializer {

	@Override
	public void onInitialize() {
		Season5Items.init();
		S5GameRules.init();
	}

	public static Identifier getID(String name) {
		return Identifier.fromNamespaceAndPath(METAcraftLib.NAMESPACE, name);
	}

	public static boolean shouldVoidTeleport(Entity entity) {
		if (entity.getType() == EntityType.ENDER_PEARL) return false;
		return !entity.isPassenger() || !shouldVoidTeleport(entity.getVehicle());
	}

}
