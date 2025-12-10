package nu.metacraft.season_5;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.season_5.items.Season5Items;

public class METAcraftSeason5 implements ModInitializer {

	@Override
	public void onInitialize() {
		Season5Items.init();
	}

	public static Identifier getID(String name) {
		return Identifier.fromNamespaceAndPath(METAcraftLib.NAMESPACE, name);
	}

}
