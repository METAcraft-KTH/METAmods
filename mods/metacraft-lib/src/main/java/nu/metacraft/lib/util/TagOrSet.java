package nu.metacraft.lib.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public record TagOrSet<T>(Either<Set<ResourceKey<T>>, TagKey<T>> elements) {

	private static final TagOrSet<?> EMPTY = new TagOrSet<>(Either.left(Set.of()));

	@SuppressWarnings("unchecked")
	public static <T> TagOrSet<T> empty() {
		return (TagOrSet<T>) EMPTY;
	}

	public static <T> Codec<TagOrSet<T>> codec(ResourceKey<? extends Registry<T>> key) {
		var keyCodec = ResourceKey.codec(key);
		return Codec.either(
				METACodecs.<ResourceKey<T>, Set<ResourceKey<T>>>collectionOrSingleCodec(
						keyCodec, METACodecs.createCollectionCodec(keyCodec, HashSet::new),
						e -> new HashSet<>(Set.of(e))
				),
				TagKey.codec(key)
		).xmap(
				TagOrSet::new, e -> e.elements
		);
	}

	public HolderSet<T> getElements(HolderLookup.Provider access) {
		return elements.map(
				set -> HolderSet.direct(set.stream().map(access::get).filter(Optional::isPresent).map(Optional::get).toList()),
				tag -> access.lookup(tag.registry()).flatMap(reg -> reg.get(tag)).map(s -> (HolderSet<T>) s).orElse(HolderSet.empty())
		);
	}

}
