package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.TradeOfferExtensions;

@Mixin(MerchantContainer.class)
public class MixinMerchantInventory {
	@Shadow @Final private Merchant merchant;

	@ModifyExpressionValue(method = "updateSellItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffer;isOutOfStock()Z"))
	public boolean isDisabledForPlayer(boolean original, @Local MerchantOffer tradeOffer) {
		if (original) {
			// If disabled then it's disabled for all players.
			return true;
		}
		// Check if disabled for this player.
		TradeOfferExtensions ext = (TradeOfferExtensions) tradeOffer;
		int maxUsesPerPlayer = ext.metacraft$getMaxUsesPerPlayer();
		if (maxUsesPerPlayer == -1) {
			// No per player uses.
			return false;
		}
		Player player = this.merchant.getTradingPlayer();
		if (player == null) {
			// No customer...?
			return false;
		}
		int playerUses = ext.metacraft$getUsesPerPlayer().getOrDefault(player.getUUID(), 0);
		return playerUses >= maxUsesPerPlayer;
	}
}
