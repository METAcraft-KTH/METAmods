package se.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

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

	/** Pitch steps for the menu: 0.5x to 2.0x in steps of 0.1. Pitch is also the playback speed. */
	private static final float[] PITCH_LEVELS = {
		0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 1.9f, 2.0f
	};

	/**
	 * Volume steps for the menu. Up to 1.0 a step makes the song louder; above 1.0 it only carries
	 * further (16 blocks per 1.0 of volume), which is how Minecraft treats sound volume.
	 */
	private static final float[] VOLUME_LEVELS = {
		0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 2.0f, 3.0f, 4.0f
	};

	private static final float EPSILON = 0.001f;

	public PortableJukeboxConfiguration pitchUp() {
		return withPitch(next(PITCH_LEVELS, pitch));
	}

	public PortableJukeboxConfiguration pitchDown() {
		return withPitch(previous(PITCH_LEVELS, pitch));
	}

	public PortableJukeboxConfiguration volumeUp() {
		return withVolume(next(VOLUME_LEVELS, volume));
	}

	public PortableJukeboxConfiguration volumeDown() {
		return withVolume(previous(VOLUME_LEVELS, volume));
	}

	/** The first step above {@code current}, or the top step. */
	private static float next(float[] levels, float current) {
		for (float level : levels) {
			if (level > current + EPSILON) return level;
		}
		return levels[levels.length - 1];
	}

	/** The last step below {@code current}, or the bottom step. */
	private static float previous(float[] levels, float current) {
		for (int i = levels.length - 1; i >= 0; i--) {
			if (levels[i] < current - EPSILON) return levels[i];
		}
		return levels[0];
	}

	/** Pitch as a speed, e.g. "1.2x". */
	public static String formatPitch(float pitch) {
		return String.format(Locale.ROOT, "%.2fx", pitch).replaceFirst("0x$", "x");
	}

	/** Loudness as a percentage, capped at 100% (more volume only adds range). */
	public static String formatVolume(float volume) {
		return Math.round(Math.min(volume, 1.0f) * 100) + "%";
	}

	/** Above 1.0 the volume is past full loudness: it only makes the song carry further. */
	public static boolean isRangeBoosted(float volume) {
		return volume > 1.0f + EPSILON;
	}

	/** How many times the normal range the song carries, e.g. "3x". */
	public static String formatRangeBoost(float volume) {
		return String.format(Locale.ROOT, "%.1fx", Math.max(volume, 1.0f)).replace(".0x", "x");
	}

	/** How far away, in blocks, the song can be heard. */
	public static int hearingRange(float volume) {
		return Math.round(16 * Math.max(volume, 1.0f));
	}

}
