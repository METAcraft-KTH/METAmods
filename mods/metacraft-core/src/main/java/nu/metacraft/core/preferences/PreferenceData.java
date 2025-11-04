package nu.metacraft.core.preferences;

import com.mojang.serialization.Codec;
import nu.metacraft.core.extensions.ServerPlayerExtensions;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;

public class PreferenceData {

	private final Map<Holder<? extends Preference<?, ? ,?>>, Object> valueMap = new HashMap<>();

	private static final Codec<Map<Holder<? extends Preference<?, ? ,?>>, Object>> VALUE_MAP_CODEC = Codec.dispatchedMap(
			Preference.REGISTRY_CODEC.xmap(t -> t, t -> (Holder<Preference<?, ?, ?>>) t),
			pref -> pref.value().type().getValueCodec()
	);

	public static final Codec<PreferenceData> CODEC = VALUE_MAP_CODEC.xmap(
			PreferenceData::new, data -> data.valueMap
	);

	public PreferenceData(Map<Holder<? extends Preference<?, ? ,?>>, Object> map) {
		this.valueMap.putAll(map);
	}

	public PreferenceData() {}

	public void applyFrom(PreferenceData other) {
		for (var k : other.keySet()) {
			set(k, other.get(downcast(k)));
		}
	}

	@SuppressWarnings("unchecked")
	public <V, P extends Preference<?, V, ?>> V get(Holder<P> pref) {
		return (V) valueMap.getOrDefault(pref, pref.value().defaultValue());
	}

	@SuppressWarnings("unchecked")
	public <V> V set(Holder<? extends Preference<?, ? extends V, ?>> pref, V value) {
		return (V) valueMap.put(pref, value);
	}

	public Set<Holder<? extends Preference<?, ? ,?>>> keySet() {
		return valueMap.keySet();
	}

	public static PreferenceData getForPlayer(ServerPlayer player) {
		return ((ServerPlayerExtensions) player).metacraft_core$getPreferences();
	}

	@SuppressWarnings("unchecked")
	public static <T, V, P extends Preference<? extends T, ? extends V, ?>> Holder<Preference<T, V, ?>> downcast(Holder<P> pref) {
		return (Holder<Preference<T, V, ?>>) pref;
	}

	public void initDefaultValues(ServerPlayer player) {
		var registry = player.registryAccess().lookupOrThrow(Preference.REGISTRY_KEY);
		registry.listElements().forEach(
				e -> {
					if (!valueMap.containsKey(e)) {
						valueMap.put(e, e.value().defaultValue());
						downcast(e).value().type().initDefaultValue(player, downcast(e));
					}
				}
		);
	}

}
