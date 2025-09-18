package nu.metacraft.portal_blocker.portal_type;

import net.minecraft.block.Blocks;
import net.minecraft.block.Portal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import nu.metacraft.portal_blocker.PortalState;

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
		if (type == PortalState.BlockingType.TRAVEL) {
			var allowPortals = server.getGameRules().get(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS);
			if (allowPortals.get() == netherBlocked) {
				allowPortals.set(!netherBlocked, server);
				server.onGameRuleUpdated(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS.getName(), allowPortals);
			}
		}
	}

}
