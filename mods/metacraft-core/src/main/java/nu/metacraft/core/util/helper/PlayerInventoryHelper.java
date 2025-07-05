package nu.metacraft.core.util.helper;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.Map;
import java.util.stream.Collectors;

public class PlayerInventoryHelper {

	private static Int2ObjectMap<EquipmentSlot> slots = PlayerInventory.EQUIPMENT_SLOTS;
	private static Object2IntMap<EquipmentSlot> reverseSlots = getReversed();

	private static void update() {
		if (slots != PlayerInventory.EQUIPMENT_SLOTS) {
			slots = PlayerInventory.EQUIPMENT_SLOTS;
			reverseSlots = getReversed();
		}
	}

	private static Object2IntMap<EquipmentSlot> getReversed() {
		return slots.int2ObjectEntrySet().stream().collect(
				Collectors.<Int2ObjectMap.Entry<EquipmentSlot>, EquipmentSlot, Integer, Object2IntMap<EquipmentSlot>>toMap(
						Map.Entry::getValue, Int2ObjectMap.Entry::getIntKey,
						(lhs, rhs) -> lhs,
						Object2IntOpenHashMap::new
				)
		);
	}

	public static int getOffHandSlot() {
		update();
		return reverseSlots.getInt(EquipmentSlot.OFFHAND);
	}

	public static int getMainHandSlot(PlayerEntity player) {
		return player.getInventory().getSelectedSlot();
	}

	public static int getHandSlot(PlayerEntity player, Hand hand) {
		return switch (hand) {
			case MAIN_HAND -> getMainHandSlot(player);
			case OFF_HAND -> getOffHandSlot();
		};
	}

	public static int getSlot(PlayerEntity player, EquipmentSlot slot) {
		update();
		if (slot == EquipmentSlot.MAINHAND) {
			return getMainHandSlot(player);
		} else {
			return reverseSlots.containsKey(slot) ? reverseSlots.getInt(slot) : -1;
		}
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
			syncInventorySlot(player, getOffHandSlot());
		}
	}

	public static boolean isSelected(PlayerEntity player, int slot) {
		return slot == getMainHandSlot(player) || slot == getOffHandSlot();
	}

}
