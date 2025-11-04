package nu.metacraft.core.preferences;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import nu.metacraft.core.METAcraftCore;

import java.util.List;
import java.util.function.Predicate;

public record Preference<T, V, P extends Predicate<V>>(
		PreferenceType<T, V, P> type, T definition, List<PreferenceType.Icon<V, P>> icons, V defaultValue
) {

	public static final ResourceKey<Registry<Preference<?, ?, ?>>> REGISTRY_KEY = ResourceKey.createRegistryKey(METAcraftCore.getID("preference"));

	private static <T, V, P extends Predicate<V>> MapCodec<Preference<T, V, P>> fixType(PreferenceType<T, V, P> type) {
		return RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						type.getDefinitionCodec().forGetter(Preference::definition),
						ExtraCodecs.nonEmptyList(type.getIconCodec().listOf()).fieldOf("icons").forGetter(Preference::icons),
						type.getValueCodec().fieldOf("default_value").forGetter(Preference::defaultValue)
				).apply(instance, (pref, icon, defaultValue) -> new Preference<>(type, pref, icon, defaultValue))
		);
	}

	private static final Codec<Preference<?, ?, ?>> CODEC = PreferenceType.REGISTRY.byNameCodec().dispatch(
			Preference::type, Preference::fixType
	);

	public static final Codec<Holder<Preference<?, ?, ?>>> REGISTRY_CODEC = RegistryFileCodec.create(REGISTRY_KEY, CODEC, false);

	public static void init() {
		DynamicRegistries.register(REGISTRY_KEY, CODEC);
	}

}
