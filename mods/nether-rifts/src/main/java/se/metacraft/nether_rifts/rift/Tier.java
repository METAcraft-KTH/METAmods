package se.metacraft.nether_rifts.rift;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import se.metacraft.nether_rifts.NetherRiftsConfig;

/** How big a raid a rift makes, from how many blocks it has: vanilla's easy, normal and hard raids. */
public enum Tier implements StringRepresentable {
	SMALL("small"),
	MEDIUM("medium"),
	LARGE("large");

	public static final Codec<Tier> CODEC = StringRepresentable.fromEnum(Tier::values);

	private final String name;

	Tier(String name) {
		this.name = name;
	}

	public static Tier of(int blocks, NetherRiftsConfig.Tiers tiers) {
		if (blocks <= tiers.smallMax()) {
			return SMALL;
		}
		return blocks <= tiers.mediumMax() ? MEDIUM : LARGE;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
