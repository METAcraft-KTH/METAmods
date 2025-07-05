package nu.metacraft.lib;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ChunkTicketType;

public class METAcraftTickets {

	public static final ChunkTicketType TELEPORT_MOB_SOON = register(
			"teleport_mob_soon",
			new ChunkTicketType(
					1, false, ChunkTicketType.Use.LOADING_AND_SIMULATION
			)
	);
	
	public static void init() {
		
	}
	
	private static ChunkTicketType register(String name, ChunkTicketType type) {
		return Registry.register(Registries.TICKET_TYPE, METAcraftLib.getID(name), type);
	}
	
}
