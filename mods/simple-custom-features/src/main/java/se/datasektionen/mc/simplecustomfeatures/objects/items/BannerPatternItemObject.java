package se.datasektionen.mc.simplecustomfeatures.objects.items;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.block.entity.BannerPattern;
import net.minecraft.item.BannerPatternItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;

public class BannerPatternItemObject implements BaseItem {

	public static final MapCodec<BannerPatternItemObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ItemStack.ITEM_CODEC.fieldOf("display_item").forGetter(o -> o.displayItem),
					ITEM_SETTINGS_CODEC.forGetter(o -> o.settings),
					TagKey.unprefixedCodec(RegistryKeys.BANNER_PATTERN).fieldOf("pattern_tag").forGetter(o -> o.patternTag)
			).apply(instance, BannerPatternItemObject::new)
	);

	private final RegistryEntry<Item> displayItem;
	private final ItemSettings settings;
	private final TagKey<BannerPattern> patternTag;

	public BannerPatternItemObject(
			RegistryEntry<Item> displayItem, ItemSettings settings, TagKey<BannerPattern> patternTag
	) {
		this.displayItem = displayItem;
		this.settings = settings;
		this.patternTag = patternTag;
	}

	@Override
	public ObjectType<? extends BaseObject<Item>, Item> getType() {
		return ObjectRegistry.BANNER_PATTERN_ITEM;
	}

	@Override
	public DataResult<Item> createObject() {
		return settings.makeSettings().map(
				settings -> new CustomBannerPatternItem(patternTag, settings, this)
		);
	}

	public static class CustomBannerPatternItem extends BannerPatternItem implements PolymerItem {

		private final BannerPatternItemObject object;

		public CustomBannerPatternItem(TagKey<BannerPattern> patternItemTag, Settings settings, BannerPatternItemObject object) {
			super(patternItemTag, settings);
			this.object = object;
		}

		@Override
		public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
			return object.displayItem.value();
		}
	}
}
