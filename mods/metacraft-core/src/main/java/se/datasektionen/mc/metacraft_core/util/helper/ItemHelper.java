package se.datasektionen.mc.metacraft_core.util.helper;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

public class ItemHelper {

	public static int getOffHandSlot(PlayerEntity player) {
		return player.getInventory().main.size() + player.getInventory().armor.size();
	}

	public static void syncInventorySlot(PlayerEntity player, int slot) {
		if (player instanceof ServerPlayerEntity p) {
			p.networkHandler.sendPacket(
					player.getInventory().createSlotSetPacket(slot)
			);
		}
	}

	public static void syncHandStack(PlayerEntity player, Hand hand) {
		if (hand.equals(Hand.MAIN_HAND)) {
			syncInventorySlot(player, player.getInventory().selectedSlot);
		} else {
			syncInventorySlot(player, getOffHandSlot(player));
		}
	}

	public static boolean isSelected(PlayerEntity player, int slot) {
		return slot == player.getInventory().selectedSlot || slot == getOffHandSlot(player);
	}

}
