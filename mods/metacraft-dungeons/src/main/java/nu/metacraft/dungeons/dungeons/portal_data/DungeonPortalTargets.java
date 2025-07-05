package nu.metacraft.dungeons.dungeons.portal_data;

import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import nu.metacraft.core.portal.PortalTarget;
import nu.metacraft.core.portal.PortalTargetRegistry;

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
