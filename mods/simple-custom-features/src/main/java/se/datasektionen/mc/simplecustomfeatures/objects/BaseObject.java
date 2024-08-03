package se.datasektionen.mc.simplecustomfeatures.objects;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.simplecustomfeatures.ObjectContainer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface BaseObject<R> {

	Codec<BaseObject<?>> REGISTRY_CODEC = ObjectRegistry.REGISTRY.getCodec().dispatch(
			BaseObject::getType, ObjectType::getCodec
	);

	ObjectType<? extends BaseObject<R>, R> getType();

	DataResult<R> createObject();

	default Multimap<Identifier, BaseObject<?>> createChildren(ObjectContainer.Loaded<R> container) {
		return Multimaps.forMap(Map.of());
	}

	default Collection<Registry<?>> getChildrenRegistries() {
		return List.of();
	}

	default void onUnregister(RegistryEntry<R> entry) {}
	default void onRegistrationFail(R value) {}
	default void onRegistrationSuccess(RegistryEntry.Reference<R> entry) {}

}
