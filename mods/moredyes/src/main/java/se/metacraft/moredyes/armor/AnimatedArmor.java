package se.metacraft.moredyes.armor;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import se.metacraft.moredyes.MoreDyes;
import se.metacraft.moredyes.color.ModColor;
import se.metacraft.moredyes.color.ModColors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Dyed armour of an animated colour. The stack carries the colour's id in {@link #ANIMATED_DYE}
 * next to a plain {@code dyed_color} of the colour's static {@code rgb}, so with the mod gone it is
 * ordinary dyed armour.
 */
public final class AnimatedArmor {
	/** The animated colour an armour piece was dyed with: the colour id. Server only. */
	public static final DataComponentType<String> ANIMATED_DYE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "animated_dye"),
			DataComponentType.<String>builder().persistent(Codec.STRING).build());

	private AnimatedArmor() {}

	/** Everything wearing RGB armour, so the refresh does not have to scan every entity. */
	private static final Set<LivingEntity> WEARERS = Collections.newSetFromMap(new WeakHashMap<>());

	/** Ticks between re-sending worn armour: at a three-minute loop the colour moves 2-3 RGB units in that time. */
	private static final int REFRESH_TICKS = 20;

	public static void init() {
		PolymerComponent.registerDataComponent(ANIMATED_DYE);
		// vanilla dyed leather is not a Polymer item, so ask for it to be routed through Polymer
		PolymerItemUtils.CONTEXT_ITEM_CHECK.register((item, context) -> item instanceof ItemStack stack && animatedColour(stack).isPresent());
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((original, client, context) -> {
			Optional<ModColor> colour = animatedColour(original);
			if (colour.isPresent()) {
				UUID viewer = viewerOf(context);
				client.set(DataComponents.DYED_COLOR, new DyedItemColor(viewerRgb(colour.get(), viewer, System.currentTimeMillis())));
			}
			return client;
		});
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, current) -> track(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living) WEARERS.remove(living);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_TICKS == 0) refresh();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PackClock.forget(handler.getOwner().id()));
	}

	/** Who is being sent the item: the connection's profile, or failing that its player (a connection made without a login has no profile). */
	private static @Nullable UUID viewerOf(@Nullable PacketContext context) {
		if (context == null) return null;
		GameProfile profile = context.get(PacketContext.GAME_PROFILE);
		if (profile != null) return profile.id();
		ServerPlayer player = PolymerCommonUtils.getPlayer(context);
		return player == null ? null : player.getUUID();
	}

	/** The colour a viewer is sent for {@code colour}: where the loop is on their pack clock. */
	public static int viewerRgb(ModColor colour, @Nullable UUID viewer, long nowMillis) {
		return colour.animation().map(animation -> animation.rgbAt(PackClock.ticksSince(viewer, nowMillis))).orElse(colour.rgb() & 0xFFFFFF);
	}

	/** Every living entity currently wearing RGB armour (weakly held). */
	public static Set<LivingEntity> wearers() {
		return WEARERS;
	}

	private static void track(LivingEntity entity) {
		if (markedSlots(entity).isEmpty()) {
			WEARERS.remove(entity);
		} else {
			WEARERS.add(entity);
		}
	}

	private static List<EquipmentSlot> markedSlots(LivingEntity entity) {
		List<EquipmentSlot> slots = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (animatedColour(entity.getItemBySlot(slot)).isPresent()) slots.add(slot);
		}
		return slots;
	}

	/**
	 * Sends everyone who sees a wearer its marked armour again; sending is what runs the per-viewer
	 * rewrite above. The wearer sees their own through their inventory, which only re-sends changed
	 * stacks, so those slots are pushed by hand.
	 */
	private static void refresh() {
		for (LivingEntity wearer : new ArrayList<>(WEARERS)) {
			if (wearer.isRemoved() || !(wearer.level() instanceof ServerLevel level)) continue;
			List<EquipmentSlot> slots = markedSlots(wearer);
			if (slots.isEmpty()) {
				WEARERS.remove(wearer);
				continue;
			}
			List<Pair<EquipmentSlot, ItemStack>> worn = new ArrayList<>();
			for (EquipmentSlot slot : slots) worn.add(Pair.of(slot, wearer.getItemBySlot(slot).copy()));
			level.getChunkSource().sendToTrackingPlayers(wearer, new ClientboundSetEquipmentPacket(wearer.getId(), worn));
			if (wearer instanceof ServerPlayer player) {
				var menu = player.inventoryMenu;
				for (int i = InventoryMenu.ARMOR_SLOT_START; i < InventoryMenu.ARMOR_SLOT_END; i++) {
					ItemStack stack = menu.getSlot(i).getItem();
					if (animatedColour(stack).isPresent()) {
						player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), i, stack.copy()));
					}
				}
			}
		}
	}

	/**
	 * The animated colour this stack is dyed with. A marker only counts while it still describes the
	 * armour: it needs a {@code dyed_color} equal to the colour's {@code rgb} and a colour that exists
	 * and animates. So washing in a cauldron (which removes {@code dyed_color}), re-colouring by any
	 * other route, or a colour taken out of colors.json all leave plain armour, not an error.
	 */
	public static Optional<ModColor> animatedColour(ItemStack stack) {
		String id = stack.get(ANIMATED_DYE);
		DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
		if (id == null || dyed == null) return Optional.empty();
		ModColor colour = ModColors.getOrNull(id);
		if (colour == null || colour.animation().isEmpty() || (dyed.rgb() & 0xFFFFFF) != (colour.rgb() & 0xFFFFFF)) return Optional.empty();
		return Optional.of(colour);
	}

	/** {@code dyed_color} for armour dyed with this colour: its static rgb. */
	public static DyedItemColor dyedColor(ModColor colour) {
		return new DyedItemColor(colour.rgb() & 0xFFFFFF);
	}
}
