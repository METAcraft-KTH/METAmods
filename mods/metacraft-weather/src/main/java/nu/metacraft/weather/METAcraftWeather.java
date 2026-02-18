package nu.metacraft.weather;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.lib.METAcraftLib;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.weather.rainseason.RainSeasonCommand;

public class METAcraftWeather implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("metacraft-weather");
	@Override
	public void onInitialize() {
		RainSeasonCommand.init();
	}

	public static Identifier getId(String id) {
		return METAcraftLib.getID(id);
	}
}
