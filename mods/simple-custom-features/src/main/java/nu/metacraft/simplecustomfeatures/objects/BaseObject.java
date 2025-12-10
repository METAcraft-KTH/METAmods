package nu.metacraft.simplecustomfeatures.objects;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.simplecustomfeatures.ObjectContainer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

public interface BaseObject<R> {

	String NO_ERROR_PREFIX = "SkipError";

	Codec<BaseObject<?>> REGISTRY_CODEC = ObjectRegistry.REGISTRY.byNameCodec().dispatch(
			BaseObject::getType, ObjectType::getCodec
	);

	ObjectType<? extends BaseObject<R>, R> getType();

	default DataResult<R> createObject(ResourceKey<R> id, @Nullable HolderLookup.Provider lookup) {
		return createObject(id);
	}

	default DataResult<R> createObject(ResourceKey<R> id) {
		throw new IllegalStateException("createObject must be implemented, either with or without lookup!");
	}

	default Multimap<Identifier, BaseObject<?>> createChildren(ObjectContainer.Loaded<R> container) {
		return Multimaps.forMap(Map.of());
	}

	default Collection<Registry<?>> getChildrenRegistries() {
		return List.of();
	}

	default void onUnregister(Holder<R> entry) {}
	default void onRegistrationFail(Identifier id, R value) {}
	default void onRegistrationSuccess(Holder.Reference<R> entry) {}

}
