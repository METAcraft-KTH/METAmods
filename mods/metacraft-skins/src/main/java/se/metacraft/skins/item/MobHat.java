package se.metacraft.skins.item;

import com.mojang.serialization.Codec;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;
import se.metacraft.skins.Kind;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.MobHats;
import se.metacraft.skins.SkinFolder;
import se.metacraft.skins.SkinSummary;
import se.metacraft.skins.Skins;

import java.util.Optional;

/**
 * The Mob Hat, {@code metacraft-skins:mob_hat}: what a mob wears when it spawns with a hat
 * ({@link MobHats}). It stores only the skin's id, in a hidden component, and is never a skin anyone
 * owns: no armour, no Perks, never dropped (the mob's drop chance for it is 0). Clients are sent the
 * skin's Look, worn on the head; while the skin isn't a loaded hat, a plain leather helmet. Should
 * one ever reach a player's inventory, it's gone on the next tick.
 */
public final class MobHat extends Item implements PolymerItem {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "mob_hat");

	/** Which skin's Look the hat is. */
	public static final DataComponentType<String> SKIN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(MetacraftSkins.MOD_ID, "mob_hat"), DataComponentType.<String>builder().persistent(Codec.STRING).build());

	public static final MobHat ITEM = Registry.register(BuiltInRegistries.ITEM, ID,
			new MobHat(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID)).stacksTo(1)));

	private MobHat(Item.Properties properties) {
		super(properties);
	}

	/** Registers the item and its component, at mod initialisation (the component hidden from clients). */
	public static void init() {
		PolymerComponent.registerDataComponent(SKIN);
	}

	/** A hat of the skin with this id. */
	public static ItemStack of(String skinId) {
		ItemStack stack = new ItemStack(ITEM);
		stack.set(SKIN, skinId);
		return stack;
	}

	/** The skin a stack is a Mob Hat of; empty for anything else. */
	public static Optional<String> skinOf(ItemStack stack) {
		return stack.is(ITEM) ? Optional.ofNullable(stack.get(SKIN)) : Optional.empty();
	}

	/** The hat's skin, if it's a loaded hat now: only then is its Look in the pack. */
	private static Optional<SkinSummary> look(ItemStack stack) {
		Skins skins = MetacraftSkins.skins();
		if (skins == null) return Optional.empty();
		return skinOf(stack).flatMap(skins::skin).filter(skin -> !skin.dormant() && skin.kind().filter(Kind::isHat).isPresent());
	}

	@Override
	public Item getPolymerItem(ItemStack stack, @Nullable PacketContext context) {
		return Items.LEATHER_HELMET;
	}

	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, @Nullable PacketContext context, HolderLookup.Provider lookup) {
		return look(stack).map(skin -> Identifier.fromNamespaceAndPath(SkinFolder.NAMESPACE, skin.id())).orElse(Identifier.withDefaultNamespace("leather_helmet"));
	}

	/**
	 * Polymer's client copy; with a Look, worn on the head with no equipment asset, so the client draws
	 * the Look there rather than leather armour (as a Skinned Item's hat is, {@link Skins#clientView}).
	 */
	@Override
	public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag tooltipType, @Nullable PacketContext context, HolderLookup.Provider lookup) {
		ItemStack client = PolymerItem.super.getPolymerItemStack(stack, tooltipType, context, lookup);
		look(stack).ifPresent(skin -> {
			client.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build());
			client.set(DataComponents.ITEM_NAME, skin.styledName());
		});
		return client;
	}

	/** What the server itself calls it (logs). */
	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Mob Hat");
	}

	/** Never a player's: a hat that reaches an inventory is gone. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (owner instanceof Player) stack.setCount(0);
	}
}
