package metacraft.kultur.content;

import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import metacraft.kultur.Kultur;
import metacraft.kultur.catalogue.Catalogue;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The pattern items, one per catalogue pattern marked {@code item}; everything else is data. */
public final class ModContent {
	private static final Map<String, PatternItem> ITEMS = new LinkedHashMap<>();
	private static final List<Consumer<CreativeModeTab.Output>> ALSO_IN_TAB = new ArrayList<>();

	private ModContent() {}

	public static void register(Catalogue catalogue) {
		for (Catalogue.Owned<Catalogue.Pattern> owned : catalogue.patterns()) {
			Catalogue.Pattern p = owned.value();
			if (!p.item()) continue;
			Identifier itemId = Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, p.itemPath());
			TagKey<BannerPattern> tag = TagKey.create(Registries.BANNER_PATTERN, Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, "pattern_item/" + p.id()));
			// The tag resolves against the dynamic registry, which does not exist when items are
			// registered — hence the delayed component, the way vanilla attaches its own pattern tags.
			Item.Properties props = new Item.Properties()
					.setId(ResourceKey.create(Registries.ITEM, itemId))
					.stacksTo(1)
					.rarity(Rarity.RARE)
					.delayedComponent(DataComponents.PROVIDES_BANNER_PATTERNS, lookup -> lookup.getOrThrow(tag));
			PatternItem item = Registry.register(BuiltInRegistries.ITEM, itemId, new PatternItem(props, itemId));
			ITEMS.put(p.id(), item);
		}
		if (!ITEMS.isEmpty()) registerCreativeTab();
	}

	/** The item gating a pattern, by pattern id; null for a free pattern. */
	public static @Nullable PatternItem patternItem(String patternId) {
		return ITEMS.get(patternId);
	}

	public static Map<String, PatternItem> items() {
		return Collections.unmodifiableMap(ITEMS);
	}

	/** More for Kultur's creative tab, after the pattern items: the drinks, when Kultur brews them itself. */
	public static void alsoInCreativeTab(Consumer<CreativeModeTab.Output> items) {
		ALSO_IN_TAB.add(items);
	}

	/**
	 * A creative tab, since a pattern item is found in no other way until a loot table gives one out.
	 */
	private static void registerCreativeTab() {
		Item icon = ITEMS.isEmpty() ? Items.FLOWER_BANNER_PATTERN : ITEMS.values().iterator().next();
		CreativeModeTab tab = PolymerCreativeModeTabUtils.builder()
				.title(Component.translatable("itemGroup." + Kultur.NAMESPACE))
				.icon(() -> new ItemStack(icon))
				.displayItems((params, output) -> {
					ITEMS.values().forEach(output::accept);
					ALSO_IN_TAB.forEach(also -> also.accept(output));
				})
				.build();
		PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, "main"), tab);
	}
}
