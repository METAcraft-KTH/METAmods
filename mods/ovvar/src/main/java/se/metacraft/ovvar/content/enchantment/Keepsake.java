package se.metacraft.ovvar.content.enchantment;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import se.metacraft.ovvar.content.ModContent;

import java.util.ArrayList;
import java.util.List;

/** Keepsake: an ovve with it stays with its wearer through death, pockets and all. */
public final class Keepsake {
	private Keepsake() {}

	public record Kept(int slot, ItemStack stack) {
		public static final Codec<Kept> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("slot").forGetter(Kept::slot),
				ItemStack.CODEC.fieldOf("stack").forGetter(Kept::stack)).apply(i, Kept::new));
	}

	public static final AttachmentType<List<Kept>> KEPT = AttachmentRegistry.create(ModContent.id("keepsakes"),
			b -> b.persistent(Kept.CODEC.listOf()));

	/**
	 * Called as the death drop starts (keepInventory off), before vanilla or a grave mod empties the
	 * inventory: every Keepsake ovve, wherever it is, moves into {@link #KEPT} with its slot, so it is not
	 * dropped. One with Curse of Vanishing stays, for vanilla to destroy as before. The companion top
	 * is not one of them (it holds no enchantment, and {@code OvveTop} clears it on death already); the
	 * cuffs are virtual and never in the inventory.
	 */
	public static void stash(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		List<Kept> kept = new ArrayList<>(player.getAttachedOrElse(KEPT, List.of()));
		int before = kept.size();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (OvveEnchantments.level(stack, OvveEnchantments.KEEPSAKE) > 0
					&& !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				kept.add(new Kept(slot, stack.copy()));
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		if (kept.size() > before) player.setAttached(KEPT, kept);
	}

	/**
	 * Gives the kept stacks to the respawned player: each to its own slot if empty, then anywhere in the
	 * inventory, then dropped at their feet. Nothing is ever deleted. Own slots are all tried first, so
	 * one ovve's fallback cannot take another's slot.
	 */
	public static void restore(ServerPlayer from, ServerPlayer to) {
		List<Kept> kept = from.getAttached(KEPT);
		if (kept == null) return;
		from.removeAttached(KEPT);
		Inventory inventory = to.getInventory();
		List<ItemStack> homeless = new ArrayList<>();
		for (Kept k : kept) {
			ItemStack stack = k.stack();
			if (stack.isEmpty()) continue;
			if (k.slot() >= 0 && k.slot() < inventory.getContainerSize() && inventory.getItem(k.slot()).isEmpty()) {
				inventory.setItem(k.slot(), stack);
			} else {
				homeless.add(stack);
			}
		}
		for (ItemStack stack : homeless) {
			if (!inventory.add(stack)) to.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	public static void init() {
		// After the respawn, not COPY_FROM: that fires before the new player is moved to the spawn point,
		// where a full-inventory drop would land at the origin.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) restore(oldPlayer, newPlayer);
		});
	}
}
