package se.datasektionen.mc.portal_blocker.portal_type;

import net.minecraft.block.Blocks;
import net.minecraft.block.Portal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.text.Text;
import se.datasektionen.mc.portal_blocker.PortalState;
import se.datasektionen.mc.portal_blocker.mixin.AccessorAbstractPropertiesHandler;
import se.datasektionen.mc.portal_blocker.mixin.AccessorMinecraftDedicatedServer;
import se.datasektionen.mc.portal_blocker.mixin.AccessorServerPropertiesHandler;

public class NetherPortalType extends PortalType {

	public NetherPortalType() {
		super(
				(Portal) Blocks.NETHER_PORTAL,
				Text.literal("The portal could not activate"),
				Text.literal("The portal cannot teleport you")
		);
	}

	@Override
	public void onGlobalStateChange(MinecraftServer server, boolean netherBlocked, PortalState.BlockingType type) {
		if (type == PortalState.BlockingType.TRAVEL && server instanceof DedicatedServer dedicatedServer) {
			((AccessorServerPropertiesHandler) dedicatedServer.getProperties()).setAllowNether(!netherBlocked);
			((AccessorAbstractPropertiesHandler) dedicatedServer.getProperties()).getProperties().put(
					"allow-nether", Boolean.toString(!netherBlocked)
			);
			((AccessorMinecraftDedicatedServer) dedicatedServer).getPropertiesLoader().store();
		}
	}

}
