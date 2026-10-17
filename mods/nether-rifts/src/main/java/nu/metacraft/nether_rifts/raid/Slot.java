package nu.metacraft.nether_rifts.raid;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** A place in a wave, named after the vanilla raider whose numbers it takes. */
public enum Slot implements StringRepresentable {
	/** Pillager: crossbow piglins. */
	RANGED("ranged"),
	/** Vindicator: spear piglins and hoglins. */
	MELEE("melee"),
	/** Ravager: ghasts with a rider. */
	HEAVY("heavy"),
	/** Witch: striders with a rider. */
	SUPPORT("support"),
	/** Evoker: piglin brutes. */
	ELITE("elite"),
	/** Vanilla's per-wave raid leader: one from wave 2. */
	LEADER("leader"),
	/** The Rift Captain: the last wave of a large raid. */
	CAPTAIN("captain"),
	/** Replaces HEAVY in a large raid's waves 5 and later: a Chained Ghast, its deck crewed by a Piglin Drummer, a Piglin Grenadier and two piglins. */
	BOMBARDIER("bombardier");

	public static final Codec<Slot> CODEC = StringRepresentable.fromEnum(Slot::values);

	private final String name;

	Slot(String name) {
		this.name = name;
	}

	/** Troopers grow with the number of players taking part; nothing else does. */
	public boolean trooper() {
		return this == RANGED || this == MELEE;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
