package se.metacraft.traders;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** The three traders: each has its own model, and its own list of trades in the config under its {@link #id()}. */
public enum TraderKind implements StringRepresentable {
	DATA,
	IT,
	MEDIA;

	/** By {@link #id()}: a config key naming no trader is an error. */
	public static final Codec<TraderKind> CODEC = StringRepresentable.fromEnum(TraderKind::values);

	/** {@code data}, {@code it}, {@code media}: the key in the config. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** {@code data_trader}: the entity's registry path, and the model's file name. */
	public String entityName() {
		return id() + "_trader";
	}

	@Override
	public String getSerializedName() {
		return id();
	}
}
