package nu.metacraft.portal_blocker.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.context.UseOnContext;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;

@Mixin(EnderEyeItem.class)
public class EnderEyeItemMixin {
	@Inject(at = @At("HEAD"), method = "useOn", cancellable = true)
	private void useOnBlock(UseOnContext context, CallbackInfoReturnable<InteractionResult> info) {
		if (context.getLevel() instanceof ServerLevel world) {
			if (PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					PortalTypeRegistry.END, world.dimension(), PortalState.BlockingType.ACTIVATION, context.getClickedPos()
			)) {
				Player player = context.getPlayer();
				if (player != null) {
					PortalTypeRegistry.END.getCreationMessage().ifPresent(
							msg -> player.displayClientMessage(msg, true)
					);
				}
				info.setReturnValue(InteractionResult.FAIL);
			}
		}
	}
}