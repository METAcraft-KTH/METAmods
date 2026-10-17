package metacraft.kultur.drinks;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.api.other.SimplePolymerPotion;
import metacraft.kultur.Kultur;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The three brewed drinks as potions, as PolymITer had them: registered {@code Potion}s a brewing
 * stand and a vanilla client both handle (Polymer hides them from the client's registry). They carry
 * no effects of their own; drinking one is what does it ({@link DrinkEffects}), so a splash or
 * lingering bottle of one does nothing to anybody. The recipes are data, 26.3's brewing recipes:
 * {@code data/kultur/recipe/alcohol.json} and its siblings, loaded only without Brewery.
 *
 * <p>On a client each bottle is PolymITer's model, {@link DrinkKind#model()}, with the drink's name.
 */
public final class DrinkPotions {
	private static final Map<DrinkKind, Holder<Potion>> POTIONS = new EnumMap<>(DrinkKind.class);

	private DrinkPotions() {}

	static void register() {
		for (DrinkKind kind : DrinkKind.values()) {
			if (kind == DrinkKind.NYCKELN) continue;
			Identifier id = Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, kind.id());
			// The name is the vanilla lang key's suffix: item.minecraft.potion.effect.kultur_spiken and the
			// splash, lingering and tipped-arrow keys like it are in Kultur's lang, so every name a client
			// builds from the potion (Polymer's own custom name for the bottle included) reads right.
			POTIONS.put(kind, Registry.registerForHolder(BuiltInRegistries.POTION, id, (Potion) new SimplePolymerPotion(kind.potionName())));
		}
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((original, client, context) -> {
			DrinkKind kind = kindOf(original);
			if (kind == null || kind == DrinkKind.NYCKELN) return client;
			if (!original.is(Items.POTION) && !original.is(Items.SPLASH_POTION) && !original.is(Items.LINGERING_POTION)) return client;   // a tipped arrow
			client.set(DataComponents.ITEM_MODEL, kind.model());
			// The tooltip would say "No Effects", which is no description of a drink.
			client.set(DataComponents.LORE, original.get(DataComponents.LORE));
			return client;
		});
	}

	/** The potion of one of the three brewed drinks; null for Nyckeln, which is an item. */
	public static @Nullable Holder<Potion> potion(DrinkKind kind) {
		return POTIONS.get(kind);
	}

	/** A bottle of a brewed drink. */
	public static ItemStack bottle(DrinkKind kind) {
		return PotionContents.createItemStack(Items.POTION, POTIONS.get(kind));
	}

	/**
	 * Which drink a stack is: a potion, splash or lingering bottle of one of ours, or Nyckeln; null for
	 * anything else.
	 */
	public static @Nullable DrinkKind kindOf(ItemStack stack) {
		if (KulturDrinks.NYCKELN != null && stack.is(KulturDrinks.NYCKELN)) return DrinkKind.NYCKELN;
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null || contents.potion().isEmpty()) return null;
		for (var entry : POTIONS.entrySet()) {
			if (contents.is(entry.getValue())) return entry.getKey();
		}
		return null;
	}
}
