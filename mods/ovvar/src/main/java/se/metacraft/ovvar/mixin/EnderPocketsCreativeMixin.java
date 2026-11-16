package se.metacraft.ovvar.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.metacraft.ovvar.content.enchantment.EnderPockets;

/**
 * A creative client puts what it likes in its slots, including an Ender Pockets stand-in it took out of the
 * pockets: before the server puts the stack in the slot, a stand-in becomes the real stack from the ender chest
 * (or nothing), see {@link EnderPockets#creativeSlot}. Only reached for a player with infinite materials.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class EnderPocketsCreativeMixin {
	@Shadow
	public ServerPlayer player;

	@ModifyExpressionValue(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ServerboundSetCreativeModeSlotPacket;itemStack()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack ovvar$enderStandIn(ItemStack stack, @Local(argsOnly = true) ServerboundSetCreativeModeSlotPacket packet) {
		return EnderPockets.creativeSlot(player, packet.slotNum(), stack);
	}
}
