package se.datasektionen.mc.metacraft_core.item;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;

public class METAcraftItems {

	private static final Item PORTAL_PADDING = register("portal_padding", new PolymerBlockItem(METAcraftBlocks.PORTAL_PADDING, new Item.Settings(), Items.ENDER_PEARL));
	private static final Item CAMPUS_LODESTONE = register("campus_lodestone", new TexturedPolymerBlockItem(METAcraftBlocks.CAMPUS_LODESTONE, new Item.Settings(), Items.FLINT, 10052));

	public static void init() {

	}

	private static Item register(String id, Item item) {
		if (item instanceof BlockItem b) {
			Item.BLOCK_ITEMS.put(b.getBlock(), b);
		}
		return Registry.register(Registries.ITEM, METAcraftCore.getID(id), item);
	}

}
