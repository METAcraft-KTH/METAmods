package se.datasektionen.mc.metacraft_season_4.boss;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.inventory.SlotRange;
import net.minecraft.inventory.SlotRanges;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.random.Random;
import se.metacraft.bosses.boss.attacks.AttackType;
import se.metacraft.bosses.boss.attacks.InstantAttack;

import java.util.*;
import java.util.stream.Stream;

public class InventoryShuffleAttack extends InstantAttack {

	public static final MapCodec<InventoryShuffleAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SlotRanges.CODEC.listOf().fieldOf("slots_to_shuffle").forGetter(t -> t.slotsToShuffle)
			).apply(instance, InventoryShuffleAttack::new)
	);

	protected final List<SlotRange> slotsToShuffle;

	public InventoryShuffleAttack(List<SlotRange> slotsToShuffle) {
		this.slotsToShuffle = slotsToShuffle;
	}

	public static InventoryShuffleAttack createSimple(
			boolean includeArmour, boolean includeOffhand, boolean includeCursor,
			boolean includeCrafting, boolean includeEnderChest
	) {
		List<SlotRange> slots = new ArrayList<>(
				List.of(
						SlotRanges.fromName("hotbar.*"),
						SlotRanges.fromName("inventory.*")
				)
		);
		if (includeArmour) {
			slots.add(SlotRanges.fromName("armor.head"));
			slots.add(SlotRanges.fromName("armor.chest"));
			slots.add(SlotRanges.fromName("armor.legs"));
			slots.add(SlotRanges.fromName("armor.feet"));
		}
		if (includeOffhand) {
			slots.add(SlotRanges.fromName("weapon.offhand"));
		}
		if (includeCursor) {
			slots.add(SlotRanges.fromName("player.cursor"));
		}
		if (includeCrafting) {
			slots.add(SlotRanges.fromName("player.crafting.*"));
		}
		if (includeEnderChest) {
			slots.add(SlotRanges.fromName("enderchest.*"));
		}
		return new InventoryShuffleAttack(slots);
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		ctx.boss().getPlayerTargets().forEach(player -> {
			shuffle(player, ctx.random());
		});
	}

	private void shuffle(ServerPlayerEntity player, Random random) {
		shuffle(player, random, slotsToShuffle.stream());
	}

	private static void shuffle(ServerPlayerEntity player, Random random, Stream<SlotRange> slotRanges) {
		IntList fromSlots = IntArrayList.toList(
				slotRanges.flatMapToInt(
						range -> range.getSlotIds().intStream()
				).distinct()
		);
		IntList toSlots = Util.shuffle(fromSlots.intStream(), random);
		for (int i = 0; i < fromSlots.size(); i++) {
			int fromSlot = fromSlots.getInt(i);
			int toSlot = toSlots.getInt(i);
			var from = player.getStackReference(fromSlot);
			var to = player.getStackReference(toSlot);
			var fromStack = from.get();
			var toStack = to.get();
			if (fromSlot == toSlot) continue;
			if (fromStack.isEmpty() && toStack.isEmpty()) continue;
			var f = fromStack.copy();
			if (from.set(toStack.copy())) {
				if (to.set(f)) { //Empty the item stacks in case GUI:s still have them cached.
					fromStack.setCount(0);
					toStack.setCount(0);
				} else {
					from.set(f); //Since we replaced the from slot it's best to empty the original.
					fromStack.setCount(0);
				}
			}
		}
		player.playerScreenHandler.syncState();
	}

	@Override
	public AttackType getType() {
		return Season4Attacks.INVENTORY_SHUFFLE;
	}
}
