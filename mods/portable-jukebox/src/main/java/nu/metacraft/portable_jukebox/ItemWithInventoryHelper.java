package nu.metacraft.portable_jukebox;

import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public class ItemWithInventoryHelper {

	public static boolean canContainItems(ItemStack stack) {
		return stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS);
	}

	public static Stream<ItemStack> getRecursiveInventoryContents(ItemStack stack) {
		if (stack.has(DataComponents.CONTAINER)) {
			return stack.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().flatMap(
					content -> Stream.concat(Stream.of(content), ItemWithInventoryHelper.getRecursiveInventoryContents(content))
			);
		}
		if (stack.has(DataComponents.BUNDLE_CONTENTS)) {
			return stack.get(DataComponents.BUNDLE_CONTENTS).itemCopies().flatMap(
					content -> Stream.concat(Stream.of(content), ItemWithInventoryHelper.getRecursiveInventoryContents(content))
			);
		}
		return Stream.empty();
	}

	public static <T> Stream<T> getAllComponents(ItemStack stack, DataComponentType<T> componentType) {
		var topComponent = stack.has(componentType) ? Stream.of(stack.get(componentType)) : Stream.<T>empty();
		if (canContainItems(stack)) {
			return Stream.concat(topComponent, getRecursiveInventoryContents(stack).flatMap(subStack -> getAllComponents(subStack, componentType)));
		}
		return topComponent;
	}

}
