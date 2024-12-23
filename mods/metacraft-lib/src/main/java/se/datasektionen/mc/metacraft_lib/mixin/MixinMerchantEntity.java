package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.TradeOffer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_lib.extensions.TradeOfferExtensions;

@Mixin(MerchantEntity.class)
public class MixinMerchantEntity {
	@Shadow @Nullable private PlayerEntity customer;

	@Inject(method = "trade", at = @At("TAIL"))
	public void a(TradeOffer offer, CallbackInfo ci) {
		var ext = (TradeOfferExtensions) offer;
		if (ext.metacraft$getMaxUsesPerPlayer() != -1 && this.customer instanceof ServerPlayerEntity player) {
			int playerUses = ext.metacraft$getUsesPerPlayer().getOrDefault(player.getUuid(), 0);
			ext.metacraft$getUsesPerPlayer().put(player.getUuid(), playerUses + 1);
		}
	}
}
