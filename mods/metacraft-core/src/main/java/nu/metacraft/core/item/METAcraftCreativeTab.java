package nu.metacraft.core.item;

import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.lib.CreativeTabNamespaces;

import java.util.Comparator;
import java.util.List;

/**
 * One creative tab, "METAcraft", with every item our mods add. Polymer shows a server's items in a
 * client's creative menu (when the client has Polymer) only through tabs registered with it, and
 * most of our mods register items without one. Mods with a tab of their own (ovvar, moredyes) keep it.
 *
 * <p>The tab lists the namespaces mods opt in to with {@link CreativeTabNamespaces#add}, which is empty until they
 * do: a mod adds its namespace from its item registration class. It is filled when it is shown, so items and
 * namespaces added after metacraft-core count too.
 */
public final class METAcraftCreativeTab {

	private METAcraftCreativeTab() {}

	public static void init() {
		var tab = PolymerCreativeModeTabUtils.builder()
				.title(Component.literal("METAcraft"))
				.icon(() -> items().stream().findFirst().map(ItemStack::new).orElseGet(() -> new ItemStack(Items.ENDER_EYE)))
				.displayItems((params, output) -> items().forEach(output::accept))
				.build();
		PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(METAcraftCore.getID("items"), tab);
	}

	/** Our items, by id. */
	public static List<Item> items() {
		return BuiltInRegistries.ITEM.entrySet().stream()
				.filter(entry -> CreativeTabNamespaces.lists(entry.getKey().identifier().getNamespace(), entry.getValue()))
				.sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
				.map(entry -> entry.getValue())
				.toList();
	}

}
