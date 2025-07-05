package nu.metacraft.core.preferences;

import com.mojang.serialization.Codec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.core.extensions.ServerPlayerEntityExtensions;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PreferenceData {

	private final Map<RegistryEntry<? extends Preference<?, ? ,?>>, Object> valueMap = new HashMap<>();

	private static final Codec<Map<RegistryEntry<? extends Preference<?, ? ,?>>, Object>> VALUE_MAP_CODEC = Codec.dispatchedMap(
			Preference.REGISTRY_CODEC.xmap(t -> t, t -> (RegistryEntry<Preference<?, ?, ?>>) t),
			pref -> pref.value().type().getValueCodec()
	);

	public static final Codec<PreferenceData> CODEC = VALUE_MAP_CODEC.xmap(
			PreferenceData::new, data -> data.valueMap
	);

	public PreferenceData(Map<RegistryEntry<? extends Preference<?, ? ,?>>, Object> map) {
		this.valueMap.putAll(map);
	}

	public PreferenceData() {}

	public void applyFrom(PreferenceData other) {
		for (var k : other.keySet()) {
			set(k, other.get(downcast(k)));
		}
	}

	@SuppressWarnings("unchecked")
	public <V, P extends Preference<?, V, ?>> V get(RegistryEntry<P> pref) {
		return (V) valueMap.getOrDefault(pref, pref.value().defaultValue());
	}

	@SuppressWarnings("unchecked")
	public <V> V set(RegistryEntry<? extends Preference<?, ? extends V, ?>> pref, V value) {
		return (V) valueMap.put(pref, value);
	}

	public Set<RegistryEntry<? extends Preference<?, ? ,?>>> keySet() {
		return valueMap.keySet();
	}

	public static PreferenceData getForPlayer(ServerPlayerEntity player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_core$getPreferences();
	}

	@SuppressWarnings("unchecked")
	public static <T, V, P extends Preference<? extends T, ? extends V, ?>> RegistryEntry<Preference<T, V, ?>> downcast(RegistryEntry<P> pref) {
		return (RegistryEntry<Preference<T, V, ?>>) pref;
	}

	public void initDefaultValues(ServerPlayerEntity player) {
		var registry = player.getRegistryManager().getOrThrow(Preference.REGISTRY_KEY);
		registry.streamEntries().forEach(
				e -> {
					if (!valueMap.containsKey(e)) {
						valueMap.put(e, e.value().defaultValue());
						downcast(e).value().type().initDefaultValue(player, downcast(e));
					}
				}
		);
	}

}
