package nu.metacraft.info_commands;

import net.minecraft.server.players.PlayerList;

public class Helper {

	public static void resendCommandTreeToAllPlayers(PlayerList manager) {
		manager.getPlayers().forEach(manager::sendPlayerPermissionLevel);
	}

}
