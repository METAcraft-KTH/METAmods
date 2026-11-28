package se.metacraft.simplecustomfeatures.objects.items.simple;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;

@SuppressWarnings("unused")
public class ItemProxy {

	public static SimpleItem simple_custom_features$getSettings(@This Item item) {
		try {
			Field field = item.getClass().getDeclaredField(SimpleItem.SETTINGS_FIELD_NAME);
			field.setAccessible(true);
			var result = (SimpleItem) field.get(item);
			field.setAccessible(false);
			return result;
		} catch (NoSuchFieldException | IllegalAccessException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Item getPolymerItem(
		@This CustomisedItem item,
		@Argument(0) ItemStack stack,
		@Argument(1) PacketContext context
	) {
		var settings = item.simple_custom_features$getSettings();
		var i = settings.disguise().map(ItemStack::getItem).orElse(settings.itemSettings().baseItem().value());
		if (i instanceof PolymerItem p) {
			return p.getPolymerItem(stack, context);
		}
		return i;
	}

	public static ItemStack getPolymerItemStack(
		@This CustomisedItem item, @SuperCall Callable<ItemStack> superCall,
		@Argument(1) TooltipFlag tooltipType, @Argument(2) PacketContext context,
		@Argument(3) HolderLookup.Provider lookup
	) throws Exception {
		var settings = item.simple_custom_features$getSettings();
		var stack = superCall.call();
		var i = settings.disguise().map(result -> {
			result.applyComponentsAndValidate(stack.getComponentsPatch());
			result.setCount(stack.getCount());
			return result;
		}).orElse(stack);
		if (i != null && i.getItem() instanceof PolymerItem p) {
			return p.getPolymerItemStack(i, tooltipType, context, lookup);
		}
		return i;
	}

}
