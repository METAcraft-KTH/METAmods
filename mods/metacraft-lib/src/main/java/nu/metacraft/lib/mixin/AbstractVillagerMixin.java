package nu.metacraft.lib.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import nu.metacraft.lib.extensions.MerchantOfferExtensions;

@Mixin(AbstractVillager.class)
public class AbstractVillagerMixin {
	@Shadow @Nullable private Player tradingPlayer;

	@Inject(method = "notifyTrade", at = @At("TAIL"))
	public void a(MerchantOffer offer, CallbackInfo ci) {
		var ext = (MerchantOfferExtensions) offer;
		if (ext.metacraft$getMaxUsesPerPlayer() != -1 && this.tradingPlayer instanceof ServerPlayer player) {
			int playerUses = ext.metacraft$getUsesPerPlayer().getOrDefault(player.getUUID(), 0);
			ext.metacraft$getUsesPerPlayer().put(player.getUUID(), playerUses + 1);
		}
	}
}
