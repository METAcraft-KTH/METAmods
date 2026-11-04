package se.metacraft.skins.item;

import com.mojang.serialization.Codec;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.SkinFolder;
import se.metacraft.skins.Skins;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The Skin Box, {@code metacraft-skins:skin_box}: a real server item, as the Skin Token is, so
 * recipes, villager trades, workstations and dispensers never take it for the vault clients are
 * shown, and a plain item rather than a block item, so the server never places it. It stores only
 * its box's id, in a hidden component; its name, look and lore are written every time it's sent
 * ({@link Skins#boxView}), so a box always describes its file as it is now.
 */
public final class SkinBox extends Item implements PolymerItem {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "skin_box");
	/** The plain vault, as clients draw a vault item. */
	public static final Identifier PLAIN_MODEL = Identifier.withDefaultNamespace("vault");
	/** The ominous vault: vanilla's block model, by an item definition the skins pack always carries. A path no skin id can take. */
	public static final Identifier OMINOUS_MODEL = Identifier.fromNamespaceAndPath(SkinFolder.NAMESPACE, "box/ominous_vault");

	/** Which box a box item is. */
	public static final DataComponentType<String> BOX = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "box"), DataComponentType.<String>builder().persistent(Codec.STRING).build());

	public static final SkinBox ITEM = Registry.register(BuiltInRegistries.ITEM, ID,
			new SkinBox(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID)).stacksTo(64)));

	private SkinBox(Item.Properties properties) {
		super(properties);
	}

	/** Registers the item and its component, at mod initialisation (the component hidden from clients). */
	public static void init() {
		PolymerComponent.registerDataComponent(BOX);
	}

	/** {@code count} boxes of the box with this id. */
	public static ItemStack of(String boxId, int count) {
		ItemStack stack = new ItemStack(ITEM, count);
		stack.set(BOX, boxId);
		return stack;
	}

	/** The box a stack is; empty for anything but a Skin Box (or one with no box id). */
	public static Optional<String> boxOf(ItemStack stack) {
		return stack.is(ITEM) ? Optional.ofNullable(stack.get(BOX)) : Optional.empty();
	}

	@Override
	public Item getPolymerItem(ItemStack stack, @Nullable PacketContext context) {
		return Items.VAULT;
	}

	/** The plain vault; an ominous box's model is set by {@link Skins#boxView}, which knows the box. */
	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, @Nullable PacketContext context, HolderLookup.Provider lookup) {
		return PLAIN_MODEL;
	}

	/** Polymer's client copy (which keeps the real stack inside it for the way back), then the box's name, look and lore. */
	@Override
	public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag tooltipType, @Nullable PacketContext context, HolderLookup.Provider lookup) {
		ItemStack client = PolymerItem.super.getPolymerItemStack(stack, tooltipType, context, lookup);
		Skins skins = MetacraftSkins.skins();
		return skins == null ? client : skins.boxView(stack, client);
	}

	/** What the server itself calls it (logs, death messages); players read {@link Skins#boxView}. */
	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Skin Box");
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return false;
	}
}
