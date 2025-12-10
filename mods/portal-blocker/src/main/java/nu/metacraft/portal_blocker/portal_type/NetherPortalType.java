package nu.metacraft.portal_blocker.portal_type;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import nu.metacraft.portal_blocker.PortalState;

public class NetherPortalType extends PortalType {
	private final Component outsideBorderMessage;

	public NetherPortalType() {
		super(
				(Portal) Blocks.NETHER_PORTAL,
				Component.literal("The portal could not activate"),
				Component.literal("The portal cannot teleport you")
		);
		this.outsideBorderMessage = Component.literal("The portal would generate outside of the world border");
	}

	@Override
	public void onGlobalStateChange(MinecraftServer server, boolean netherBlocked, PortalState.BlockingType type) {
		if (type == PortalState.BlockingType.TRAVEL) {
			boolean allowPortals = server.overworld().getGameRules().get(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS);
			if (allowPortals == netherBlocked) {
				server.overworld().getGameRules().set(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, !netherBlocked, server);
			}
		}
	}

	public Component getOutsideBorderMessage() {
		return this.outsideBorderMessage;
	}
}
