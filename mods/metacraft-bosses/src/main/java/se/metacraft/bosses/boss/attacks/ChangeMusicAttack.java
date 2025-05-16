package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;

public class ChangeMusicAttack extends InstantAttack {

	public static final MapCodec<ChangeMusicAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					MusicEntry.CODEC.fieldOf("music").forGetter(a -> a.music)
			).apply(instance, ChangeMusicAttack::new)
	);

	private final MusicEntry music;

	public ChangeMusicAttack(MusicEntry music) {
		this.music = music;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		ctx.boss().getBossBar().setMusic(music);
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.CHANGE_MUSIC;
	}
}
