package nu.metacraft.nether_rifts.rift;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import nu.metacraft.nether_rifts.NetherRiftsConfig;

/** How big a raid a rift makes, from how many blocks it has: vanilla's easy, normal and hard raids. */
public enum Tier implements StringRepresentable {
	SMALL("small", 3),
	MEDIUM("medium", 5),
	LARGE("large", 7);

	public static final Codec<Tier> CODEC = StringRepresentable.fromEnum(Tier::values);

	private final String name;
	private final int waves;

	Tier(String name, int waves) {
		this.name = name;
		this.waves = waves;
	}

	public static Tier of(int blocks, NetherRiftsConfig.Tiers tiers) {
		if (blocks <= tiers.smallMax()) {
			return SMALL;
		}
		return blocks <= tiers.mediumMax() ? MEDIUM : LARGE;
	}

	public int waves() {
		return waves;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
