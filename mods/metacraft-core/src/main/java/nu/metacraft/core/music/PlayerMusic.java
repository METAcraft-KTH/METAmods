package nu.metacraft.core.music;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.random.WeightedList;
import nu.metacraft.core.mixin.AccessorPool;
import nu.metacraft.lib.util.METACodecs;
import org.jetbrains.annotations.NotNull;

public record PlayerMusic(WeightedList<MusicEntry> music, int priority) implements Comparable<PlayerMusic> {

	private static final Codec<WeightedList<MusicEntry>> POOL_CODEC = WeightedList.nonEmptyCodec(MusicEntry.EASY_CODEC);

	private static final MapCodec<PlayerMusic> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					POOL_CODEC.fieldOf("music_pool").forGetter(PlayerMusic::music),
					Codec.INT.optionalFieldOf("priority", 0).forGetter(PlayerMusic::priority)
			).apply(instance, PlayerMusic::new)
	);

	private static final MapCodec<PlayerMusic> SINGLE_MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					MusicEntry.EASY_MAP_CODEC.xmap(WeightedList::of, pool -> pool.unwrap().getFirst().value()).forGetter(PlayerMusic::music),
					Codec.INT.optionalFieldOf("priority", 0).forGetter(PlayerMusic::priority)
			).apply(instance, PlayerMusic::new)
	);

	private static final Codec<PlayerMusic> CODEC = MAP_CODEC.codec();

	private static final Codec<PlayerMusic> SINGLE_CODEC = SINGLE_MAP_CODEC.codec();

	private static final Codec<PlayerMusic> DISC_CODEC = MusicEntry.DISC_CODEC.xmap(
			entry -> new PlayerMusic(WeightedList.<MusicEntry>builder().add(entry).build(), 0),
			music -> music.music.unwrap().getFirst().value()
	);

	public static final Codec<PlayerMusic> EASY_CODEC = Codec.withAlternative(
			CODEC, Codec.withAlternative(
					SINGLE_CODEC, Codec.withAlternative(
							DISC_CODEC, DISC_CODEC.fieldOf("song").codec()
					)
			)
	);

	public static final MapCodec<PlayerMusic> EASY_MAP_CODEC = METACodecs.withAlternative(
			MAP_CODEC, METACodecs.withAlternative(
					SINGLE_MAP_CODEC, DISC_CODEC.fieldOf("song")
			)
	);

	@Override
	public int compareTo(@NotNull PlayerMusic musicEntry) {
		return -Integer.compare(this.priority, musicEntry.priority);
	}

	@Override
	public @NotNull String toString() {
		String musicString = music.unwrap().getFirst().toString();
		if (music.unwrap().size() > 1) {
			StringBuilder b = new StringBuilder();
			b.append("[");
			b.append(musicString);
			for (int i = 1; i < music.unwrap().size(); i++) {
				b.append(", ");
				b.append(music.unwrap().get(i));
			}
			b.append("]");
			return b.toString();
		}
		return "PlayerMusic[music=" + musicString + ", priority=" + priority + "]";
	}

	// Necessary for compatibility with Lithium:
	// https://github.com/CaffeineMC/lithium/blob/28f9fe57f5bebbcb1889911130dee27aae03d4c2/common/src/main/java/net/caffeinemc/mods/lithium/mixin/collections/mob_spawning/WeightedListMixin.java#L4
	@Override
	public boolean equals(Object other) {
		if (other instanceof PlayerMusic(WeightedList<MusicEntry> otherMusic, int otherPriority)) {
			if (this.priority != otherPriority) return false;
			//noinspection DataFlowIssue
			if (
					((AccessorPool) (Object) this.music).getTotalWeight() !=
					((AccessorPool) (Object) otherMusic).getTotalWeight()
			) return false;
			if (this.music.unwrap().size() != otherMusic.unwrap().size()) return false;
			for (int i = 0; i < this.music.unwrap().size(); i++) {
				if (!this.music.unwrap().get(i).equals(otherMusic.unwrap().get(i))) return false;
			}

			return true;
		}
		return false;
	}

}
