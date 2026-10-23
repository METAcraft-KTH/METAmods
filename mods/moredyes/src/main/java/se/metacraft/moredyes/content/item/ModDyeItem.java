package se.metacraft.moredyes.content.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import se.metacraft.moredyes.color.ModColor;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.item.SignApplicator;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import se.metacraft.moredyes.pet.Collars;
import se.metacraft.moredyes.sign.SignColors;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import se.metacraft.moredyes.mixin.LoomMenuMixin;

/**
 * A More Dyes dye. Deliberately NOT a vanilla {@code DyeItem}: that would drag it into every
 * {@code DyeColor}-keyed vanilla path (tag recipes, collars) that cannot show our colour.
 * The interactions we want (sheep, armour, blocks, sign text, collars, the loom) are implemented
 * against {@link ModColor} instead.
 *
 * <p>The client is told it holds white dye with our item model, so it behaves like a dye in hand, and
 * with the nearest vanilla colour's {@code dye} component, so the vanilla loom screen takes it (see
 * {@link LoomMenuMixin}). That component exists only on the client's copy.
 */
public final class ModDyeItem extends Item implements PolymerItem, SignApplicator {
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

	/** Sign text in our colour: see {@link SignColors}. */
	@Override
	public boolean canApplyToSign(SignText text, ItemStack stack, Player player) {
		return text.hasMessage(player.isTextFilteringEnabled()) && SignColors.colourOf(text) != color;
	}

	@Override
	public boolean tryApplyToSign(Level level, SignBlockEntity sign, SignTextSlot slot, ItemStack stack, Player player) {
		if (!sign.updateText(text -> SignColors.paint(text, color), slot)) return false;
		level.playSound(null, sign.getBlockPos(), SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		return true;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.DYE.white();
	}

	/**
	 * The client's copy carries the nearest vanilla colour as its {@code dye} component, so the vanilla
	 * loom lets it into the dye slot and lists the patterns. What else a vanilla client does with that
	 * component is only a prediction the server corrects: a wolf's collar (the server sets the same
	 * nearest colour, {@link Collars}) and the dye sound on a sheep.
	 */
	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		out.set(DataComponents.DYE, SignColors.nearestDye(color));
	}

	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return model;
	}
}
