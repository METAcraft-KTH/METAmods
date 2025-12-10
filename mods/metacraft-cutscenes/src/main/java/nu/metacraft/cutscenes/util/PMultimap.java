package nu.metacraft.cutscenes.util;

import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.pcollections.PMap;

import java.util.Collection;

public interface PMultimap<K, V> extends Multimap<K ,V> {

	/**
	 * @param key
	 * @param value
	 * @return a map with the mappings of this but with key mapped to value
	 */
	PMultimap<K, V> plus(K key, V value);

	/**
	 * @param map
	 * @return this combined with map, with map's mappings used for any keys in both map and this
	 */
	PMultimap<K, V> plusAll(Multimap<? extends K, ? extends V> map);

	/**
	 * @param key
	 * @return a map with the mappings of this but with no value for key
	 */
	PMultimap<K, V> minus(Object key);

	/**
	 * @param key
	 * @return a map with the mappings of this but with no value for key
	 */
	PMultimap<K, V> minus(Object key, Object value);

	/**
	 * @param keys
	 * @return a map with the mappings of this but with no value for any element of keys
	 */
	PMultimap<K, V> minusAll(Collection<?> keys);

	@Override
	@Deprecated
	boolean put(K key, V value);

	@Override
	@Deprecated
	boolean remove(Object key, Object value);

	@Override
	@Deprecated
	boolean putAll(K key, Iterable<? extends V> values);

	@Override
	@Deprecated
	boolean putAll(Multimap<? extends K, ? extends V> multimap);

	@Override
	@Deprecated
	Collection<V> replaceValues(K key, Iterable<? extends V> values);

	@Override
	@Deprecated
	Collection<V> removeAll(Object key);

	@Override
	@Deprecated
	void clear();

	@Override
	@NotNull PMap<K, Collection<V>> asMap();

}
