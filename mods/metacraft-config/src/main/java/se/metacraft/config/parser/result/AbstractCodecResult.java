package se.metacraft.config.parser.result;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.Unit;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.MetadataMap;
import se.metacraft.config.parser.metadata.Container;
import se.metacraft.config.parser.metadata.ContainerType;
import se.metacraft.config.util.helper.CodecInternalsHelper;

import java.util.Optional;
import java.util.function.Predicate;

public interface AbstractCodecResult {

	default boolean isEmpty() {
		return false;
	}

	default AbstractCodecResult underlying() {
		return AbstractCodecResult.EMPTY;
	}

	default PVector<AbstractCodecResult> components() {
		return TreePVector.empty();
	}

	default PVector<AbstractCodecResult> thisAndAllUnderlying() {
		return TreePVector.singleton(this).plusAll(allUnderlying());
	}

	default PVector<AbstractCodecResult> allUnderlying() {
		if (isEmpty()) return TreePVector.empty();
		return TreePVector.singleton(underlying()).plusAll(underlying().allUnderlying());
	}

	default boolean isContainerType(ContainerType<?> type) {
		return metadata(MetadataKey.CONTAINER).map(Container::type).map(t -> t == type).orElse(false);
	}

	default boolean hasContainerType(ContainerType<?> type) {
		return nestedMetadata(MetadataKey.CONTAINER).map(Container::type).map(t -> t == type).orElse(false);
	}

	default Optional<AbstractCodecResult> getContainer(ContainerType<?> container) {
		return getUnderlying(c -> c.isContainerType(container));
	}

	default <T extends Container.ContainerData> Optional<T> getContainerData(ContainerType<T> container) {
		//noinspection unchecked
		return nestedMetadata(MetadataKey.CONTAINER).filter(data -> data.type() == container).map(
			data -> (T) data.data()
		);
	}

	default Optional<AbstractCodecResult> getUnderlying(Predicate<AbstractCodecResult> toMatch) {
		if (toMatch.test(this)) return Optional.of(this);
		if (isEmpty()) return Optional.empty();
		return underlying().getUnderlying(toMatch);
	}

	default <T extends Metadata> Optional<T> nestedMetadata(MetadataKey<T> metadata) {
		var meta = metadata(metadata);
		if (meta.isPresent()) return meta;
		if (underlying().isEmpty()) return Optional.empty();
		return underlying().nestedMetadata(metadata);
	}

	default PVector<AbstractCodecResult> nestedComponents() {
		if (underlying().isEmpty()) {
			return components();
		} else {
			return underlying().nestedComponents();
		}
	}

	Codec<?> codec();
	default Optional<MapCodec<?>> mapCodec() {
		return Optional.empty();
	}

	default String codecName() {
		if (nestedMetadata(MetadataKey.COMMENTS).isPresent()) return underlying().codecName();
		return mapCodec().map(Object::toString).orElseGet(() -> codec().toString());
	}

	default DataResult<?> convert(Object value, AbstractCodecResult targetType, HolderLookup.Provider lookup) {
		return CodecInternalsHelper.convert(codec(), targetType.codec(), value, lookup);
	}

	MetadataMap metadata();
	default <T extends Metadata> Optional<T> metadata(MetadataKey<T> key) {
		return Optional.ofNullable(metadata().get(key));
	}

	AbstractCodecResult EMPTY = new AbstractCodecResult() {
		@Override
		public MetadataMap metadata() {
			return MetadataMap.EMPTY;
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Codec<?> codec() {
			return MapCodec.unitCodec(Unit.INSTANCE);
		}
	};
}
