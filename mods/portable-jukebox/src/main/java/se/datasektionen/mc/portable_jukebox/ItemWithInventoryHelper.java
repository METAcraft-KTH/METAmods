package se.datasektionen.mc.portable_jukebox;

import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

import java.util.stream.Stream;

public class ItemWithInventoryHelper {

	public static boolean canContainItems(ItemStack stack) {
		return stack.contains(DataComponentTypes.CONTAINER) || stack.contains(DataComponentTypes.BUNDLE_CONTENTS);
	}

	public static Stream<ItemStack> getRecursiveInventoryContents(ItemStack stack) {
		if (stack.contains(DataComponentTypes.CONTAINER)) {
			return stack.get(DataComponentTypes.CONTAINER).streamNonEmpty().flatMap(
					content -> Stream.concat(Stream.of(content), ItemWithInventoryHelper.getRecursiveInventoryContents(content))
			);
		}
		if (stack.contains(DataComponentTypes.BUNDLE_CONTENTS)) {
			return stack.get(DataComponentTypes.BUNDLE_CONTENTS).stream().flatMap(
					content -> Stream.concat(Stream.of(content), ItemWithInventoryHelper.getRecursiveInventoryContents(content))
			);
		}
		return Stream.empty();
	}

	public static <T> Stream<T> getAllComponents(ItemStack stack, ComponentType<T> componentType) {
		var topComponent = stack.contains(componentType) ? Stream.of(stack.get(componentType)) : Stream.<T>empty();
		if (canContainItems(stack)) {
			return Stream.concat(topComponent, getRecursiveInventoryContents(stack).flatMap(subStack -> getAllComponents(subStack, componentType)));
		}
		return topComponent;
	}

}
