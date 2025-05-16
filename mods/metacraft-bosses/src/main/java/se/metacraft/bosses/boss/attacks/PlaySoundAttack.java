package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

public class PlaySoundAttack extends InstantAttack {

	public static final MapCodec<PlaySoundAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SoundEvent.ENTRY_CODEC.fieldOf("sound").forGetter(a -> a.sound),
					ExtraCodecs.SOUND_CATEGORY_CODEC.fieldOf("category").forGetter(a -> a.category),
					Codec.floatRange(0, Float.MAX_VALUE).fieldOf("volume").forGetter(a -> a.volume),
					Codec.floatRange(0, 2).fieldOf("pitch").forGetter(a -> a.pitch)
			).apply(instance, PlaySoundAttack::new)
	);

	private final RegistryEntry<SoundEvent> sound;
	private final SoundCategory category;
	private final float volume;
	private final float pitch;

	public PlaySoundAttack(SoundEvent sound, SoundCategory category, float volume, float pitch) {
		this(Registries.SOUND_EVENT.getEntry(sound), category, volume, pitch);
	}

	public PlaySoundAttack(RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch) {
		this.sound = sound;
		this.category = category;
		this.volume = volume;
		this.pitch = pitch;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		if (ctx.boss().isDead()) {
			ctx.getWorld().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(), sound, category, volume, pitch, ctx.random().nextLong());
		} else {
			ctx.getWorld().playSoundFromEntity(null, ctx.boss(), sound, category, volume, pitch, ctx.random().nextLong());
		}
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.PLAY_SOUND;
	}
}
