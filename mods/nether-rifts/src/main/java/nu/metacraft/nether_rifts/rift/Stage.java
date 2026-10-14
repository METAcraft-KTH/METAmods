package nu.metacraft.nether_rifts.rift;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum Stage implements StringRepresentable {
	/** Lit once: flickers, blocks travel, does nothing else. */
	LIT("lit", 0xFD),
	/** Lit twice: stronger; falls back to LIT if left alone. */
	UNSTABLE("unstable", 0xFC),
	/** Lit three times: the countdown before wave 1. */
	TORN("torn", 0xFB),
	/** Waves are coming through. */
	RAID("raid", 0xFB);

	public static final Codec<Stage> CODEC = StringRepresentable.fromEnum(Stage::values);

	private final String name;
	private final int marker;

	Stage(String name, int marker) {
		this.name = name;
		this.marker = marker;
	}

	/** The green channel of the shader marker: FD, FC, FB for the shader's stages 1, 2, 3. */
	public int marker() {
		return marker;
	}

	public boolean raiding() {
		return this == TORN || this == RAID;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
