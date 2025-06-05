package nu.metacraft.minigame_util;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MinigameUtil implements ModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("metacraft-minigame-util");

    @Override
    public void onInitialize() {
        Commands.init();
        MinigameGameRules.init();
    }
}
