package se.datasektionen.mc.metacraft_core.util.helper;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

public class PlayerInventoryHelper {

	public static int getOffHandSlot(PlayerEntity player) {
		return player.getInventory().main.size() + player.getInventory().armor.size();
	}

	public static int getMainHandSlot(PlayerEntity player) {
		return player.getInventory().selectedSlot;
	}

	public static int getHandSlot(PlayerEntity player, Hand hand) {
		return switch (hand) {
			case MAIN_HAND -> getMainHandSlot(player);
			case OFF_HAND -> getOffHandSlot(player);
		};
	}

	public static int getSlot(PlayerEntity player, EquipmentSlot slot) {
		return switch (slot) {
			case MAINHAND -> getMainHandSlot(player);
			case OFFHAND -> getOffHandSlot(player);
			case HEAD, CHEST, LEGS, FEET -> slot.getOffsetEntitySlotId(player.getInventory().main.size());
			case BODY -> -1;
		};
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
			syncInventorySlot(player, getMainHandSlot(player));
		} else {
			syncInventorySlot(player, getOffHandSlot(player));
		}
	}

	public static boolean isSelected(PlayerEntity player, int slot) {
		return slot == getMainHandSlot(player) || slot == getOffHandSlot(player);
	}

}
