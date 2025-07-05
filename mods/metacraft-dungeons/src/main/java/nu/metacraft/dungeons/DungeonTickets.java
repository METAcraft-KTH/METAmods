package nu.metacraft.dungeons;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ChunkTicketType;

public class DungeonTickets {

	public static final ChunkTicketType DUNGEON_ENTRANCE = register(
			"dungeon_entrance",
			new ChunkTicketType(
					0, false, ChunkTicketType.Use.LOADING
			)
	);

	public static void init() {

	}

	private static ChunkTicketType register(String name, ChunkTicketType type) {
		return Registry.register(Registries.TICKET_TYPE, METAcraftDungeons.getID(name), type);
	}

}
