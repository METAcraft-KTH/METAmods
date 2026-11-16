package se.metacraft.ovvar.content;

import com.google.common.collect.Sets;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import se.metacraft.ovvar.content.enchantment.Magpie;

import java.util.List;
import java.util.Set;

/**
 * Where a patch comes from. Every patch in the catalogue is in exactly one of {@link #LOOT} and
 * {@link #NON_LOOT}, checked at startup, so a new patch can't end up in chests by being forgotten:
 * it has to be put in one of them. Loot may turn up, one at a time, in a vanilla structure's chest
 * or a Structory one; the rest are crafted ({@code data/ovvar/recipe}) or only ever given.
 *
 * <p>Loot is also what the dye colour names: a droppable patch can turn up on anyone, so it must
 * show the moment it is sewn, without a pack reload. {@link Patches#byCode} gives {@link #LOOT}
 * the first design codes, and {@link #check} fails startup and datagen if it ever outgrows the
 * {@value Looks#INSTANT_DESIGNS} the dye colour has room for.
 *
 * <p>Added to the tables as they load rather than shipped as copies of them, so a datapack or
 * another mod that changes a chest's loot keeps its change, and Structory's tables only get a pool
 * when Structory is there to load them.
 */
public final class PatchLoot {
	private PatchLoot() {}

	// Moving a patch out of LOOT later makes it vanish from persistent mobs garments: they never request combos.
	/** The patches structure chests hold, and the instant ones (see the class comment). */
	public static final Set<String> LOOT = Set.of(
			"spiken", "slaggan", "ticket_to_my_heart", "maid", "pung",
			"in_gold", "nyckeln0x0", "nyckeln0x1", "nyckeln0x2",
			"jgs", "tmeit",
			"batata", "batato", "batatu", "cerise", "playboy-pikachu", "starkt",
			"data_neko", "kth", "media_neko", "metacraft");

	/** The patches no chest holds. */
	public static final Set<String> NON_LOOT = Set.of(
			// crafted: data/ovvar/recipe/patch_<id>.json
			"it", "data", "itk", "in", "medieteknik",
			// given: the Rivals event's own, TMEIT's marshals' and the staff's, and KomMN's
			"rivals", "tmeit-marshal", "metacrew", "kommn");

	/** How likely a chest is to hold a patch; which one is even odds among {@link #looted}. */
	public static final float CHANCE = 0.05f;

	/** The bonus chest by the spawn is not a structure's. */
	private static final Identifier BONUS_CHEST = Identifier.withDefaultNamespace("chests/spawn_bonus_chest");

	public static void init() {
		check();
		List<Patches.Patch> looted = looted();
		if (looted.isEmpty()) return;
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (!inStructureChest(key.identifier())) return;
			LootPool.Builder pool = LootPool.lootPool().when(Magpie.chestCondition(registries));
			for (Patches.Patch patch : looted) pool.add(LootItem.lootTableItem(ModContent.patchItem(patch)));
			table.withPool(pool);
		});
	}

	/**
	 * Every patch in exactly one of the two sets, no id in them that isn't a patch, and room in the
	 * dye colour for every loot patch. A startup and datagen check, not a gameplay one.
	 */
	public static void check() {
		for (Patches.Patch patch : Patches.all()) {
			boolean loot = LOOT.contains(patch.id()), nonLoot = NON_LOOT.contains(patch.id());
			if (loot == nonLoot) {
				throw new IllegalStateException("[ovvar] patch '" + patch.id() + "' is in " + (loot ? "both PatchLoot.LOOT and" : "neither PatchLoot.LOOT nor")
						+ " PatchLoot.NON_LOOT: put it in exactly one");
			}
		}
		for (String id : Sets.union(LOOT, NON_LOOT)) {
			if (!Patches.exists(id)) throw new IllegalStateException("[ovvar] PatchLoot names '" + id + "', which is not a patch");
		}
		checkInstantRoom(LOOT.size());
	}

	/** {@code lootCount} loot patches must all fit among the dye colour's instant designs. */
	public static void checkInstantRoom(int lootCount) {
		if (lootCount > Looks.INSTANT_DESIGNS) {
			throw new IllegalStateException("[ovvar] PatchLoot.LOOT has " + lootCount + " patches but the dye colour has room for "
					+ Looks.INSTANT_DESIGNS + " instant designs: every droppable patch must show at once. Free a slot (fewer top cells) or move a patch to NON_LOOT.");
		}
	}

	/** The patches in {@link #LOOT}, in catalogue order. */
	public static List<Patches.Patch> looted() {
		return Patches.all().stream().filter(patch -> LOOT.contains(patch.id())).toList();
	}

	/** Vanilla's structure chests, and all of Structory's tables (every one of them fills a chest or a barrel). */
	public static boolean inStructureChest(Identifier table) {
		if (table.getNamespace().equals("structory")) return true;
		return table.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
				&& table.getPath().startsWith("chests/") && !table.equals(BONUS_CHEST);
	}
}
