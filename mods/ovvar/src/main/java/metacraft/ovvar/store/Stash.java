package metacraft.ovvar.store;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.PatchItem;
import metacraft.ovvar.content.Patches;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DeathProtection;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The stash: the patches a player owns but has not sewn, kept in their wardrobe and so on every
 * server. Patches are earned into it ({@link #grant}, with the totem-of-undying flourish and a
 * word in chat), taken out of it as items where the config allows ({@link #withdraw}), put back
 * ({@link #deposit}), and — on a server where an item would be lost, a minigame server — any patch
 * item that turns up in an inventory is banked into it at once ({@link #bank}).
 */
public final class Stash {
	private Stash() {}

	/** Players with a bank write in flight, so a stack is not banked twice while the store answers. */
	private static final Set<UUID> BANKING = new HashSet<>();

	private static StashConfig config() {
		return OvvarConfig.get().stash();
	}

	// ---- earning

	/** {@code count} more of a patch in the player's stash, with the flourish and the explanation on success. */
	public static void grant(ServerPlayer player, Patches.Patch patch, int count, Consumer<Wardrobes.Outcome> done) {
		Wardrobes.update(player.getUUID(), w -> w.add(patch, count), outcome -> {
			if (outcome == Wardrobes.Outcome.OK) {
				celebrate(player, patch);
				explain(player, patch, count);
			}
			done.accept(outcome);
		});
	}

	/**
	 * The totem-of-undying animation with the patch's art instead of the totem. The client animates
	 * whatever is in its hands that carries the death-protection component, so the player is shown
	 * their off-hand holding such a stack for the one packet in between, and then the real one again.
	 * Vanilla clients, no mixin; the sound and particles come with the event.
	 */
	public static void celebrate(ServerPlayer player, Patches.Patch patch) {
		PatchItem item = ModContent.patchItem(patch);
		ItemStack shown = new ItemStack(Items.PAPER);
		shown.set(DataComponents.ITEM_MODEL, item.getPolymerItemModel(new ItemStack(item), null, null));
		shown.set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING);
		shown.set(DataComponents.CUSTOM_NAME, Component.literal(patch.name()));
		int offhand = 45;   // the off-hand slot of the player's own inventory menu
		player.connection.send(new ClientboundContainerSetSlotPacket(player.inventoryMenu.containerId, player.inventoryMenu.incrementStateId(), offhand, shown));
		player.connection.send(new ClientboundEntityEventPacket(player, EntityEvent.PROTECTED_FROM_DEATH));
		player.inventoryMenu.sendAllDataToRemote();
	}

	/** What just happened and what the stash is, in chat. */
	public static void explain(ServerPlayer player, Patches.Patch patch, int count) {
		player.sendSystemMessage(Component.literal("You earned " + (count == 1 ? "a " : count + " × ") + patch.name() + " patch!")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (!config().explainInChat()) return;
		player.sendSystemMessage(Component.literal("It is in your patch stash, which follows you to every server: ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal("/ovvar stash").withStyle(ChatFormatting.YELLOW)));
		if (config().minigameServer()) {
			player.sendSystemMessage(Component.literal("Sew it on your ovve on a survival server.").withStyle(ChatFormatting.GRAY));
		} else {
			player.sendSystemMessage(Component.literal(config().canWithdraw()
					? "Open the stash and click the patch to take it out, then sew it on an armour stand wearing your ovve, or trade it."
					: "Open the stash and click the patch to sew it on your ovve.").withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- items in, items out

	/**
	 * A patch item in a player's inventory, on a server that banks them: taken away and put in the
	 * stash (the item goes first, and comes back if the store says no). Called every inventory tick;
	 * does nothing until the wardrobe is loaded, and never twice at once.
	 */
	public static void bank(ServerPlayer player, ItemStack stack) {
		if (!config().banksOnPickup() || !(stack.getItem() instanceof PatchItem item)) return;
		if (player.isCreative() && !config().bankInCreative()) return;
		if (stack.has(ModComponents.SESSION)) return;   // the stash session's own fake item
		UUID id = player.getUUID();
		if (BANKING.contains(id)) return;
		if (!Wardrobes.loaded(id)) {
			Wardrobes.fetch(id);
			return;
		}
		int count = stack.getCount();
		stack.setCount(0);
		BANKING.add(id);
		Wardrobes.update(id, w -> w.add(item.patch, count), outcome -> {
			BANKING.remove(id);
			if (outcome == Wardrobes.Outcome.OK) {
				celebrate(player, item.patch);
				explain(player, item.patch, count);
			} else {
				give(player, item.patch, count);
				if (outcome != Wardrobes.Outcome.NOT_LOADED) {
					Ovvar.LOGGER.warn("[ovvar] stash: banking {} × {} for {}: {}", count, item.patch.id(), player.getName().getString(), outcome);
				}
			}
		});
	}

	/** One patch out of the stash and into the hand, where the config allows it. */
	public static void withdraw(ServerPlayer player, Patches.Patch patch, Consumer<String> reply) {
		if (!config().canWithdraw()) {
			reply.accept("Patches cannot be taken out of the stash on this server");
			return;
		}
		String refusal = OwnedSewing.editingRefusal(player);
		if (refusal != null) {
			reply.accept(refusal);
			return;
		}
		UUID id = player.getUUID();
		if (Wardrobes.current(id).count(patch) == 0) {
			reply.accept("No " + patch.name() + " in the stash");
			return;
		}
		Wardrobes.update(id, w -> w.count(patch) > 0 ? w.add(patch, -1) : null, outcome -> {
			switch (outcome) {
				case OK -> {
					give(player, patch, 1);
					reply.accept(patch.name() + " taken out of the stash");
				}
				case REJECTED -> reply.accept("No " + patch.name() + " in the stash");
				case CONFLICT -> reply.accept("The stash changed on another server, try again");
				case NOT_LOADED -> reply.accept("Your wardrobe is still loading, try again in a moment");
				case UNREACHABLE -> reply.accept("The wardrobe store cannot be reached, try again later");
			}
		});
	}

	/** Every patch item in the player's inventory into the stash (the items go first, and come back if the store says no). */
	public static void deposit(ServerPlayer player, Consumer<String> reply) {
		if (config().minigameServer() && !config().banksOnPickup()) {
			reply.accept("Not on this server");
			return;
		}
		UUID id = player.getUUID();
		if (!Wardrobes.loaded(id)) {
			Wardrobes.fetch(id);
			reply.accept("Your wardrobe is still loading, try again in a moment");
			return;
		}
		java.util.Map<Patches.Patch, Integer> taken = new java.util.LinkedHashMap<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof PatchItem item && !stack.has(ModComponents.SESSION)) {
				taken.merge(item.patch, stack.getCount(), Integer::sum);
				player.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		if (taken.isEmpty()) {
			reply.accept("No patches in your inventory");
			return;
		}
		Wardrobes.update(id, w -> {
			Wardrobe out = w;
			for (var entry : taken.entrySet()) out = out.add(entry.getKey(), entry.getValue());
			return out;
		}, outcome -> {
			if (outcome == Wardrobes.Outcome.OK) {
				int n = taken.values().stream().mapToInt(Integer::intValue).sum();
				reply.accept(n + " patch(es) put in the stash");
			} else {
				taken.forEach((patch, count) -> give(player, patch, count));
				reply.accept("Could not reach the stash (" + outcome + "), your patches are back");
			}
		});
	}

	/** {@code count} of a patch as items into the player's inventory, or dropped at their feet. */
	public static void give(ServerPlayer player, Patches.Patch patch, int count) {
		ItemStack stack = new ItemStack(ModContent.patchItem(patch), count);
		if (!player.getInventory().add(stack)) player.drop(stack, false, Prediction.SERVER_ONLY);
	}
}
