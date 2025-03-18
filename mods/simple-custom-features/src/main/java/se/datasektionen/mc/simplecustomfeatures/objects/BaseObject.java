package se.datasektionen.mc.simplecustomfeatures.objects;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.simplecustomfeatures.ObjectContainer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface BaseObject<R> {

	String NO_ERROR_PREFIX = "SkipError";

	Codec<BaseObject<?>> REGISTRY_CODEC = ObjectRegistry.REGISTRY.getCodec().dispatch(
			BaseObject::getType, ObjectType::getCodec
	);

	ObjectType<? extends BaseObject<R>, R> getType();

	default DataResult<R> createObject(RegistryKey<R> id, @Nullable RegistryWrapper.WrapperLookup lookup) {
		return createObject(id);
	}

	@Deprecated
	default DataResult<R> createObject(RegistryKey<R> id) {
		throw new IllegalStateException("createObject must be implemented, either with or without lookup!");
	}

	default Multimap<Identifier, BaseObject<?>> createChildren(ObjectContainer.Loaded<R> container) {
		return Multimaps.forMap(Map.of());
	}

	default Collection<Registry<?>> getChildrenRegistries() {
		return List.of();
	}

	default void onUnregister(RegistryEntry<R> entry) {}
	default void onRegistrationFail(Identifier id, R value) {}
	default void onRegistrationSuccess(RegistryEntry.Reference<R> entry) {}

}
