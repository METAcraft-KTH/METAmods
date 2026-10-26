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

	/** Troopers grow with every player taking part past the first ({@code trooper_scaling_*}). */
	public boolean trooper() {
		return this == RANGED || this == MELEE;
	}

	/**
	 * Heavy, support and elite mobs grow only with more players than {@code elite_scaling_from}, so a small group's raid
	 * stays as it was. The Chained Ghasts that take heavy's place in large waves 5+ grow with it; the leader, the Captain
	 * and the grenadier and mounted drummer slots never do.
	 */
	public boolean scalesPastThreshold() {
		return this == HEAVY || this == SUPPORT || this == ELITE;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
