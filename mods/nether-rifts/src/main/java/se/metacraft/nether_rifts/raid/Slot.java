package se.metacraft.nether_rifts.raid;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** A place in a wave, named after the vanilla raider whose numbers it takes; the last few are our own. */
public enum Slot implements StringRepresentable {
	/** Pillager: crossbow piglins. */
	RANGED("ranged"),
	/** Vindicator: spear piglins and hoglins. */
	MELEE("melee"),
	/** Ravager: ghasts, without a rider. */
	HEAVY("heavy"),
	/** Witch: hoglins ridden by a spear piglin or a Piglin Drummer. */
	SUPPORT("support"),
	/** Evoker: piglin brutes. */
	ELITE("elite"),
	/** Vanilla's per-wave raid leader: a Piglin Drummer. The default waves bring one from wave 2. */
	LEADER("leader"),
	/** The wave's Captain, with its own boss bar: the Piglin General. Off by default; a wave that lists it brings him. */
	CAPTAIN("captain"),
	/** Replaces HEAVY in a large raid's waves 5 and later: a Chained Ghast, its deck crewed by a Piglin Drummer, a Piglin Grenadier and two piglins. */
	BOMBARDIER("bombardier"),
	/** The Piglin General's guard, also for waves: a Piglin Grenadier on foot. */
	GRENADIER("grenadier"),
	/** The Piglin General's guard, also for waves: a Piglin Drummer riding a hoglin. */
	MOUNTED_DRUMMER("mounted_drummer");

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
