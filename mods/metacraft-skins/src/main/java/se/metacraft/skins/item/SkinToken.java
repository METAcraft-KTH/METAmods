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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The Skin Token, {@code metacraft-skins:skin_token}: a real server item, so recipes, villager
 * trades, workstations and the recipe book (which all test the server's item) never take it for
 * the paper clients are shown. It stores only its skin's id, in a hidden component; its name and
 * lore are written for each viewer every time it's sent ({@link Skins#tokenView}), so a token
 * always describes the skin as it is now. Every token wears the one shared token model. Tokens come
 * from {@code /itemskins give}, from Extract ({@link Skins#extract}), which turns an owned skin back into one, and
 * from Skin Boxes (the Roll menu).
 */
public final class SkinToken extends Item implements PolymerItem {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "skin_token");
	/** The shared token model in the skins pack. A path no skin id can take: ids never hold a '/'. */
	public static final Identifier MODEL = Identifier.fromNamespaceAndPath(SkinFolder.NAMESPACE, "token/skin_token");

	/** Which skin a token is for. */
	public static final DataComponentType<String> SKIN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "token"), DataComponentType.<String>builder().persistent(Codec.STRING).build());

	public static final SkinToken ITEM = Registry.register(BuiltInRegistries.ITEM, ID,
			new SkinToken(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID)).stacksTo(64)));

	private SkinToken(Item.Properties properties) {
		super(properties);
	}

	/** Registers the item and its component, at mod initialisation (the component hidden from clients). */
	public static void init() {
		PolymerComponent.registerDataComponent(SKIN);
	}

	/** {@code count} tokens for the skin. */
	public static ItemStack of(String skinId, int count) {
		ItemStack stack = new ItemStack(ITEM, count);
		stack.set(SKIN, skinId);
		return stack;
	}

	/** The skin a stack is a token for; empty for anything but a token (or a token with no skin id). */
	public static Optional<String> skinOf(ItemStack stack) {
		return stack.is(ITEM) ? Optional.ofNullable(stack.get(SKIN)) : Optional.empty();
	}

	/**
	 * Sends the player their inventory (and any open menu) again, after their ownership changed:
	 * a token's text is written only as it's sent, so the copies they hold would say the old thing.
	 */
	public static void resend(ServerPlayer player) {
		player.inventoryMenu.sendAllDataToRemote();
		if (player.containerMenu != player.inventoryMenu) player.containerMenu.sendAllDataToRemote();
	}

	@Override
	public Item getPolymerItem(ItemStack stack, @Nullable PacketContext context) {
		return Items.PAPER;
	}

	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, @Nullable PacketContext context, HolderLookup.Provider lookup) {
		return MODEL;
	}

	/** Polymer's client copy (which keeps the real stack inside it for the way back), then the viewer's text. */
	@Override
	public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag tooltipType, @Nullable PacketContext context, HolderLookup.Provider lookup) {
		ItemStack client = PolymerItem.super.getPolymerItemStack(stack, tooltipType, context, lookup);
		Skins skins = MetacraftSkins.skins();
		var profile = context == null ? null : context.get(PacketContext.GAME_PROFILE);
		UUID viewer = profile == null ? null : profile.id();
		return skins == null ? client : skins.tokenView(stack, client, viewer);
	}

	/** What the server itself calls it (logs, death messages); players read {@link Skins#tokenView}. */
	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Skin Token");
	}

	/** A right-click with it in the main hand, on air, opens the token's claim menu ({@link RightClick}). */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return RightClick.open(player, hand);
	}

	/** A right-click with it in the main hand on a block that does nothing with it opens the token's claim menu. */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		return RightClick.open(context.getPlayer(), context.getHand());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return false;
	}
}
