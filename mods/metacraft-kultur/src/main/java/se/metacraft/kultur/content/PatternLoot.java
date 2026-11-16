package se.metacraft.kultur.content;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import se.metacraft.kultur.catalogue.Catalogue;

import java.util.ArrayList;
import java.util.List;

/**
 * A pattern item in a structure's chest, now and then. Every pattern the catalogue flags
 * {@code loot} (and gives an item) is one of the candidates, at even odds. None is flagged for now
 * (the gated patterns come only from Punkphöz), and with none flagged no table is touched.
 * Added to the tables as they load, so a datapack that changes a chest keeps its change.
 */
public final class PatternLoot {
	private PatternLoot() {}

	/** How likely a structure chest is to hold a pattern item; which one is even odds among the loot-flagged. */
	public static final float CHANCE = 0.02f;

	/** The bonus chest by the spawn is not a structure's. */
	private static final Identifier BONUS_CHEST = Identifier.withDefaultNamespace("chests/spawn_bonus_chest");

	public static void init(Catalogue catalogue) {
		List<Item> looted = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : catalogue.patterns()) {
			Catalogue.Pattern p = owned.value();
			if (!p.loot()) continue;
			PatternItem item = ModContent.patternItem(p.id());
			if (item != null) looted.add(item);
		}
		if (looted.isEmpty()) return;
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (!inStructureChest(key.identifier())) return;
			LootPool.Builder pool = LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(CHANCE));
			for (Item item : looted) pool.add(LootItem.lootTableItem(item));
			table.withPool(pool);
		});
	}

	/** Vanilla's structure chests, and all of Structory's tables (every one of them fills a chest or a barrel). */
	public static boolean inStructureChest(Identifier table) {
		if (table.getNamespace().equals("structory")) return true;
		return table.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
				&& table.getPath().startsWith("chests/") && !table.equals(BONUS_CHEST);
	}
}
