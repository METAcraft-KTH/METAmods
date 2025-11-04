package nu.metacraft.plots.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.player.Player;
import nu.metacraft.plots.item.PlotKey;
import nu.metacraft.plots.zone.PlotDataTypes;
import nu.metacraft.zones.compat.leukocyte.LeukocyteZoneManager;
import xyz.nucleoid.leukocyte.rule.ProtectionExclusions;

@Mixin(value = ProtectionExclusions.class, remap = false)
public class MixinProtectionExclusions {

	@Inject(method = "isExcluded", at = @At("RETURN"), cancellable = true)
	public void isExcluded(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (!player.level().isClientSide()) {
			LeukocyteZoneManager.getZoneFromExclusions((ProtectionExclusions) (Object) this).ifPresent(zone -> {
				if (zone.get(PlotDataTypes.PLAYER_PROTECTORATE).map(data -> data.isAllowed(player)).orElse(false)) {
					cir.setReturnValue(true);
					return;
				}

				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					var stack = player.getInventory().getItem(i);
					PlotKey.getZone(stack, player.level().getServer()).ifPresent(z -> {
						if (z.zone() == zone) {
							cir.setReturnValue(true);
						}
					});
				}
			});
		}
	}

}
