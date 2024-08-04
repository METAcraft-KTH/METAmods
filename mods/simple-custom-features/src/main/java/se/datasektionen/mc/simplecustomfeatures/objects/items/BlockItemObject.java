package se.datasektionen.mc.simplecustomfeatures.objects.items;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;

public class BlockItemObject implements BaseItem {

	public static final MapCodec<BlockItemObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ItemStack.ITEM_CODEC.fieldOf("display_item").forGetter(o -> o.displayItem),
					ITEM_SETTINGS_CODEC.forGetter(o -> o.settings),
					Registries.BLOCK.getCodec().fieldOf("block").forGetter(o -> o.block)
			).apply(instance, BlockItemObject::new)
	);

	private final RegistryEntry<Item> displayItem;
	private final ItemSettings settings;
	private final Block block;

	public BlockItemObject(
			RegistryEntry<Item> displayItem, ItemSettings settings, Block block
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
	public DataResult<Item> createObject() {
		return settings.makeSettings().map(
				settings -> new CustomBlockItem(block, settings, this)
		);
	}

	public static class CustomBlockItem extends BlockItem implements PolymerItem {

		private final BlockItemObject object;

		public CustomBlockItem(Block block, Settings settings, BlockItemObject object) {
			super(block, settings);
			this.object = object;
		}

		@Override
		public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
			return object.displayItem.value();
		}
	}
}
