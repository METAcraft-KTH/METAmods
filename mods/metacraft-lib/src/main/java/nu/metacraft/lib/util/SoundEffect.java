package nu.metacraft.lib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public record SoundEffect(
		Holder<SoundEvent> sound,
		SoundSource category,
		float volume, float pitch
) {

	public static final Codec<SoundEffect> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					SoundEvent.CODEC.fieldOf("sound").forGetter(SoundEffect::sound),
					METACodecs.SOUND_CATEGORY_CODEC.fieldOf("category").forGetter(SoundEffect::category),
					ExtraCodecs.POSITIVE_FLOAT.fieldOf("volume").forGetter(SoundEffect::volume),
					Codec.floatRange(0.5f, 2).fieldOf("pitch").forGetter(SoundEffect::pitch)
			).apply(instance, SoundEffect::new)
	);

	public static final Codec<WeightedList<SoundEffect>> POOL_CODEC = Codec.withAlternative(
			WeightedList.codec(CODEC),
			CODEC, WeightedList::of
	);

	public void playSound(ServerPlayer player, double x, double y, double z, long seed) {
		player.connection.send(new ClientboundSoundPacket(
				sound, category, x, y, z, volume, pitch, seed
		));
	}

	public void playSoundFromEntity(ServerPlayer player, Entity source, long seed) {
		player.connection.send(new ClientboundSoundEntityPacket(
				sound, category, source, volume, pitch, seed
		));
	}

	public void playSound(Level world, double x, double y, double z) {
		world.playSound(null, x, y, z, sound.value(), category, volume, pitch);
	}

	public void playSound(Level world, BlockPos pos) {
		world.playSound(null, pos, sound.value(), category, volume, pitch);
	}

	public void playSoundFromEntity(Level world, Entity source) {
		world.playSound(null, source, sound.value(), category, volume, pitch);
	}

}
