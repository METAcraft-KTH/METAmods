package se.datasektionen.mc.portal_blocker.portal_type;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.block.Portal;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.portal_blocker.PortalBlocker;

public class PortalTypeRegistry {

	public static final Registry<PortalType> REGISTRY = FabricRegistryBuilder.<PortalType>createSimple(
			RegistryKey.ofRegistry(PortalBlocker.getID("portal-type"))
	).buildAndRegister();

	public static final PortalType NETHER = register("nether", new NetherPortalType());
	public static final PortalType END = register("end", new PortalType(
			(Portal) Blocks.END_PORTAL, Text.literal("A mysterious force rejects the Eye of Ender"), Text.literal("")
	));

	public static void init() {
		//Makes sure registry is loaded before registry is frozen. DO NOT REMOVE THIS FUNCTION!
	}

	private static PortalType register(String name, PortalType type) {
		//Register the default ones under the minecraft namespace to simplify the command.
		return Registry.register(REGISTRY, Identifier.ofVanilla(name), type);
	}

}
