package se.metacraft.fake_players;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class METAcraftFakePlayers implements ModInitializer {
	public static final String MOD_ID = "metacraft-fake-players";
	public static final Logger LOGGER = LogManager.getLogger("METAcraft-Fake-Players");

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> FakePlayersCommand.register(dispatcher)
		);
		ServerTickEvents.END_SERVER_TICK.register(Playback::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(Playback::stopAll);
	}
}
