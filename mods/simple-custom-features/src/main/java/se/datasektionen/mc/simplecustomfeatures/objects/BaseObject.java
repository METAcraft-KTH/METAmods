package se.datasektionen.mc.simplecustomfeatures.objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.registry.entry.RegistryEntry;

public interface BaseObject<R> {

	Codec<BaseObject<?>> REGISTRY_CODEC = ObjectRegistry.REGISTRY.getCodec().dispatch(
			BaseObject::getType, ObjectType::getCodec
	);

	ObjectType<? extends BaseObject<R>, R> getType();

	DataResult<R> createObject();

	default void onUnregister(RegistryEntry<R> entry) {}
	default void onRegistrationFail(R value) {}
	default void onRegistrationSuccess(RegistryEntry.Reference<R> entry) {}

}
