package nu.metacraft.lib;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

public class METAcraftTickets {

	public static final TicketType TELEPORT_MOB_SOON = register(
			"teleport_mob_soon",
			new TicketType(
					1, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
			)
	);
	
	public static void init() {
		
	}
	
	private static TicketType register(String name, TicketType type) {
		return Registry.register(BuiltInRegistries.TICKET_TYPE, METAcraftLib.getID(name), type);
	}
	
}
