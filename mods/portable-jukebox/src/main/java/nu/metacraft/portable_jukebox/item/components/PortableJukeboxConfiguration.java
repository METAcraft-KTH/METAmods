package nu.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PortableJukeboxConfiguration(float volume, float pitch) {

	public static final PortableJukeboxConfiguration DEFAULT = new PortableJukeboxConfiguration(4, 1);

	public static final Codec<PortableJukeboxConfiguration> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf("volume", 4.0f).forGetter(PortableJukeboxConfiguration::volume),
					Codec.floatRange(0.5f, 2).optionalFieldOf("pitch", 1.0f).forGetter(PortableJukeboxConfiguration::pitch)
			).apply(instance, PortableJukeboxConfiguration::new)
	);

	public PortableJukeboxConfiguration withVolume(float volume) {
		return new PortableJukeboxConfiguration(volume, this.pitch);
	}

	public PortableJukeboxConfiguration withPitch(float pitch) {
		return new PortableJukeboxConfiguration(this.volume, pitch);
	}

}
