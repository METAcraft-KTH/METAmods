package se.metacraft.ovvar.content.entity;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What Punkphöz has on offer on a visit: the ovvar of chapters nobody else sells (the ones that have waited longest
 * the likeliest), loot patches, and picks from the config's pool. Pure but for the random source and the history.
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

	/**
	 * A visit's stock: chapter ovvar, then patches, then pool picks, each without repeats and as many as there are
	 * when fewer than asked. Counts the visit in {@code history}; marking it dirty is the caller's.
	 */
	public static List<Stocked> roll(PunkphozConfig config, List<PoolEntry> pool, RandomSource random, PunkphozHistory history) {
		List<Stocked> stock = new ArrayList<>();
		List<Chapter> chapters = pickChapters(config.chapterOvves(), random, history);
		for (Chapter chapter : chapters) stock.add(new Stocked(new ItemStack(ModContent.ovve(chapter)), config.ovvePrice(), 1));
		for (Patches.Patch patch : pick(PatchLoot.looted(), config.patches(), random)) {
			stock.add(new Stocked(new ItemStack(ModContent.patchItem(patch)), config.patchPrice(), 1));
		}
		for (PoolEntry entry : pick(pool, config.poolPicks(), random)) {
			Item item = BuiltInRegistries.ITEM.getValue(entry.item());
			if (item == Items.AIR) continue;
			stock.add(new Stocked(new ItemStack(item, entry.count()), entry.price(), entry.maxUses()));
		}
		history.recordVisit(chapters);
		return stock;
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
