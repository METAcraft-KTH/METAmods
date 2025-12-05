package nu.metacraft.portal_blocker.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

	@Inject(method = "onGameRuleChanged", at = @At("RETURN"))
	public <T> void onGameRuleChanged(GameRule<@NotNull T> gameRule, T value, CallbackInfo ci) {
		if (gameRule == GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS) {
			var server = (MinecraftServer) (Object) this;
			var state = PortalBlockerSettings.getInstance(server);
			boolean netherBlocked = state.isPortalBlockedGlobally(PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL);
			boolean isNetherAllowed = (Boolean) value;
			if (isNetherAllowed == netherBlocked) {
				state.setPortalBlockedGlobally(PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL, !isNetherAllowed);
			}
		}
	}

}
