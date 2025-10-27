package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.music.PlayerMusic;

public class ChangeMusicAttack extends InstantAttack {

	public static final MapCodec<ChangeMusicAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PlayerMusic.EASY_CODEC.fieldOf("music").forGetter(a -> a.music)
			).apply(instance, ChangeMusicAttack::new)
	);

	private final PlayerMusic music;

	public ChangeMusicAttack(PlayerMusic music) {
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
