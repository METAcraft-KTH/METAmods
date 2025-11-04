package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import nu.metacraft.lib.util.METACodecs;

public class PlaySoundAttack extends InstantAttack {

	public static final MapCodec<PlaySoundAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SoundEvent.CODEC.fieldOf("sound").forGetter(a -> a.sound),
					METACodecs.SOUND_CATEGORY_CODEC.fieldOf("category").forGetter(a -> a.category),
					Codec.floatRange(0, Float.MAX_VALUE).fieldOf("volume").forGetter(a -> a.volume),
					Codec.floatRange(0, 2).fieldOf("pitch").forGetter(a -> a.pitch)
			).apply(instance, PlaySoundAttack::new)
	);

	private final Holder<SoundEvent> sound;
	private final SoundSource category;
	private final float volume;
	private final float pitch;

	public PlaySoundAttack(SoundEvent sound, SoundSource category, float volume, float pitch) {
		this(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, volume, pitch);
	}

	public PlaySoundAttack(Holder<SoundEvent> sound, SoundSource category, float volume, float pitch) {
		this.sound = sound;
		this.category = category;
		this.volume = volume;
		this.pitch = pitch;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		if (ctx.boss().isDeadOrDying()) {
			ctx.getWorld().playSeededSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(), sound, category, volume, pitch, ctx.random().nextLong());
		} else {
			ctx.getWorld().playSeededSound(null, ctx.boss(), sound, category, volume, pitch, ctx.random().nextLong());
		}
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.PLAY_SOUND;
	}
}
