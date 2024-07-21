package se.datasektionen.mc.info_commands;

import net.minecraft.server.PlayerManager;

public class Helper {

	public static void resendCommandTreeToAllPlayers(PlayerManager manager) {
		manager.getPlayerList().forEach(manager::sendCommandTree);
	}

}
