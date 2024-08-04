package se.datasektionen.mc.simplecustomfeatures.objects.items;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.block.Block;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.simplecustomfeatures.RegistryHelper;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;

import java.util.Optional;

public class BlockItemObject implements BaseObject<Item> {

	public static final MapCodec<BlockItemObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ItemStack.ITEM_CODEC.fieldOf("display_item").forGetter(o -> o.displayItem),
					ComponentMap.CODEC.fieldOf("components").forGetter(o -> o.components),
					ItemStack.ITEM_CODEC.optionalFieldOf("recipe_remainder").forGetter(o -> o.recipeRemainder),
					Registries.BLOCK.getCodec().fieldOf("block").forGetter(o -> o.block)
			).apply(instance, BlockItemObject::new)
	);

	private final RegistryEntry<Item> displayItem;
	private final ComponentMap components;
	private final Optional<RegistryEntry<Item>> recipeRemainder;
	private final Block block;

	public BlockItemObject(
			RegistryEntry<Item>  displayItem, ComponentMap components,
			Optional<RegistryEntry<Item>> recipeRemainder, Block block
	) {
		this.displayItem = displayItem;
		this.components = components;
		this.recipeRemainder = recipeRemainder;
		this.block = block;
	}

	@Override
	public ObjectType<? extends BaseObject<Item>, Item> getType() {
		return ObjectRegistry.BLOCK_ITEM;
	}

	private static <T> void addComponent(Item.Settings settings, Component<T> component) {
		settings.component(component.type(), component.value());
	}

	@Override
	public DataResult<Item> createObject() {
		var settings = new Item.Settings();
		recipeRemainder.ifPresent(
				remainder -> settings.recipeRemainder(remainder.value())
		);
		try {
			components.forEach(component -> addComponent(settings, component));
		} catch (IllegalStateException err) {
			return DataResult.error(err::getMessage);
		}
		return DataResult.success(
				new CustomBlockItem(block, settings, this)
		);
	}

	@Override
	public void onRegistrationFail(Item value) {
		RegistryHelper.removeIntrusiveEntry(Registries.ITEM, value);
	}

	@Override
	public void onUnregister(RegistryEntry<Item> entry) {
		Item.BLOCK_ITEMS.remove(block, entry.value());
	}

	@Override
	public void onRegistrationSuccess(RegistryEntry.Reference<Item> entry) {
		Item.BLOCK_ITEMS.put(block, entry.value());
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
