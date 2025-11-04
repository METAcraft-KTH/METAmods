package nu.metacraft.portal_blocker.portal_type;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
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
			var allowPortals = server.getGameRules().getRule(GameRules.RULE_ALLOW_NETHER);
			if (allowPortals.get() == netherBlocked) {
				allowPortals.set(!netherBlocked, server);
				server.onGameRuleChanged(GameRules.RULE_ALLOW_NETHER.getId(), allowPortals);
			}
		}
	}

	public Component getOutsideBorderMessage() {
		return this.outsideBorderMessage;
	}
}
