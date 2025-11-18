package nu.metacraft.season_5.items;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;
import nu.metacraft.season_5.METAcraftSeason5;
import nu.metacraft.season_5.items.components.Season5Components;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class Season5Items {

	private static final Style LORE_STYLE = Style.EMPTY.applyFormat(ChatFormatting.GRAY).withItalic(false);

	public static final Item BEDROCK_DRILL = register(
			"bedrock_drill", BedrockDrillItem::new, new Item.Properties().stacksTo(16).component(
					DataComponents.LORE, new ItemLore(List.of(
							Component.translatable("item.metacraft.bedrock_drill.desc.1").withStyle(LORE_STYLE),
							Component.translatable("item.metacraft.bedrock_drill.desc.2", Component.keybind("key.use")).withStyle(LORE_STYLE)
					))
			).useCooldown(10)
	);

	public static void init() {
		Season5Components.init();

		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((original, client, context) -> {
			if (original.getItem() instanceof BedrockDrillItem drill) {
				client.set(
						DataComponents.CONSUMABLE,
						new Consumable(
								drill.getUseDuration(original) / 20.0f,
								drill.getUseAnimation(original),
								Holder.direct(new SoundEvent(METAcraftSeason5.getID("silence"), Optional.empty())),
								false, List.of()
						)
				);
			}
			return client;
		});
	}

	private static Item register(
			String id, Function<Item.Properties, Item> constructor, Item.Properties properties
	) {
		var key = ResourceKey.create(Registries.ITEM, METAcraftSeason5.getID(id));
		Item item = constructor.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

}
