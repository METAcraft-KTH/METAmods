package metacraft.ovvar.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A chapter's ovve: one item, worn in the legs slot, with pockets. It is a plain vanilla bundle
 * (same capacity, so what the client predicts is what happens) that can also be filled while
 * worn, by clicking items onto the legs slot.
 *
 * Right click behaves as a bundle (hold to empty it); sneak + right click rolls the top up or
 * down. Neither equips it — drag it into the slot or shift-click. While the top is up the mod
 * keeps a companion {@link OvveTopItem} in the chest slot so the sleeves render. Leather-grade
 * defence, no durability (it breaking would spill someone's pockets). The client is handed a
 * bundle with our equipment asset, chosen per stack.
 */
public final class OvveItem extends BundleItem implements PolymerItem {
    public final Chapter chapter;
    private final Identifier id;

    public OvveItem(Properties properties, Chapter chapter, Identifier id) {
        super(properties);
        this.chapter = chapter;
        this.id = id;
    }

    public static boolean topUp(ItemStack ovve) {
        return Boolean.TRUE.equals(ovve.get(ModComponents.TOP_UP));
    }

    public static void setTopUp(ItemStack ovve, boolean up) {
        if (up) ovve.set(ModComponents.TOP_UP, true);
        else ovve.remove(ModComponents.TOP_UP);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (!chapter.rollable) {
            serverPlayer.sendOverlayMessage(Component.literal("A " + chapter.garmentWord() + " has nothing to roll down"));
            return InteractionResult.FAIL;
        }
        boolean up = !topUp(stack);
        setTopUp(stack, up);
        serverPlayer.sendOverlayMessage(Component.literal(up ? "Top rolled up" : "Top rolled down"));
        return InteractionResult.SUCCESS;
    }

    // ---- wearing

    /** Worn in the legs slot (player or armour stand): keep the companion top in step every tick. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.LEGS && entity instanceof LivingEntity wearer) OvveTop.sync(wearer, stack);
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return Items.BUNDLE;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
        return id;
    }

    @Override
    public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext context, HolderLookup.Provider lookup) {
        ItemStack out = PolymerItem.super.getPolymerItemStack(stack, flag, context, lookup);
        out.set(DataComponents.EQUIPPABLE, OvveTop.equippable(stack.get(DataComponents.EQUIPPABLE), Looks.bottom(stack)));
        return out;
    }
}
