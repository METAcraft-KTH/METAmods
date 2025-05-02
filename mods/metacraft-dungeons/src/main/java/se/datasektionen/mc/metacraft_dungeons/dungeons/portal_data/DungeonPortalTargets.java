package se.datasektionen.mc.metacraft_dungeons.dungeons.portal_data;

import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_core.portal.PortalTarget;
import se.datasektionen.mc.metacraft_core.portal.PortalTargetRegistry;

public class DungeonPortalTargets {


	public static final PortalTargetRegistry.PortalTargetType<Dungeon> DUNGEON = register(
			"dungeon", new PortalTargetRegistry.PortalTargetType<Dungeon>(Dungeon.CODEC)
	);

	private static <T extends PortalTargetRegistry.PortalTargetType<? extends PortalTarget>> T register(String id, T object) {
		return Registry.register(PortalTargetRegistry.REGISTRY, Identifier.ofVanilla(id), object);
	}

	public static void init() {

	}

}
