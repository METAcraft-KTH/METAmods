package se.datasektionen.mc.metacraft_season_4.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.item.TexturedPolymerBlockItem;
import se.datasektionen.mc.metacraft_season_4.block.Season4Blocks;

import java.util.List;

public class Season4Items {

	private static final Item CAMPUS_LODESTONE = register("campus_lodestone", new TexturedPolymerBlockItem(
		Season4Blocks.CAMPUS_LODESTONE,
		new Item.Settings().component(DataComponentTypes.LORE, new LoreComponent(List.of(
			Text.translatable("block.metacraft.campus_lodestone.lore1").styled(style -> style.withColor(Formatting.WHITE).withItalic(false)),
			Text.translatable("block.metacraft.campus_lodestone.lore2").styled(style -> style.withColor(Formatting.RED).withItalic(false)),
			Text.empty(),
			Text.translatable("block.metacraft.campus_lodestone.lore3"),
			Text.translatable("block.metacraft.campus_lodestone.lore4")
		))).component(DataComponentTypes.RARITY, Rarity.EPIC).maxCount(1),
		Items.STRUCTURE_VOID,
		10052
	));

	public static void init() {

	}

	private static Item register(String id, Item item) {
		if (item instanceof BlockItem b) {
			Item.BLOCK_ITEMS.put(b.getBlock(), b);
		}
		return Registry.register(Registries.ITEM, METAcraftCore.getID(id), item);
	}

}
