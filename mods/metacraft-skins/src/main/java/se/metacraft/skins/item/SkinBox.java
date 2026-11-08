package se.metacraft.skins.item;

import com.mojang.serialization.Codec;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.SkinFolder;
import se.metacraft.skins.Skins;
import se.metacraft.skins.ui.RightClick;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The Skin Box, {@code metacraft-skins:skin_box}: a real server item, as the Skin Token is, so
 * recipes, villager trades, workstations and dispensers never take it for the vault clients are
 * sent, and a plain item rather than a block item, so the server never places it. That vault is
 * marked as a box ({@link #LOOK}), which the skins pack draws with the box's own model; a client
 * without the pack draws a vault. Boxes don't
 * stack: each is an item of its own. It stores only
 * its box's id, in a hidden component; its name, look and lore are written every time it's sent
 * ({@link Skins#boxView}), so a box always describes its file as it is now.
 */
public final class SkinBox extends Item implements PolymerItem {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "skin_box");
	/** Vanilla's vault item definition. */
	public static final Identifier MODEL = Identifier.withDefaultNamespace("vault");
	/** The custom model data string that marks a vault a client is sent as a Skin Box. Vanilla draws it as any vault. */
	public static final String LOOK = "metacraft-skins:skin_box";
	/** The box's model in the skins pack, which draws a {@link #LOOK} vault. A path no skin's model can take. */
	public static final Identifier BOX_MODEL = Identifier.fromNamespaceAndPath(SkinFolder.NAMESPACE, "box/skin_box");

	/** Which box a box item is. */
	public static final DataComponentType<String> BOX = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "box"), DataComponentType.<String>builder().persistent(Codec.STRING).build());

	public static final SkinBox ITEM = Registry.register(BuiltInRegistries.ITEM, ID,
			new SkinBox(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID)).stacksTo(1)));

	private SkinBox(Item.Properties properties) {
		super(properties);
	}

	/** Registers the item and its component, at mod initialisation (the component hidden from clients). */
	public static void init() {
		PolymerComponent.registerDataComponent(BOX);
	}

	/** One box of the box with this id. */
	public static ItemStack of(String boxId) {
		ItemStack stack = new ItemStack(ITEM);
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

	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, @Nullable PacketContext context, HolderLookup.Provider lookup) {
		return MODEL;
	}

	/** Polymer's client copy (which keeps the real stack inside it for the way back), drawn as a box, then the box's name and lore. */
	@Override
	public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag tooltipType, @Nullable PacketContext context, HolderLookup.Provider lookup) {
		ItemStack client = drawnAsBox(PolymerItem.super.getPolymerItemStack(stack, tooltipType, context, lookup));
		Skins skins = MetacraftSkins.skins();
		return skins == null ? client : skins.boxView(stack, client);
	}

	/** {@code client}, a vault a client is sent, marked as a Skin Box ({@link #LOOK}); the Roll button's too. */
	public static ItemStack drawnAsBox(ItemStack client) {
		client.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(LOOK), List.of()));
		return client;
	}

	/** What the server itself calls it (logs, death messages); players read {@link Skins#boxView}. */
	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Skin Box");
	}

	/** A right-click with it in the main hand, on air, opens the box's Roll menu ({@link RightClick}). */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return RightClick.open(player, hand);
	}

	/** A right-click with it in the main hand on a block that does nothing with it opens the box's Roll menu. */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		return RightClick.open(context.getPlayer(), context.getHand());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return false;
	}
}
