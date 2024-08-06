package se.datasektionen.mc.metacraft_lib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record SoundEffect(
		RegistryEntry<SoundEvent> sound,
		SoundCategory category,
		float volume, float pitch
) {

	public static final Codec<SoundEffect> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					SoundEvent.ENTRY_CODEC.fieldOf("sound").forGetter(SoundEffect::sound),
					ExtraCodecs.SOUND_CATEGORY_CODEC.fieldOf("category").forGetter(SoundEffect::category),
					Codecs.POSITIVE_FLOAT.fieldOf("volume").forGetter(SoundEffect::volume),
					Codec.floatRange(0.5f, 2).fieldOf("pitch").forGetter(SoundEffect::pitch)
			).apply(instance, SoundEffect::new)
	);

	public static final Codec<DataPool<SoundEffect>> POOL_CODEC = DataPool.createCodec(CODEC);

	public void playSound(ServerPlayerEntity player, double x, double y, double z, long seed) {
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(
				sound, category, x, y, z, volume, pitch, seed
		));
	}

	public void playSoundFromEntity(ServerPlayerEntity player, Entity source, long seed) {
		player.networkHandler.sendPacket(new PlaySoundFromEntityS2CPacket(
				sound, category, source, volume, pitch, seed
		));
	}

	public void playSound(World world, double x, double y, double z) {
		world.playSound(null, x, y, z, sound.value(), category, volume, pitch);
	}

	public void playSound(World world, BlockPos pos) {
		world.playSound(null, pos, sound.value(), category, volume, pitch);
	}

	public void playSoundFromEntity(World world, Entity source) {
		world.playSoundFromEntity(null, source, sound.value(), category, volume, pitch);
	}

}
