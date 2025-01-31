package se.datasektionen.mc.cutscenes.util;

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import org.jetbrains.annotations.NotNull;
import org.pcollections.*;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class PGeneralMultimap<K, V> implements PMultimap<K, V> {

	private final PSet<V> emptySet;
	private final PMap<K, PSet<V>> emptyMap;
	private final PMap<K, PSet<V>> innerMap;

	public PGeneralMultimap(PSet<V> emptySet, PMap<K, PSet<V>> emptyMap) {
		this(emptySet, emptyMap, emptyMap);
	}

	protected PGeneralMultimap(PSet<V> emptySet, PMap<K, PSet<V>> emptyMap, PMap<K, PSet<V>> innerMap) {
		this.emptySet = emptySet;
		this.emptyMap = emptyMap;
		this.innerMap = innerMap;

		if (!emptySet.isEmpty() || !emptyMap.isEmpty()) {
			throw new IllegalStateException("Empty sets and maps must be empty!");
		}
	}

	private static final PGeneralMultimap<Object, Object> EMPTY_HASH = new PGeneralMultimap<>(HashTreePSet.empty(), HashTreePMap.empty());

	public static <K, V> PMultimap<K, V> emptyHashBased() {
		return (PMultimap<K, V>) EMPTY_HASH;
	}

	@Override
	public int size() {
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return innerMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return innerMap.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return innerMap.values().stream().anyMatch(set -> set.contains(value));
	}

	@Override
	public boolean containsEntry(Object key, Object value) {
		return innerMap.getOrDefault(key, emptySet).contains(value);
	}

	protected PGeneralMultimap<K, V> with(PMap<K, PSet<V>> innerMap) {
		return new PGeneralMultimap<>(emptySet, emptyMap, innerMap);
	}

	@Override
	public PMultimap<K, V> plus(K key, V value) {
		return with(innerMap.plus(key, innerMap.getOrDefault(key, emptySet).plus(value)));
	}

	@Override
	public PMultimap<K, V> plusAll(Multimap<? extends K, ? extends V> map) {
		PMultimap<K, V> result = this;
		for (var e : map.entries()) {
			result = plus(e.getKey(), e.getValue());
		}
		return result;
	}

	@Override
	public PMultimap<K, V> minus(Object key) {
		return with(innerMap.minus(key));
	}

	@Override
	public PMultimap<K, V> minus(Object key, Object value) {
		var s = innerMap.getOrDefault(key, emptySet).minus(value);
		if (s.isEmpty()) {
			return with(innerMap.minus(key));
		} else {
			try {
				return with(innerMap.plus((K) key, s));
			} catch (ClassCastException ignored) {
				return this;
			}
		}
	}

	@Override
	public PMultimap<K, V> minusAll(Collection<?> keys) {
		return with(innerMap.minusAll(keys));
	}

	@Override
	public boolean put(K key, V value) {
		throw new UnsupportedOperationException("PMap cannot be modified");
	}

	@Override
	public boolean remove(Object key, Object value) {
		throw new UnsupportedOperationException("PMap cannot be modified");
	}

	@Override
	public boolean putAll(K key, Iterable<? extends V> values) {
		throw new UnsupportedOperationException("PMap cannot be modified");
	}

	@Override
	public boolean putAll(Multimap<? extends K, ? extends V> multimap) {
		throw new UnsupportedOperationException("PMap cannot be modified");
	}

	@Override
	public Collection<V> replaceValues(K key, Iterable<? extends V> values) {
		throw new UnsupportedOperationException("PMap cannot be modified");
	}

	@Override
	public Collection<V> removeAll(Object key) {
		throw new UnsupportedOperationException("PMap cannot be modified");
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException("PMap cannot be modified");
	}

	@Override
	public @NotNull Collection<V> get(K key) {
		return innerMap.getOrDefault(key, emptySet);
	}

	@Override
	public @NotNull Set<K> keySet() {
		return innerMap.keySet();
	}

	@Override
	public @NotNull Multiset<K> keys() {
		return innerMap.entrySet().stream().collect(ImmutableMultiset.toImmutableMultiset(
				Map.Entry::getKey, e -> e.getValue().size()
		));
	}

	@Override
	public @NotNull Collection<V> values() {
		return innerMap.values().stream().flatMap(Collection::stream).toList();
	}

	@Override
	public @NotNull Collection<Map.Entry<K, V>> entries() {
		return innerMap.entrySet().stream().flatMap(
				e -> e.getValue().stream().map(
						v -> (Map.Entry<K, V>) new PEntry<>(e.getKey(), v)
				)
		).toList();
	}

	@Override
	public @NotNull PMap<K, Collection<V>> asMap() {
		return (PMap<K, Collection<V>>) (Object) innerMap;
	}

	public static class PEntry<K, V> implements Map.Entry<K, V> {

		private final K key;
		private final V value;
		
		public PEntry(K key, V value) {
			this.key = key;
			this.value = value;
		}
		
		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(V v) {
			throw new UnsupportedOperationException("PMap cannot be modified");
		}
	}
}
