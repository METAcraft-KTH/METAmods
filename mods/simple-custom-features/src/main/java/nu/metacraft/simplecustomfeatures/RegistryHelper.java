package nu.metacraft.simplecustomfeatures;

import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.mixin.AccessorIdList;

import java.util.Set;
import net.minecraft.core.IdMapper;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;

public class RegistryHelper {

	public static boolean unlockRegistry(Registry<?> registry) {
		if (registry instanceof MappedRegistry<?>) {
			return ((RegistryExtensions) registry).simpleCustomFeatures$unfreezeRegistry();
		}
		return false;
	}

	public static void lockRegistry(Registry<?> registry) {
		if (registry instanceof MappedRegistry<?> simple) {
			simple.bindAllTagsToEmpty();
			registry.freeze();
		}
	}

	/**
	 * Removes the given elements from the given id list.
	 * @param list The id list.
	 * @param elements The elements to remove.
	 * @param <T> The type of the elements.
	 */
	public static <T> void removeFromIdList(IdMapper<T> list, Set<T> elements) {
		var listAccessor = (AccessorIdList<T>) list;
		listAccessor.getIdToT().removeIf(elements::contains);
		for (var element : elements) {
			var id = listAccessor.getTToId().removeInt(element);
			listAccessor.getTToId().reference2IntEntrySet().forEach(
					stateEntry -> {
						if (stateEntry.getIntValue() > id) {
							stateEntry.setValue(stateEntry.getIntValue()-1);
						}
					}
			);
			listAccessor.setNextId(listAccessor.getNextId()-1);
		}
	}

	/**
	 * Some objects, such as {@link Block} and {@link net.minecraft.world.item.Item} create registry entries immediately when the object is created.
	 * This is a problem, because if we create an object, and then never register it, the game will crash.
	 * Basically, put this into {@link BaseObject#onRegistrationFail(Object)}
	 * to make sure the game doesn't crash when using any object that registers intrusive entries.
	 * @param registry The registry to remove the intrusive entry from.
	 * @param object The object to remove.
	 * @param <T> The type of the object.
	 */
	public static <T> void removeIntrusiveEntry(Registry<T> registry, T object) {
		((RegistryExtensions<T>) registry).simpleCustomFeatures$removeIntrusiveEntry(object);
	}
}
