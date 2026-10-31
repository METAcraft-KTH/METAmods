package se.metacraft.player_bosses;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.player_bosses.command.PlayerBossCommand;
import se.metacraft.player_bosses.state.PlayerBossState;

public class PlayerBosses implements ModInitializer {

	public static final String NAMESPACE = "metacraft";

	public static final Logger LOGGER = LogManager.getLogger("metacraft-bosses");

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			PlayerBossCommand.register(dispatcher, buildContext);
		});
		ServerTickEvents.END_LEVEL_TICK.register(world -> {
			PlayerBossState.getInstance(world).ifPresent(inst -> inst.tick(world));
		});
	}

}
