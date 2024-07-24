package se.datasektionen.mc.metacraft_weather;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_weather.rainseason.RainSeasonCommand;

public class METAcraftWeather implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("metacraft-weather");
	@Override
	public void onInitialize() {
		RainSeasonCommand.init();
	}
}
