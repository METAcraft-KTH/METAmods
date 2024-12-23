package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.extensions.TradeOfferExtensions;

@Mixin(MerchantInventory.class)
public class MixinMerchantInventory {
	@Shadow @Final private Merchant merchant;

	@ModifyExpressionValue(method = "updateOffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/village/TradeOffer;isDisabled()Z"))
	public boolean isDisabledForPlayer(boolean original, @Local TradeOffer tradeOffer) {
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
		PlayerEntity player = this.merchant.getCustomer();
		if (player == null) {
			// No customer...?
			return false;
		}
		int playerUses = ext.metacraft$getUsesPerPlayer().getOrDefault(player.getUuid(), 0);
		return playerUses >= maxUsesPerPlayer;
	}
}
