package nu.metacraft.simplecustomfeatures;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import nu.metacraft.simplecustomfeatures.extension.DataComponentInitializersExtension;
import nu.metacraft.simplecustomfeatures.extension.InitializerEntryExtension;
import nu.metacraft.simplecustomfeatures.mixin.DataComponentInitializersAccessor;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.mixin.IdMapperAccessor;

import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

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
		var listAccessor = (IdMapperAccessor<T>) list;
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

	public static void removeComponentInitializer(Object object) {
		((DataComponentInitializersExtension) BuiltInRegistries.DATA_COMPONENT_INITIALIZERS).simple_custom_features$removeInitializer(
				object
		);
	}

	public static <T> Optional<DataComponentInitializers.InitializerEntry<T>> getComponentInitializer(ResourceKey<T> key, T object) {
		var initializers = ((DataComponentInitializersAccessor) BuiltInRegistries.DATA_COMPONENT_INITIALIZERS).getInitializers();
		for (var initializer : initializers) {
			//noinspection ConstantValue
			if (initializer.key() == key && ((InitializerEntryExtension) (Object) initializer).simple_custom_features$getObject() == object) {
				//noinspection unchecked
				return Optional.of((DataComponentInitializers.InitializerEntry<T>) initializer);
			}
		}
		return Optional.empty();
	}

	public static <T> void addInitializer(
			T object, DataComponentInitializers.Initializer<T> initializerToAdd
	) {
		var initializers = ((DataComponentInitializersAccessor) BuiltInRegistries.DATA_COMPONENT_INITIALIZERS).getInitializers();
		for (var initializer : initializers) {
			if (((InitializerEntryExtension) (Object) initializer).simple_custom_features$getObject() == object) {
				//noinspection unchecked
				var actualInitializer = (DataComponentInitializers.InitializerEntry<T>) initializer;
				var newInitializer = actualInitializer.initializer().andThen(initializerToAdd);
				//noinspection DataFlowIssue
				((DataComponentInitializersAccessor.InitializerEntry) (Object) actualInitializer).setInitializer(newInitializer);
				break;
			}
		}
	}

	public static <T> DataComponentMap getComponentsFor(ResourceKey<T> key, T object, HolderLookup.Provider lookup) {
		var initializer = RegistryHelper.getComponentInitializer(key, object);
		var builder = DataComponentMap.builder();
		initializer.orElseThrow().run(
				builder, lookup
		);
		return builder.build();
	}
}
