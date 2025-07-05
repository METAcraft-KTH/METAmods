package nu.metacraft.simplecustomfeatures.objects.items.simple;

import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;

/**
 * Note, be careful when adding more functions here. The names used at runtime will be obfuscated and therefore might not match!
 */
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

	public static Item getPolymerItem(@This CustomisedItem item) {
		var settings = item.simple_custom_features$getSettings();
		return settings.disguise().map(ItemStack::getItem).orElse(settings.itemSettings().baseItem().value());
	}

	public static ItemStack getPolymerItemStack(
			@This CustomisedItem item, @SuperCall Callable<ItemStack> superCall
	) throws Exception {
		var settings = item.simple_custom_features$getSettings();
		var stack = superCall.call();
		return settings.disguise().map(result -> {
			result.applyChanges(stack.getComponentChanges());
			result.setCount(stack.getCount());
			return result;
		}).orElse(stack);
	}

}
