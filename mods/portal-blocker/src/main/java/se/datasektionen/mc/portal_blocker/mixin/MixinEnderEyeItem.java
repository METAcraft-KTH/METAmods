package se.datasektionen.mc.portal_blocker.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EnderEyeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.portal_blocker.PortalBlockerSettings;
import se.datasektionen.mc.portal_blocker.PortalState;
import se.datasektionen.mc.portal_blocker.portal_type.PortalTypeRegistry;

@Mixin(EnderEyeItem.class)
public class MixinEnderEyeItem {
	@Inject(at = @At("HEAD"), method = "useOnBlock", cancellable = true)
	private void useOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> info) {
		if (context.getWorld() instanceof ServerWorld world) {
			if (PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					PortalTypeRegistry.END, world.getRegistryKey(), PortalState.BlockingType.CREATION, context.getBlockPos()
			)) {
				PlayerEntity player = context.getPlayer();
				if (player != null) {
					PortalTypeRegistry.END.getCreationMessage().ifPresent(
							msg -> player.sendMessage(msg, true)
					);
				}
				info.setReturnValue(ActionResult.FAIL);
			}
		}
	}
}