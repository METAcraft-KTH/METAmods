package se.metacraft.core.item;

import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import se.metacraft.core.METAcraftCore;
import se.metacraft.lib.CreativeTabNamespaces;

import java.util.Comparator;
import java.util.List;

/**
 * A creative tab, "Spawn Eggs", with every spawn egg our mods add: the spawn eggs in the namespaces opted in to with
 * {@link CreativeTabNamespaces#add}, the same ones the METAcraft tab lists, so other mods' eggs stay out. Like the
 * METAcraft tab, Polymer shows it only to clients that have Polymer.
 *
 * <p>The tab is filled when it is shown, so eggs and namespaces added after metacraft-core count too.
 */
public final class SpawnEggsCreativeTab {

	private SpawnEggsCreativeTab() {}

	public static void init() {
		var tab = PolymerCreativeModeTabUtils.builder()
				.title(Component.literal("Spawn Eggs"))
				.icon(() -> eggs().stream().findFirst().map(ItemStack::new).orElseGet(() -> new ItemStack(Items.PIGLIN_SPAWN_EGG)))
				.displayItems((params, output) -> eggs().forEach(output::accept))
				.build();
		PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(METAcraftCore.getID("spawn_eggs"), tab);
	}

	/** Our spawn eggs, by id. */
	public static List<Item> eggs() {
		return BuiltInRegistries.ITEM.entrySet().stream()
				.filter(entry -> entry.getValue() instanceof SpawnEggItem && CreativeTabNamespaces.lists(entry.getKey().identifier().getNamespace(), entry.getValue()))
				.sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
				.map(entry -> entry.getValue())
				.toList();
	}

}
