package nu.metacraft.core.util.helper;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class PlayerInventoryHelper {

	private static Int2ObjectMap<EquipmentSlot> slots = Inventory.EQUIPMENT_SLOT_MAPPING;
	private static Object2IntMap<EquipmentSlot> reverseSlots = getReversed();

	private static void update() {
		if (slots != Inventory.EQUIPMENT_SLOT_MAPPING) {
			slots = Inventory.EQUIPMENT_SLOT_MAPPING;
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

	public static int getMainHandSlot(Player player) {
		return player.getInventory().getSelectedSlot();
	}

	public static int getHandSlot(Player player, InteractionHand hand) {
		return switch (hand) {
			case MAIN_HAND -> getMainHandSlot(player);
			case OFF_HAND -> getOffHandSlot();
		};
	}

	public static int getSlot(Player player, EquipmentSlot slot) {
		update();
		if (slot == EquipmentSlot.MAINHAND) {
			return getMainHandSlot(player);
		} else {
			return reverseSlots.containsKey(slot) ? reverseSlots.getInt(slot) : -1;
		}
	}

	public static void syncInventorySlot(Player player, int slot) {
		if (player instanceof ServerPlayer p) {
			p.connection.send(
					player.getInventory().createInventoryUpdatePacket(slot)
			);
		}
	}

	public static void syncHandStack(Player player, InteractionHand hand) {
		if (hand.equals(InteractionHand.MAIN_HAND)) {
			syncInventorySlot(player, getMainHandSlot(player));
		} else {
			syncInventorySlot(player, getOffHandSlot());
		}
	}

	public static boolean isSelected(Player player, int slot) {
		return slot == getMainHandSlot(player) || slot == getOffHandSlot();
	}

}
