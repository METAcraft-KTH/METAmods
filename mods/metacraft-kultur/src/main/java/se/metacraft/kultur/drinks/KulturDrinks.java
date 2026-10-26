package se.metacraft.kultur.drinks;

import se.metacraft.kultur.Kultur;
import se.metacraft.kultur.content.ModContent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;

/**
 * The chapter drinks when Brewery is not on the server: the drinks as items, with no way to make
 * them (Season 6 brings them with Brewery, at an event), and Brewery's drunkenness (the meter, its steps, the stagger, the blackout) done here instead. With
 * Brewery on the server none of this registers and Brewery brews the drinks from
 * {@code data/kultur/brewery_drinks}, as before.
 */
public final class KulturDrinks {
	/** {@code kultur:inebriated}; null with Brewery on the server. */
	public static Holder<MobEffect> INEBRIATED;
	/** {@code kultur:nyckeln}; null with Brewery on the server. */
	public static Item NYCKELN;
	private static boolean registered;

	private KulturDrinks() {}

	/** True when this mode is on: Brewery is not loaded. */
	public static boolean active() {
		return !FabricLoader.getInstance().isModLoaded("brewery");
	}

	/** True once {@link #register} has run in this mode. */
	public static boolean registered() {
		return registered;
	}

	/** Registers everything; does nothing when Brewery is loaded. */
	public static void register() {
		if (!active() || registered) return;
		INEBRIATED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id("inebriated"), (MobEffect) new InebriatedEffect());
		Drunkenness.register();
		DrunkTicker.register();
		DrinkPotions.register();
		DrinksPack.register();
		NYCKELN = Registry.register(BuiltInRegistries.ITEM, id("nyckeln"), new NyckelnItem(new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, id("nyckeln")))
				.stacksTo(1)
				.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
				.usingConvertsTo(Items.GLASS_BOTTLE)));
		ModContent.alsoInCreativeTab(output -> {
			for (DrinkKind kind : DrinkKind.values()) {
				output.accept(kind == DrinkKind.NYCKELN ? new ItemStack(NYCKELN) : DrinkPotions.bottle(kind));
			}
		});
		registered = true;
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, path);
	}
}
