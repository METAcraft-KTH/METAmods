package se.metacraft.nether_rifts.raid;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import se.metacraft.portalopening.raid.MobEntry;

import java.util.Map;
import java.util.Optional;

/**
 * What each slot spawns, as portal-opening's {@link MobEntry}: one weighted pick of entity NBT per mob.
 * Only {@link MobEntry#mobs()} is read; its amount fields are unused because the wave table decides how many.
 */
public final class Roster {

	private static final String CROSSBOW_PIGLIN = "{id:\"minecraft:piglin\",equipment:{mainhand:{id:\"minecraft:crossbow\",count:1}}}";
	private static final String SPEAR_PIGLIN = "{id:\"minecraft:piglin\",equipment:{mainhand:{id:\"minecraft:golden_spear\",count:1}}}";
	/** A drummer drumming from a hoglin's back (the hoglin steers). */
	private static final String DRUMMER_ON_HOGLIN = "{id:\"minecraft:hoglin\",Passengers:[{id:\"nether_rifts:war_drummer\"}]}";

	public static final Codec<Map<Slot, MobEntry>> CODEC = Codec.unboundedMap(Slot.CODEC, MobEntry.CODEC);

	public static final Map<Slot, MobEntry> DEFAULT = Map.of(
			Slot.RANGED, one(CROSSBOW_PIGLIN),
			Slot.MELEE, weighted(Map.of(
					SPEAR_PIGLIN, 4,
					"{id:\"minecraft:hoglin\"}", 1,
					"{id:\"minecraft:hoglin\",Passengers:[{id:\"minecraft:piglin\",IsBaby:1b}]}", 1
			)),
			// IgnoreYCheck (metacraft-mob-modifiers): it fires at players well above or below it, not only level with it
			Slot.HEAVY, one("{id:\"minecraft:ghast\",IgnoreYCheck:1b}"),
			// a spear piglin steers its hoglin; a drummer only drums, and the hoglin goes its own way
			Slot.SUPPORT, weighted(Map.of(
					"{id:\"minecraft:hoglin\",Passengers:[" + SPEAR_PIGLIN + "]}", 2,
					DRUMMER_ON_HOGLIN, 1
			)),
			Slot.ELITE, one("{id:\"minecraft:piglin_brute\",equipment:{mainhand:{id:\"minecraft:golden_axe\",count:1}}}"),
			Slot.LEADER, one("{id:\"nether_rifts:war_drummer\"}"),
			// his stats come from the config's mobs.general
			Slot.CAPTAIN, one("{id:\"nether_rifts:piglin_general\",BossBar:{color:red,music:{music:\"minecraft:music_disc.pigstep\",length:137,credit:\"Pigstep - Lena Raine\"}}}"),
			Slot.BOMBARDIER, one("{id:\"nether_rifts:war_ghast\"}"),
			// the General's guard: the Grenadier on foot, and a drummer on a hoglin
			Slot.GRENADIER, one("{id:\"nether_rifts:ghast_bombardier\"}"),
			Slot.MOUNTED_DRUMMER, one(DRUMMER_ON_HOGLIN)
	);

	private Roster() {}

	private static MobEntry one(String snbt) {
		return entry(WeightedList.of(MobEntry.EntityEntry.fromData(nbt(snbt))));
	}

	private static MobEntry weighted(Map<String, Integer> snbtToWeight) {
		var builder = new WeightedList.Builder<MobEntry.EntityEntry>();
		snbtToWeight.forEach((snbt, weight) -> builder.add(MobEntry.EntityEntry.fromData(nbt(snbt)), weight));
		return entry(builder.build());
	}

	private static MobEntry entry(WeightedList<MobEntry.EntityEntry> mobs) {
		return new MobEntry(mobs, ConstantInt.of(1), 1.0, Optional.empty());
	}

	private static CompoundTag nbt(String snbt) {
		try {
			return TagParser.parseCompoundFully(snbt);
		} catch (CommandSyntaxException e) {
			throw new IllegalStateException("Bad default roster entry: " + snbt, e);
		}
	}
}
