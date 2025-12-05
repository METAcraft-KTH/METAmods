package nu.metacraft.portal_blocker.portal_type;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import nu.metacraft.portal_blocker.PortalBlocker;

public class PortalTypeRegistry {

	public static final Registry<PortalType> REGISTRY = FabricRegistryBuilder.<PortalType>createSimple(
			ResourceKey.createRegistryKey(PortalBlocker.getID("portal-type"))
	).buildAndRegister();

	public static final NetherPortalType NETHER = register("nether", new NetherPortalType());
	public static final PortalType END = register("end", new PortalType(
			(Portal) Blocks.END_PORTAL,
			Component.literal("A mysterious force rejects the Eye of Ender"),
			Component.literal("A mysterious force prevents the portal from teleporting you")
	));

	public static void init() {
		//Makes sure registry is loaded before registry is frozen. DO NOT REMOVE THIS FUNCTION!
	}

	private static <T extends PortalType> T register(String name, T type) {
		//Register the default ones under the minecraft namespace to simplify the command.
		return Registry.register(REGISTRY, Identifier.withDefaultNamespace(name), type);
	}

}
