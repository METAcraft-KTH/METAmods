package nu.metacraft.nether_rifts.raid;

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

	public static final Codec<Map<Slot, MobEntry>> CODEC = Codec.unboundedMap(Slot.CODEC, MobEntry.CODEC);

	public static final Map<Slot, MobEntry> DEFAULT = Map.of(
			Slot.RANGED, one(CROSSBOW_PIGLIN),
			Slot.MELEE, weighted(Map.of(
					"{id:\"minecraft:piglin\",equipment:{mainhand:{id:\"minecraft:golden_spear\",count:1}}}", 4,
					"{id:\"minecraft:hoglin\"}", 1,
					"{id:\"minecraft:hoglin\",Passengers:[{id:\"minecraft:piglin\",IsBaby:1b}]}", 1
			)),
			Slot.HEAVY, one("{id:\"minecraft:ghast\",Passengers:[" + CROSSBOW_PIGLIN + "]}"),
			Slot.SUPPORT, one("{id:\"minecraft:strider\",Passengers:[" + CROSSBOW_PIGLIN + "]}"),
			Slot.ELITE, one("{id:\"minecraft:piglin_brute\",equipment:{mainhand:{id:\"minecraft:golden_axe\",count:1}}}"),
			Slot.LEADER, one("{id:\"nether_rifts:war_drummer\"}"),
			Slot.CAPTAIN, one("{id:\"minecraft:piglin_brute\",CustomName:\"Piglin General\",Health:100f,"
					+ "attributes:[{id:\"minecraft:max_health\",base:100d}],"
					+ "equipment:{head:{id:\"minecraft:red_banner\",count:1},mainhand:{id:\"minecraft:golden_axe\",count:1}}}"),
			Slot.BOMBARDIER, one("{id:\"nether_rifts:war_ghast\"}")
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
