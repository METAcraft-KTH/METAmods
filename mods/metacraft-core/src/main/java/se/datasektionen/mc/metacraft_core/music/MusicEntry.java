package se.datasektionen.mc.metacraft_core.music;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record MusicEntry(RegistryEntry<SoundEvent> music, int length, float pitch, int priority, Optional<Credit> credit) {

	private static final Map<RegistryKey<SoundEvent>, RegistryEntry<SoundEvent>> cache = new HashMap<>();
	public static final Codec<RegistryEntry<SoundEvent>> MUSIC_CODEC_WITH_CACHE = Identifier.CODEC.xmap(
			MusicEntry::getFromID, MusicEntry::getID
	);

	public static final MapCodec<MusicEntry> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			MUSIC_CODEC_WITH_CACHE.fieldOf("music").forGetter(MusicEntry::music),
			Codec.INT.fieldOf("length").forGetter(MusicEntry::length),
			Codec.FLOAT.fieldOf("pitch").orElse(1.0f).forGetter(MusicEntry::pitch),
			Codec.INT.fieldOf("priority").orElse(0).forGetter(MusicEntry::priority),
			Credit.CODEC.optionalFieldOf("credit").forGetter(MusicEntry::credit)
		).apply(instance, MusicEntry::new)
	);

	public static final Codec<MusicEntry> CODEC = MAP_CODEC.codec();

	public static RegistryEntry<SoundEvent> getFromID(Identifier id) {
		return getFromID(RegistryKey.of(RegistryKeys.SOUND_EVENT, id));
	}

	public static RegistryEntry<SoundEvent> getFromID(RegistryKey<SoundEvent> key) {
		return Registries.SOUND_EVENT.getEntry(key).map(
				e -> (RegistryEntry<SoundEvent>) e
		).orElseGet(() -> {
			if (!cache.containsKey(key)) {
				var newSound = RegistryEntry.of(SoundEvent.of(key.getValue()));
				cache.put(key, newSound);
			}
			return cache.get(key);
		});
	}

	public static Identifier getID(RegistryEntry<SoundEvent> entry) {
		return entry.getKeyOrValue().map(RegistryKey::getValue, SoundEvent::getId);
	}

	@Override
	public String toString() {
		String firstPart = "MusicEntry[music=" + music.value().getId() + ", length=" +
				length + ", pitch=" + pitch + ", priority=" + priority;
		if (credit.isPresent()) {
			firstPart += ", credit=" + credit.get();
		}
		return firstPart + "]";
	}

	public record Credit(Text name, Text author, Style style, Optional<Style> playingStyle, Optional<Credit> basedOf) {
		private static Codec<Credit> getCodec() {
			return Codec.lazyInitialized(() -> CODEC);
		}
		public static final Codec<Credit> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						TextCodecs.CODEC.fieldOf("name").forGetter(Credit::name),
						TextCodecs.CODEC.fieldOf("author").forGetter(Credit::author),
						Style.Codecs.CODEC.fieldOf("style").orElse(Style.EMPTY.withColor(Formatting.AQUA)).forGetter(Credit::style),
						Style.Codecs.CODEC.optionalFieldOf("style").forGetter(Credit::playingStyle),
						getCodec().optionalFieldOf("based_of").forGetter(Credit::basedOf)
				).apply(instance, Credit::new)
		);

		public Text getCredit() {
			return basedOf.map(
					other -> Text.translatableWithFallback(
							"music.metacraft.recursive_credit", "%s by %s based of %s",
							name, author, other.getCredit()
					).fillStyle(style)
			).orElse(
					Text.translatableWithFallback(
							"music.metacraft.credit", "%s by %s",
							name, author
					).fillStyle(style)
			);
		}

		public Text getPlayingCredit() {
			return Text.translatable(
					"record.nowPlaying", getCredit()
			).fillStyle(playingStyle.orElse(style));
		}

		@Override
		public String toString() {
			return CODEC.encodeStart(JavaOps.INSTANCE, this).getOrThrow().toString();
		}
	}

}
