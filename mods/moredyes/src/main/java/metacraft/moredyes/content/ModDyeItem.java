package metacraft.moredyes.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import metacraft.moredyes.banner.DyeLoomGui;
import metacraft.moredyes.color.ModColor;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A More Dyes dye. Deliberately NOT a vanilla {@code DyeItem}: that would drag it into every
 * {@code DyeColor}-keyed vanilla path (tag recipes, loom, collars) that cannot show our colour.
 * The interactions we want (sheep, armour, blocks) are implemented against {@link ModColor} instead.
 *
 * The client is told it holds white dye with our item model, so it behaves like a dye in hand.
 */
public final class ModDyeItem extends Item implements PolymerItem {
    private final ModColor color;
    private final Identifier model;

    public ModDyeItem(Properties properties, ModColor color, Identifier id) {
        super(properties);
        this.color = color;
        this.model = id;
    }

    public ModColor color() {
        return color;
    }

    /** Right-click in the air with a banner/shield in the other hand: open the dye loom. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!DyeLoomGui.canOpen(player.getItemInHand(other))) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            new DyeLoomGui(serverPlayer, color, hand).open();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return Items.DYE.white();
    }

    @Override
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
        return model;
    }
}
