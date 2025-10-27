package nu.metacraft.core.music;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.collection.Pool;
import nu.metacraft.lib.util.ExtraCodecs;
import org.jetbrains.annotations.NotNull;

public record PlayerMusic(Pool<MusicEntry> music, int priority) implements Comparable<PlayerMusic> {

	private static final Codec<Pool<MusicEntry>> POOL_CODEC = Pool.createNonEmptyCodec(MusicEntry.EASY_CODEC);

	private static final MapCodec<PlayerMusic> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					POOL_CODEC.fieldOf("music_pool").forGetter(PlayerMusic::music),
					Codec.INT.optionalFieldOf("priority", 0).forGetter(PlayerMusic::priority)
			).apply(instance, PlayerMusic::new)
	);

	private static final MapCodec<PlayerMusic> SINGLE_MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					MusicEntry.EASY_MAP_CODEC.xmap(Pool::of, pool -> pool.getEntries().getFirst().value()).forGetter(PlayerMusic::music),
					Codec.INT.optionalFieldOf("priority", 0).forGetter(PlayerMusic::priority)
			).apply(instance, PlayerMusic::new)
	);

	private static final Codec<PlayerMusic> CODEC = MAP_CODEC.codec();

	private static final Codec<PlayerMusic> SINGLE_CODEC = SINGLE_MAP_CODEC.codec();

	private static final Codec<PlayerMusic> DISC_CODEC = MusicEntry.DISC_CODEC.xmap(
			entry -> new PlayerMusic(Pool.<MusicEntry>builder().add(entry).build(), 0),
			music -> music.music.getEntries().getFirst().value()
	);

	public static final Codec<PlayerMusic> EASY_CODEC = Codec.withAlternative(
			CODEC, Codec.withAlternative(
					SINGLE_CODEC, Codec.withAlternative(
							DISC_CODEC, DISC_CODEC.fieldOf("song").codec()
					)
			)
	);

	public static final MapCodec<PlayerMusic> EASY_MAP_CODEC = ExtraCodecs.withAlternative(
			MAP_CODEC, ExtraCodecs.withAlternative(
					SINGLE_MAP_CODEC, DISC_CODEC.fieldOf("song")
			)
	);

	@Override
	public int compareTo(@NotNull PlayerMusic musicEntry) {
		return -Integer.compare(this.priority, musicEntry.priority);
	}

	@Override
	public @NotNull String toString() {
		String musicString = music.getEntries().getFirst().toString();
		if (music.getEntries().size() > 1) {
			StringBuilder b = new StringBuilder();
			b.append("[");
			b.append(musicString);
			for (int i = 1; i < music.getEntries().size(); i++) {
				b.append(", ");
				b.append(music.getEntries().get(i));
			}
			b.append("]");
			return b.toString();
		}
		return "PlayerMusic[music=" + musicString + ", priority=" + priority + "]";
	}

}
