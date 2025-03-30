package se.datasektionen.mc.metacraft_core.preferences;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.dynamic.Codecs;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

import java.util.List;
import java.util.function.Predicate;

public record Preference<T, V, P extends Predicate<V>>(
		PreferenceType<T, V, P> type, T definition, List<PreferenceType.Icon<V, P>> icons, V defaultValue
) {

	public static final RegistryKey<Registry<Preference<?, ?, ?>>> REGISTRY_KEY = RegistryKey.ofRegistry(METAcraftCore.getID("preference"));

	private static <T, V, P extends Predicate<V>> MapCodec<Preference<T, V, P>> fixType(PreferenceType<T, V, P> type) {
		return RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						type.getDefinitionCodec().forGetter(Preference::definition),
						Codecs.nonEmptyList(type.getIconCodec().listOf()).fieldOf("icons").forGetter(Preference::icons),
						type.getValueCodec().fieldOf("default_value").forGetter(Preference::defaultValue)
				).apply(instance, (pref, icon, defaultValue) -> new Preference<>(type, pref, icon, defaultValue))
		);
	}

	private static final Codec<Preference<?, ?, ?>> CODEC = PreferenceType.REGISTRY.getCodec().dispatch(
			Preference::type, Preference::fixType
	);

	public static final Codec<RegistryEntry<Preference<?, ?, ?>>> REGISTRY_CODEC = RegistryElementCodec.of(REGISTRY_KEY, CODEC, false);

	public static void init() {
		DynamicRegistries.register(REGISTRY_KEY, CODEC);
	}

}
