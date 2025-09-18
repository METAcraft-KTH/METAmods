package nu.metacraft.plots.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.plots.item.PlotKey;
import nu.metacraft.plots.zone.PlotDataTypes;
import nu.metacraft.zones.compat.leukocyte.LeukocyteZoneManager;
import xyz.nucleoid.leukocyte.rule.ProtectionExclusions;

@Mixin(value = ProtectionExclusions.class, remap = false)
public class MixinProtectionExclusions {

	@Inject(method = "isExcluded", at = @At("RETURN"), cancellable = true)
	public void isExcluded(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		if (!player.getEntityWorld().isClient()) {
			LeukocyteZoneManager.getZoneFromExclusions((ProtectionExclusions) (Object) this).ifPresent(zone -> {
				if (zone.get(PlotDataTypes.PLAYER_PROTECTORATE).map(data -> data.isAllowed(player)).orElse(false)) {
					cir.setReturnValue(true);
					return;
				}

				for (int i = 0; i < player.getInventory().size(); i++) {
					var stack = player.getInventory().getStack(i);
					PlotKey.getZone(stack, player.getEntityWorld().getServer()).ifPresent(z -> {
						if (z.zone() == zone) {
							cir.setReturnValue(true);
						}
					});
				}
			});
		}
	}

}
