package nu.metacraft.qol.void_anchor.rift;

import net.minecraft.util.StringRepresentable;

/** How a rift looks. */
public enum RiftStyle implements StringRepresentable {

	/** One crack in space, lying flat under the player, the End's void showing through it. */
	CRACK("crack"),
	/** Space shattering like glass: that crack, crossed by glowing cracks at every angle through a bright core that pulls in light. */
	SHATTER("shatter");

	public static final StringRepresentable.EnumCodec<RiftStyle> CODEC = StringRepresentable.fromEnum(RiftStyle::values);

	private final String name;

	RiftStyle(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

}
