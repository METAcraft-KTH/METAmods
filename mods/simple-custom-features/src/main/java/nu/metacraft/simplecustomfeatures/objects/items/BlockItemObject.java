package nu.metacraft.simplecustomfeatures.objects.items;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;
import xyz.nucleoid.packettweaker.PacketContext;

public class BlockItemObject implements BaseItem {

	public static final MapCodec<BlockItemObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Item.CODEC.fieldOf("display_item").forGetter(o -> o.displayItem),
					ITEM_SETTINGS_CODEC.forGetter(o -> o.settings),
					BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(o -> o.block)
			).apply(instance, BlockItemObject::new)
	);

	private final Holder<Item> displayItem;
	private final ItemSettings settings;
	private final Block block;

	public BlockItemObject(
			Holder<Item> displayItem, ItemSettings settings, Block block
	) {
		this.displayItem = displayItem;
		this.settings = settings;
		this.block = block;
	}

	@Override
	public ObjectType<? extends BaseObject<Item>, Item> getType() {
		return ObjectRegistry.BLOCK_ITEM;
	}

	@Override
	public DataResult<Item> createObject(ResourceKey<Item> id) {
		return settings.makeSettings(id, BaseItem.getModel(displayItem)).map(
				settings -> new CustomBlockItem(block, settings.useBlockDescriptionPrefix(), this)
		);
	}

	public static class CustomBlockItem extends BlockItem implements PolymerItem {

		private final BlockItemObject object;

		public CustomBlockItem(Block block, net.minecraft.world.item.Item.Properties settings, BlockItemObject object) {
			super(block, settings);
			this.object = object;
		}

		@Override
		public Item getPolymerItem(ItemStack itemStack, PacketContext ctx) {
			return object.displayItem.value();
		}
	}
}
