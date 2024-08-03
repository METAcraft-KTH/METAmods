package se.datasektionen.mc.simplecustomfeatures;

import net.minecraft.block.Block;
import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.collection.IdList;
import se.datasektionen.mc.simplecustomfeatures.mixin.AccessorIdList;

import java.util.HashSet;
import java.util.Set;

public class RegistryHelper {

	public static boolean unlockRegistry(Registry<?> registry) {
		if (registry instanceof SimpleRegistry<?>) {
			return ((RegistryExtensions) registry).simpleCustomFeatures$unfreezeRegistry();
		}
		return false;
	}

	public static void lockRegistry(Registry<?> registry) {
		if (registry instanceof SimpleRegistry<?>) {
			registry.freeze();
		}
	}

	/**
	 * Removes all block states for the given block from the block state id list.
	 * Put this in {@link se.datasektionen.mc.simplecustomfeatures.objects.BaseObject#onUnregister(RegistryEntry)}
	 * for all block objects.
	 * @param block The block to remove the states for.
	 */
	public static void removeBlockStatesFor(Block block) {
		removeFromIdList(Block.STATE_IDS, new HashSet<>(block.getStateManager().getStates()));
	}

	/**
	 * Removes the given elements from the given id list.
	 * @param list The id list.
	 * @param elements The elements to remove.
	 * @param <T> The type of the elements.
	 */
	public static <T> void removeFromIdList(IdList<T> list, Set<T> elements) {
		var listAccessor = (AccessorIdList<T>) list;
		listAccessor.getList().removeIf(elements::contains);
		for (var element : elements) {
			var id = listAccessor.getIdMap().removeInt(element);
			listAccessor.getIdMap().reference2IntEntrySet().forEach(
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
	 * Add this to {@link se.datasektionen.mc.simplecustomfeatures.objects.BaseObject#onRegistrationSuccess(RegistryEntry.Reference)}
	 * to add the block to the block state id list, initialize the shape cache and loot table key.
	 * @param block The block that was recently registered.
	 */
	public static void finishBlockRegistration(Block block) {
		block.getStateManager().getStates().forEach(state -> {
			Block.STATE_IDS.add(state);
			state.initShapeCache();
		});
		block.getLootTableKey();
	}

	/**
	 * Some objects, such as {@link Block} and {@link net.minecraft.item.Item} create registry entries immediately when the object is created.
	 * This is a problem, because if we create an object, and then never register it, the game will crash.
	 * Basically, put this into {@link se.datasektionen.mc.simplecustomfeatures.objects.BaseObject#onRegistrationFail(Object)}
	 * to make sure the game doesn't crash when using any object that registers intrusive entries.
	 * @param registry The registry to remove the intrusive entry from.
	 * @param object The object to remove.
	 * @param <T> The type of the object.
	 */
	public static <T> void removeIntrusiveEntry(Registry<T> registry, T object) {
		((RegistryExtensions<T>) registry).simpleCustomFeatures$removeIntrusiveEntry(object);
	}
}
