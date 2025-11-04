package nu.metacraft.dungeons;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

public class DungeonTickets {

	public static final TicketType DUNGEON_ENTRANCE = register(
			"dungeon_entrance",
			new TicketType(
					0, TicketType.FLAG_LOADING
			)
	);

	public static void init() {

	}

	private static TicketType register(String name, TicketType type) {
		return Registry.register(BuiltInRegistries.TICKET_TYPE, METAcraftDungeons.getID(name), type);
	}

}
