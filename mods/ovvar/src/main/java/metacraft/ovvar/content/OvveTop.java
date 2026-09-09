package metacraft.ovvar.content;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import metacraft.ovvar.pack.Trims;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.Map;

/**
 * Keeps the chest slot in step with the ovve in the legs slot: a companion top while the ovve's
 * top is up and the slot is free, nothing of ours otherwise. Runs from the ovve's inventory tick
 * plus the edges the tick can't see: the stack on the cursor, a drop, and death.
 */
public final class OvveTop {
    private OvveTop() {}

    public static void init() {
        // A player who swaps a chestplate into the slot is holding the top on the cursor, where nothing ticks.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.containerMenu.getCarried().getItem() instanceof OvveTopItem) {
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                    player.containerMenu.broadcastChanges();
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

    /** Called every tick for an ovve worn in the legs slot. */
    static void sync(LivingEntity wearer, ItemStack ovve) {
        ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
        if (!OvveItem.topUp(ovve)) {
            if (chest.getItem() instanceof OvveTopItem) wearer.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            return;
        }
        ItemStack want = topFor(ovve);
        if (chest.isEmpty()) {
            wearer.setItemSlot(EquipmentSlot.CHEST, want);
        } else if (chest.getItem() instanceof OvveTopItem) {
            if (!ItemStack.matches(chest, want)) wearer.setItemSlot(EquipmentSlot.CHEST, want);
        }
        // Anything else worn there is real armour over the ovve: the top stays up underneath, hidden the
        // way a chestplate hides it (the armour model covers the same body and arms), and comes back
        // when the armour comes off.
    }

    private static ItemStack topFor(ItemStack ovve) {
        OvveItem item = (OvveItem) ovve.getItem();
        ItemStack top = new ItemStack(ModContent.top(item.chapter));
        List<String> patches = ovve.get(ModComponents.PATCHES);
        if (patches != null) top.set(ModComponents.PATCHES, patches);
        String preview = ovve.get(ModComponents.PREVIEW);
        if (preview != null) top.set(ModComponents.PREVIEW, preview);
        return top;
    }

    /**
     * What a vanilla client is told about a garment half: our equipment asset, no right-click
     * swap (that click is the bundle's), the half's instant patches as the dye colour and its first
     * patch as the armour trim (both hidden from the tooltip), and no way to dye it at a cauldron
     * or crafting table.
     */
    static void dress(ItemStack client, Equippable base, ItemStack garment, Chapter chapter, Piece piece, boolean nercabbad,
                      PacketContext context, HolderLookup.Provider lookup) {
        if (base == null) throw new IllegalStateException("garment lost its equippable component");
        GameProfile profile = context == null ? null : context.get(PacketContext.GAME_PROFILE);
        Looks.Look look = Looks.look(garment, piece, profile == null ? null : profile.id());
        client.set(DataComponents.EQUIPPABLE, Equippable.builder(base.slot())
                .setEquipSound(base.equipSound())
                .setAsset(Looks.asset(chapter, piece, nercabbad, look.combo()))
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
