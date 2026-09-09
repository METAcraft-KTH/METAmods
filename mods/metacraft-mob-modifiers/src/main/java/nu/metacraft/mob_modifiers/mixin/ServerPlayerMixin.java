package nu.metacraft.mob_modifiers.mixin;

import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nu.metacraft.mob_modifiers.extensions.MerchantOfferExtensions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {

	public ServerPlayerMixin(Level world, GameProfile profile) {
		super(world, profile);
	}


	@ModifyVariable(method = "sendMerchantOffers", at = @At(value = "HEAD"), argsOnly = true)
	public MerchantOffers modifyTradeOfferList(MerchantOffers tradeOfferList) {
		Player playerEntity = (Player) this;
		MerchantOffers newOffers = new MerchantOffers();
		for (MerchantOffer offer : tradeOfferList) {
			var ext = ((MerchantOfferExtensions) offer);
			int maxUsesPerPlayer = ext.metacraft$getMaxUsesPerPlayer();
			if (maxUsesPerPlayer == -1) {
				newOffers.add(offer);
				continue;
			}
			int playerUses = ext.metacraft$getUsesPerPlayer().getOrDefault(playerEntity.getUUID(), 0);
			int globalUsesUntilDisabled = offer.getMaxUses() - offer.getUses();
			int playerUsesUntilDisabled = maxUsesPerPlayer - playerUses;
			if (globalUsesUntilDisabled <= playerUsesUntilDisabled) {
				// The global max uses will be hit before the player one. So send the global one.
				newOffers.add(offer);
				continue;
			}
			// Otherwise modify the max uses and uses to be the per-player ones.
			MerchantOffer copy = offer.copy();
			var copyExt = ((MerchantOfferExtensions) copy);
			copyExt.metacraft$setUses(playerUses);
			copyExt.metacraft$setMaxUses(maxUsesPerPlayer);
			copyExt.metacraft$setMaxUsesPerPlayer(maxUsesPerPlayer);
			copyExt.metacraft$getUsesPerPlayer().putAll(ext.metacraft$getUsesPerPlayer());
			newOffers.add(copy);
		}
		return newOffers;
	}
}
