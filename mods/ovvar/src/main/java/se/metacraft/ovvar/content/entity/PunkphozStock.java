package se.metacraft.ovvar.content.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import se.metacraft.ovvar.PunkphozConfig;
import se.metacraft.ovvar.PunkphozConfig.PoolEntry;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.enchantment.EnchantmentsConfig;
import se.metacraft.ovvar.content.enchantment.SpilledDrink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What Punkphöz has on offer on a visit: the ovvar of chapters nobody else sells (the ones that have waited longest
 * the likeliest), loot patches, and picks from the config's pool (plus the curse book where Brewery is). Pure but for the random source and the history.
 */
public final class PunkphozStock {
	private PunkphozStock() {}

	/** The chapters whose ovvar only he sells, in enum order. */
	public static final Set<Chapter> EXCLUSIVE = Collections.unmodifiableSet(exclusive());

	/** One offer: {@code result} for {@code emeralds}, {@code maxUses} times. */
	public record Stocked(ItemStack result, int emeralds, int maxUses) {}

	private static EnumSet<Chapter> exclusive() {
		EnumSet<Chapter> set = EnumSet.noneOf(Chapter.class);
		set.addAll(Chapter.wandererStock());
		return set;
	}

	/** A visit's stock with no extras: see {@link #roll(PunkphozConfig, List, List, RandomSource, PunkphozHistory)}. */
	public static List<Stocked> roll(PunkphozConfig config, List<PoolEntry> pool, RandomSource random, PunkphozHistory history) {
		return roll(config, pool, List.of(), random, history);
	}

	/**
	 * A visit's stock: chapter ovvar, then patches, then pool picks, each without repeats and as many as there are
	 * when fewer than asked. The picks are drawn from the pool and {@code extras} alike (offers the pool can't name by
	 * registry id alone, see {@link #extras}). Counts the visit in {@code history}; marking it dirty is the caller's.
	 */
	public static List<Stocked> roll(PunkphozConfig config, List<PoolEntry> pool, List<Stocked> extras, RandomSource random, PunkphozHistory history) {
		List<Stocked> stock = new ArrayList<>();
		List<Chapter> chapters = pickChapters(config.chapterOvves(), random, history);
		for (Chapter chapter : chapters) stock.add(new Stocked(new ItemStack(ModContent.ovve(chapter)), config.ovvePrice(), 1));
		for (Patches.Patch patch : pick(PatchLoot.looted(), config.patches(), random)) {
			stock.add(new Stocked(new ItemStack(ModContent.patchItem(patch)), config.patchPrice(), 1));
		}
		List<Stocked> candidates = new ArrayList<>();
		for (PoolEntry entry : pool) {
			Item item = BuiltInRegistries.ITEM.getValue(entry.item());
			if (item != Items.AIR) candidates.add(new Stocked(new ItemStack(item, entry.count()), entry.price(), entry.maxUses()));
		}
		candidates.addAll(extras);
		for (Stocked picked : pick(candidates, config.poolPicks(), random)) stock.add(new Stocked(picked.result().copy(), picked.emeralds(), picked.maxUses()));
		history.recordVisit(chapters);
		return stock;
	}

	/**
	 * The pool candidates that aren't config entries: the Curse of the Spilled Drink's book for
	 * {@link SpilledDrink#WANDERER_PRICE} emeralds, once, but only where the curse is offered (Brewery installed and
	 * the curse switched on, {@link SpilledDrink#wandererBook}); otherwise none, silently.
	 */
	public static List<Stocked> extras(HolderLookup.Provider registries, EnchantmentsConfig enchantments, boolean brewery) {
		return SpilledDrink.wandererBook(registries, enchantments, brewery)
				.map(book -> List.of(new Stocked(book, SpilledDrink.WANDERER_PRICE, 1)))
				.orElse(List.of());
	}

	/**
	 * Up to {@code count} distinct exclusive chapters, each drawn with weight {@code visits - lastOffered}: one for a
	 * chapter on offer last visit, more the longer it has waited (never offered counts as offered on visit -1).
	 */
	private static List<Chapter> pickChapters(int count, RandomSource random, PunkphozHistory history) {
		List<Chapter> left = new ArrayList<>(EXCLUSIVE);
		List<Chapter> picked = new ArrayList<>();
		while (picked.size() < count && !left.isEmpty()) {
			long total = 0;
			long[] weights = new long[left.size()];
			for (int i = 0; i < left.size(); i++) {
				weights[i] = Math.max(1, (long) history.visits() - history.lastOffered(left.get(i)));
				total += weights[i];
			}
			long roll = (long) (random.nextDouble() * total);
			int at = 0;
			while (at < weights.length - 1 && roll >= weights[at]) roll -= weights[at++];
			picked.add(left.remove(at));
		}
		return picked;
	}

	/** Up to {@code count} distinct elements of {@code from}, in random order. */
	private static <T> List<T> pick(List<T> from, int count, RandomSource random) {
		List<T> left = new ArrayList<>(from);
		List<T> picked = new ArrayList<>();
		while (picked.size() < count && !left.isEmpty()) picked.add(left.remove(random.nextInt(left.size())));
		return picked;
	}
}
