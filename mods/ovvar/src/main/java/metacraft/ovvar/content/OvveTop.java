package metacraft.ovvar.content;

import com.mojang.authlib.GameProfile;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import metacraft.ovvar.pack.EquipmentJson;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import metacraft.ovvar.pack.Trims;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the chest slot in step with the ovve in the legs slot: a companion top while the ovve's
 * top is up and the slot is free, nothing of ours otherwise. Runs from the ovve's inventory tick
 * plus the edges the tick can't see: the stack on the cursor, a drop, and death.
 */
public final class OvveTop {
	private OvveTop() {}

	/** Vanilla chestplate materials we composite the ovve top under; metals only, as leather's dye is its own colour. */
	public static final List<String> MATERIALS = OvveFeet.MATERIALS;
	private static final Set<String> MATERIAL_SET = Set.copyOf(MATERIALS);

	public static void init() {
		// Chestplates marked as worn over an ovve, on their way to a client: shown carrying the top underneath.
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register(OvveTop::wrap);
		// A player who swaps a chestplate into the slot is holding the top on the cursor, where nothing ticks.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.containerMenu.getCarried().getItem() instanceof OvveTopItem) {
					player.containerMenu.setCarried(ItemStack.EMPTY);
					player.containerMenu.broadcastChanges();
					// Force the client's cursor empty too: after a swap it keeps predicting the item back,
					// and with the icon blanked it reads as an invisible stuck item. Slot -1 is the cursor.
					player.connection.send(new ClientboundContainerSetSlotPacket(-1, player.containerMenu.incrementStateId(), -1, ItemStack.EMPTY));
				}
				// A chestplate left wrapped after the ovve or its top went away is unwrapped.
				ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
				if (chest.has(ModComponents.WRAPPED_TOP) && !wantsTop(player.getItemBySlot(EquipmentSlot.LEGS))) {
					chest.remove(ModComponents.WRAPPED_TOP);
				}
			}
		});
		// Dropped from the cursor it would become an item on the ground; it never gets that far.
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, loadedFromDisk) ->
				!(entity instanceof ItemEntity item && item.getItem().getItem() instanceof OvveTopItem));
		// Clear it before the inventory is dropped; with keepInventory it stays and the tick re-checks it.
		ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
			if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof OvveTopItem) {
				player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			}
			return true;
		});
	}

	static boolean wantsTop(ItemStack legs) {
		return legs.getItem() instanceof OvveItem && OvveItem.topUp(legs);
	}

	/** The material of a chestplate we can composite the top under, or null. */
	public static String material(ItemStack chest) {
		Equippable equippable = chest.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != EquipmentSlot.CHEST || equippable.assetId().isEmpty()) return null;
		var asset = equippable.assetId().get().identifier();
		return asset.getNamespace().equals("minecraft") && MATERIAL_SET.contains(asset.getPath()) ? asset.getPath() : null;
	}

	/** Called every tick for an ovve worn in the legs slot. */
	static void sync(LivingEntity wearer, ItemStack ovve) {
		ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
		if (!OvveItem.topUp(ovve)) {
			if (chest.getItem() instanceof OvveTopItem) wearer.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			if (chest.has(ModComponents.WRAPPED_TOP)) chest.remove(ModComponents.WRAPPED_TOP);
			return;
		}
		if (chest.isEmpty()) {
			wearer.setItemSlot(EquipmentSlot.CHEST, topFor(ovve));
		} else if (chest.getItem() instanceof OvveTopItem) {
			ItemStack want = topFor(ovve);
			if (!ItemStack.matches(chest, want)) wearer.setItemSlot(EquipmentSlot.CHEST, want);
		} else if (material(chest) != null) {
			// A real chestplate of a known material: shown to clients carrying the top underneath, whose
			// sleeves and collar show through the armour's open arms and neck (see OvveTop::wrap).
			if (!wearer.getUUID().equals(chest.get(ModComponents.WRAPPED_TOP))) chest.set(ModComponents.WRAPPED_TOP, wearer.getUUID());
		} else {
			// Unknown or modded armour whose layers we don't have: leave it be, the top hides under it.
			if (chest.has(ModComponents.WRAPPED_TOP)) chest.remove(ModComponents.WRAPPED_TOP);
		}
	}

	/** A chestplate marked wrapped, on its way to a client: our composite asset and the top's dye colour, its own kept. */
	private static ItemStack wrap(ItemStack original, ItemStack client, PacketContext context) {
		UUID wearerId = original.get(ModComponents.WRAPPED_TOP);
		if (wearerId == null) return client;
		MinecraftServer server = context == null ? null : context.get(PacketContext.SERVER_INSTANCE);
		LivingEntity wearer = findWearer(server, wearerId);
		if (wearer == null) return client;
		ItemStack legs = wearer.getItemBySlot(EquipmentSlot.LEGS);
		if (!(legs.getItem() instanceof OvveItem ovve) || !OvveItem.topUp(legs)
				|| !ItemStack.isSameItemSameComponents(wearer.getItemBySlot(EquipmentSlot.CHEST), original)) return client;
		String material = material(original);
		if (material == null) return client;
		dressChest(client, legs, ovve.chapter, material, context);
		return client;
	}

	private static LivingEntity findWearer(MinecraftServer server, UUID id) {
		if (server == null) return null;
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null) return player;
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof LivingEntity living) return living;
		}
		return null;
	}

	/** The client's chestplate re-pointed at the composite asset (ovve top under the chestplate) with the top's dye. */
	private static void dressChest(ItemStack client, ItemStack ovve, Chapter chapter, String material, PacketContext context) {
		GameProfile profile = context == null ? null : context.get(PacketContext.GAME_PROFILE);
		// dye only: the wrap draws the instant patches, never the pack combo, so don't queue a build for it.
		Looks.Look look = Looks.look(ovve, Piece.TOP, profile == null ? null : profile.id(), false);
		Equippable base = client.get(DataComponents.EQUIPPABLE);
		if (base == null) return;
		client.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
				.setEquipSound(base.equipSound())
				.setAsset(EquipmentJson.chestAsset(chapter, material))
				.setDamageOnHurt(base.damageOnHurt())
				.setSwappable(base.swappable())
				.setDispensable(base.dispensable())
				.build());
		TooltipDisplay display = client.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		if (look.dye() != 0) {
			client.set(DataComponents.DYED_COLOR, new DyedItemColor(look.dye()));
			display = display.withHidden(DataComponents.DYED_COLOR, true);
		} else {
			client.remove(DataComponents.DYED_COLOR);
		}
		client.set(DataComponents.TOOLTIP_DISPLAY, display);
	}

	private static ItemStack topFor(ItemStack ovve) {
		OvveItem item = (OvveItem) ovve.getItem();
		ItemStack top = new ItemStack(ModContent.top(item.chapter));
		SpotPlacements patches = ovve.get(ModComponents.PATCHES);
		if (patches != null) top.set(ModComponents.PATCHES, patches);
		Placement preview = ovve.get(ModComponents.PREVIEW);
		if (preview != null) top.set(ModComponents.PREVIEW, preview);
		if (Boolean.TRUE.equals(ovve.get(ModComponents.ON_STAND))) top.set(ModComponents.ON_STAND, true);   // its patches are display entities too
		return top;
	}

	/**
	 * What a vanilla client is told about a garment half: our equipment asset, no right-click
	 * swap (that click is the bundle's), the half's instant patches as the dye colour and, on the
	 * top, one more as the armour trim (both hidden from the tooltip), and no way to dye it at a
	 * cauldron or crafting table.
	 */
	static void dress(
			ItemStack client, Equippable base, ItemStack garment, Chapter chapter, Piece piece, boolean nercabbad,
			PacketContext context, HolderLookup.Provider lookup
	) {
		if (base == null) throw new IllegalStateException("garment lost its equippable component");
		GameProfile profile = context == null ? null : context.get(PacketContext.GAME_PROFILE);
		Looks.Look look = Looks.look(garment, piece, profile == null ? null : profile.id());
		client.set(DataComponents.EQUIPPABLE, Equippable.builder(base.slot())
				.setEquipSound(base.equipSound())
				.setAsset(Looks.asset(chapter, piece, nercabbad, look.combo().key()))
				.setDamageOnHurt(base.damageOnHurt())
				.setSwappable(false)
				.setDispensable(false)
				.build());
		TooltipDisplay display = client.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		if (look.dye() != 0) {
			client.set(DataComponents.DYED_COLOR, new DyedItemColor(look.dye()));
			display = display.withHidden(DataComponents.DYED_COLOR, true);
		} else {
			client.remove(DataComponents.DYED_COLOR);
		}
		if (look.trim() != null) {
			var pattern = lookup.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(ResourceKey.create(Registries.TRIM_PATTERN, Trims.pattern(look.trim())));
			var material = lookup.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(ResourceKey.create(Registries.TRIM_MATERIAL, Trims.material()));
			client.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
			display = display.withHidden(DataComponents.TRIM, true);
		} else {
			client.remove(DataComponents.TRIM);
		}
		client.set(DataComponents.TOOLTIP_DISPLAY, display);
	}
}
