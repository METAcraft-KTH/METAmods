package metacraft.ovvar.content;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.pack.EquipmentJson;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The boots render pass as a second dye channel for the legs. The client draws the feet slot
 * with the whole leg boxes (vanilla boot textures are just transparent above the ankle), with
 * its own equipment asset and its own dye colour — 24 more bits — so an ovve wearer's feet slot
 * is made to carry our preview layer:
 * <ul>
 *   <li>over vanilla boots of a known material, the boots themselves: marked with the wearer
 *       ({@link ModComponents#WRAPPED}), and shown to clients with our layer added to theirs,
 *       trim and glint kept — nothing else changes about them;</li>
 *   <li>while the slot is empty, virtual cuffs ({@link OvveFeetItem}) that exist only in the
 *       equipment packets other players get — never in an inventory, so the wearer's own
 *       inventory stays empty there and boots go on as usual. The wearer's own client renders
 *       their body from that inventory, so it does not get the channel: they see up to three
 *       fewer of their own newest leg patches until the pack catches up.</li>
 * </ul>
 * Leather boots use the dye colour for their own colour and other mods' boots have layers we
 * don't know, so over those the channel is off and the legs fall back to three instant patches.
 * The ovve is marked with {@link ModComponents#FEET_CHANNEL} so {@link Looks#look} knows the room.
 */
public final class OvveFeet {
    private OvveFeet() {}

    /** Vanilla equipment assets whose boots we wrap: their humanoid layer is one texture of the same name. */
    public static final List<String> MATERIALS = List.of("chainmail", "copper", "diamond", "gold", "iron", "netherite");
    /** The virtual cuffs' "material": our layer alone. */
    public static final String NONE = "none";
    private static final Set<String> MATERIAL_SET = Set.copyOf(MATERIALS);

    /** What each viewer was last sent for a wearer's empty feet slot (the dye value), so it is re-sent only on change. */
    private static final Map<UUID, Map<UUID, Integer>> SENT = new HashMap<>();
    /** Wearers whose virtual cuffs were synced this tick — the rest of SENT is stale and dropped. */
    private static final Set<UUID> ALIVE = new HashSet<>();

    public static void init() {
        PolymerItemUtils.ITEM_MODIFICATION_EVENT.register(OvveFeet::wrap);
        ServerTickEvents.END_SERVER_TICK.register(OvveFeet::tick);
        // A viewer who starts tracking a wearer gets the cuffs on the next sync (after the pairing packets).
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) -> {
            Map<UUID, Integer> viewers = SENT.get(entity.getUUID());
            if (viewers != null) viewers.remove(player.getUUID());
        });
        // The moment an ovve or boots go on or off, before the equipment packet leaves.
        ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, next) -> {
            if (slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) return;
            ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS);
            if (legs.getItem() instanceof OvveItem) {
                OvveTop.sync(entity, legs);
                sync(entity, legs);
            } else if (slot == EquipmentSlot.LEGS && previous.getItem() instanceof OvveItem) {
                clear(entity);
            }
        });
    }

    /** The material of boots we can wrap ({@link #NONE} for the virtual cuffs), or null. */
    public static String material(ItemStack feet) {
        if (feet.getItem() instanceof OvveFeetItem) return NONE;
        Equippable equippable = feet.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.FEET || equippable.assetId().isEmpty()) return null;
        var asset = equippable.assetId().get().identifier();
        return asset.getNamespace().equals("minecraft") && MATERIAL_SET.contains(asset.getPath()) ? asset.getPath() : null;
    }

    /** Called every tick for an ovve worn in the legs slot: keep the feet slot carrying the channel, and the ovve told. */
    static void sync(LivingEntity wearer, ItemStack ovve) {
        ItemStack feet = wearer.getItemBySlot(EquipmentSlot.FEET);
        boolean channel;
        if (feet.isEmpty()) {
            channel = true;
            sendCuffs(wearer, ovve);
        } else if (material(feet) != null) {
            if (!wearer.getUUID().equals(feet.get(ModComponents.WRAPPED))) feet.set(ModComponents.WRAPPED, wearer.getUUID());
            channel = true;
        } else {
            if (feet.has(ModComponents.WRAPPED)) feet.remove(ModComponents.WRAPPED);
            channel = false;
        }
        if (Looks.feetChannel(ovve) != channel) ovve.set(ModComponents.FEET_CHANNEL, channel);
    }

    /** The virtual cuffs to every viewer whose last-sent look differs (a new viewer, new patches, a newer pack). */
    private static void sendCuffs(LivingEntity wearer, ItemStack ovve) {
        ALIVE.add(wearer.getUUID());
        Map<UUID, Integer> viewers = SENT.computeIfAbsent(wearer.getUUID(), id -> new HashMap<>());
        ItemStack cuffs = null;
        for (ServerPlayer viewer : PlayerLookup.tracking(wearer)) {
            if (viewer == wearer) continue;
            int dye = Looks.look(ovve, Piece.BOTTOM, viewer.getUUID()).feetDye();
            Integer last = viewers.get(viewer.getUUID());
            if (last != null && last == dye) continue;
            if (cuffs == null) cuffs = cuffsFor(ovve);
            viewer.connection.send(new ClientboundSetEquipmentPacket(wearer.getId(), List.of(Pair.of(EquipmentSlot.FEET, cuffs))));
            viewers.put(viewer.getUUID(), dye);
        }
    }

    /** The ovve came off an empty-footed wearer: viewers get the empty slot back. */
    private static void clear(LivingEntity wearer) {
        Map<UUID, Integer> viewers = SENT.remove(wearer.getUUID());
        if (viewers == null || !wearer.getItemBySlot(EquipmentSlot.FEET).isEmpty()) return;
        for (ServerPlayer viewer : PlayerLookup.tracking(wearer)) {
            if (viewers.containsKey(viewer.getUUID())) {
                viewer.connection.send(new ClientboundSetEquipmentPacket(wearer.getId(), List.of(Pair.of(EquipmentSlot.FEET, ItemStack.EMPTY))));
            }
        }
    }

    /** Boots left wrapped after the ovve came off are unwrapped; wearers that stopped syncing (gone, died) are forgotten. */
    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
            if (feet.has(ModComponents.WRAPPED) && !(player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof OvveItem)) {
                feet.remove(ModComponents.WRAPPED);
            }
        }
        SENT.keySet().retainAll(ALIVE);
        ALIVE.clear();
    }

    private static ItemStack cuffsFor(ItemStack ovve) {
        OvveItem item = (OvveItem) ovve.getItem();
        ItemStack cuffs = new ItemStack(ModContent.feet(item.chapter));
        List<String> patches = ovve.get(ModComponents.PATCHES);
        if (patches != null) cuffs.set(ModComponents.PATCHES, patches);
        String preview = ovve.get(ModComponents.PREVIEW);
        if (preview != null) cuffs.set(ModComponents.PREVIEW, preview);
        cuffs.set(ModComponents.FEET_CHANNEL, true);
        if (Boolean.TRUE.equals(ovve.get(ModComponents.ON_STAND))) cuffs.set(ModComponents.ON_STAND, true);
        return cuffs;
    }

    /** Vanilla boots marked as wrapped, on their way to a client: our layer and dye colour added, theirs kept. */
    private static ItemStack wrap(ItemStack original, ItemStack client, PacketContext context) {
        UUID wearerId = original.get(ModComponents.WRAPPED);
        if (wearerId == null) return client;
        MinecraftServer server = context == null ? null : context.get(PacketContext.SERVER_INSTANCE);
        LivingEntity wearer = findWearer(server, wearerId);
        if (wearer == null) return client;
        ItemStack legs = wearer.getItemBySlot(EquipmentSlot.LEGS);
        if (!(legs.getItem() instanceof OvveItem) || !ItemStack.isSameItemSameComponents(wearer.getItemBySlot(EquipmentSlot.FEET), original)) return client;
        String material = material(original);
        if (material == null) return client;
        dress(client, legs, material, context);
        return client;
    }

    private static LivingEntity findWearer(MinecraftServer server, UUID id) {
        if (server == null) return null;
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    /**
     * What a client is told about the feet slot over an ovve: the boots' own layers (if any) plus
     * our preview layer, and the legs' second three instant patches as the dye colour.
     *
     * @param garment  the ovve (or the virtual cuffs, which carry the ovve's patches)
     * @param material a wrapped material, or null for the virtual cuffs
     */
    static void dress(ItemStack client, ItemStack garment, String material, PacketContext context) {
        GameProfile profile = context == null ? null : context.get(PacketContext.GAME_PROFILE);
        Looks.Look look = Looks.look(garment, Piece.BOTTOM, profile == null ? null : profile.id());
        Equippable base = client.get(DataComponents.EQUIPPABLE);
        if (base == null) throw new IllegalStateException("[" + Ovvar.MOD_ID + "] feet stack lost its equippable component");
        client.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET)
                .setEquipSound(base.equipSound())
                .setAsset(EquipmentJson.feetAsset(material == null ? NONE : material))
                .setDamageOnHurt(base.damageOnHurt())
                .setSwappable(material != null && base.swappable())
                .setDispensable(material != null && base.dispensable())
                .build());
        TooltipDisplay display = client.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (look.feetDye() != 0) {
            client.set(DataComponents.DYED_COLOR, new DyedItemColor(look.feetDye()));
            display = display.withHidden(DataComponents.DYED_COLOR, true);
        } else {
            client.remove(DataComponents.DYED_COLOR);
        }
        client.set(DataComponents.TOOLTIP_DISPLAY, display);
    }
}
