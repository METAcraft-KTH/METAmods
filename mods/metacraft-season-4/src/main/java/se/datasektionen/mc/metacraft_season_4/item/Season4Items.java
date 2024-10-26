package se.datasektionen.mc.metacraft_season_4.item;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import se.datasektionen.mc.metacraft_season_4.Season4;
import se.datasektionen.mc.metacraft_season_4.block.Season4Blocks;

import java.util.List;
import java.util.function.Function;

public class Season4Items {

	private static final Item CAMPUS_LODESTONE = register(
			"campus_lodestone", settings -> new PolymerBlockItem(
				Season4Blocks.CAMPUS_LODESTONE, settings,
				Items.STRUCTURE_VOID, true
			),
			new Item.Settings().component(DataComponentTypes.LORE, new LoreComponent(List.of(
				Text.translatable("block.metacraft.campus_lodestone.lore1").styled(style -> style.withColor(Formatting.WHITE).withItalic(false)),
				Text.translatable("block.metacraft.campus_lodestone.lore2").styled(style -> style.withColor(Formatting.RED).withItalic(false)),
				Text.empty(),
				Text.translatable("block.metacraft.campus_lodestone.lore3"),
				Text.translatable("block.metacraft.campus_lodestone.lore4")
			))).component(DataComponentTypes.RARITY, Rarity.EPIC).maxCount(1)
	);

	public static void init() {

	}

	private static Item register(
			String id, Function<Item.Settings, Item> item, Item.Settings settings
	) {
		var key = RegistryKey.of(RegistryKeys.ITEM, Season4.getID(id));
		if (item instanceof BlockItem b) {
			Item.BLOCK_ITEMS.put(b.getBlock(), b);
		}
		return Registry.register(Registries.ITEM, key, item.apply(settings.registryKey(key)));
	}

}
