package nu.metacraft.core.music;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.component.type.JukeboxPlayableComponent;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import nu.metacraft.lib.util.ExtraCodecs;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record MusicEntry(
		Music music, Optional<Music> intro, Optional<Credit> credit
) {

	protected static final Codec<MusicEntry> DISC_CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<MusicEntry, T>> decode(DynamicOps<T> ops, T input) {
			return JukeboxPlayableComponent.CODEC.decode(ops, input).flatMap(
					song -> {
						DataResult<? extends RegistryEntry<JukeboxSong>> entry = song.getFirst().song().contents().map(
								DataResult::success,
								k -> {
									if (ops instanceof RegistryOps<T> registryOps) {
										var l = registryOps.getEntryLookup(RegistryKeys.JUKEBOX_SONG);
										if (l.isEmpty()) return DataResult.error(() -> "Cannot find jukebox song registry!");
										var lookup = l.get();
										return lookup.getOptional(k).map(
												DataResult::success
										).orElse(DataResult.error(() -> "Jukebox song with id " + k.getValue() + " did not exist"));
									}
									return DataResult.error(() -> "Parsing this value requires RegistryOps.");
								}
						);
						if (entry.error().isPresent()) return DataResult.error(entry.error().get().messageSupplier());
						var actualEntry = entry.getOrThrow().value();
						return DataResult.success(
								Pair.of(
										new MusicEntry(
												new MusicEntry.Music(actualEntry.soundEvent(), actualEntry.lengthInSeconds(), 1, false),
												Optional.empty(), Optional.of(new MusicEntry.Credit(actualEntry.description(), 1))
										),
										song.getSecond()
								)
						);
					}
			);
		}

		@Override
		public <T> DataResult<T> encode(MusicEntry input, DynamicOps<T> ops, T prefix) {
			return CODEC.encode(input, ops, prefix);
		}
	};

	private static final Map<RegistryKey<SoundEvent>, RegistryEntry<SoundEvent>> cache = new HashMap<>();
	public static final Codec<RegistryEntry<SoundEvent>> MUSIC_CODEC_WITH_CACHE = Identifier.CODEC.xmap(
			MusicEntry::getFromID, MusicEntry::getID
	);

	private static final MapCodec<MusicEntry> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Music.MAP_CODEC.forGetter(MusicEntry::music),
			Music.CODEC.optionalFieldOf("intro").forGetter(MusicEntry::intro),
			Credit.SIMPLE_CODEC.optionalFieldOf("credit").forGetter(MusicEntry::credit)
		).apply(instance, MusicEntry::new)
	);

	private static final Codec<MusicEntry> CODEC = MAP_CODEC.codec();

	public static final Codec<MusicEntry> EASY_CODEC = Codec.withAlternative(CODEC, DISC_CODEC);

	public static final MapCodec<MusicEntry> EASY_MAP_CODEC = ExtraCodecs.withAlternative(MAP_CODEC, DISC_CODEC.fieldOf("song"));

	public static RegistryEntry<SoundEvent> getFromID(Identifier id) {
		return getFromID(RegistryKey.of(RegistryKeys.SOUND_EVENT, id));
	}

	public static RegistryEntry<SoundEvent> getFromID(RegistryKey<SoundEvent> key) {
		return Registries.SOUND_EVENT.getOptional(key).map(
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
		return entry.getKeyOrValue().map(RegistryKey::getValue, SoundEvent::id);
	}

	public Music getMusic(boolean intro) {
		if (intro && this.intro.isPresent()) {
			return this.intro.get();
		} else {
			return music;
		}
	}

	@Override
	public @NotNull String toString() {
		String firstPart = "MusicEntry[music=" + music;
		if (intro.isPresent()) {
			firstPart += ", intro=" + intro.get();
		}
		if (credit.isPresent()) {
			firstPart += ", credit=" + credit.get();
		}
		return firstPart + "]";
	}

	public record Credit(Text text, int displayTime) {
		public static final Codec<Credit> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						TextCodecs.CODEC.fieldOf("text").forGetter(Credit::text),
						Codecs.POSITIVE_INT.optionalFieldOf("display_time", 1).forGetter(Credit::displayTime)
				).apply(instance, Credit::new)
		);

		public static final Codec<Credit> SIMPLE_CODEC = Codec.withAlternative(
				CODEC, TextCodecs.CODEC.xmap(text -> new Credit(text, 1), credit -> credit.text)
		);

		@Override
		public @NotNull String toString() {
			return CODEC.encodeStart(JavaOps.INSTANCE, this).getOrThrow().toString();
		}
	}

	public record Music(
			RegistryEntry<SoundEvent> music, double length, float pitch, boolean forceStop
	) {
		public static final MapCodec<Music> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						MUSIC_CODEC_WITH_CACHE.fieldOf("music").forGetter(Music::music),
						Codec.DOUBLE.fieldOf("length").forGetter(Music::length),
						Codec.FLOAT.fieldOf("pitch").orElse(1.0f).forGetter(Music::pitch),
						Codec.BOOL.optionalFieldOf("force_stop", false).forGetter(Music::forceStop)
				).apply(instance, Music::new)
		);

		public static final Codec<Music> CODEC = MAP_CODEC.codec();

		@Override
		public @NotNull String toString() {
			return  "Music[music=" + music.value().id() + ", length=" +
					length + ", pitch=" + pitch + "]";
		}
	}

}
