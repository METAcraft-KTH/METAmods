package se.metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;
import se.metacraft.ovvar.content.PatchLoot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The {@code punkphoz} block of {@code config/ovvar.json}: the wandering trader Punkphöz. Every key has a default.
 *
 * @param enabled        whether he turns up at all
 * @param share          his share (0-0.5) of all wanderer visits
 * @param chapterOvves   how many chapters' ovvar (only he sells them) are on offer per visit
 * @param ovvePrice      emeralds asked for one
 * @param patches        how many loot patches are on offer per visit
 * @param patchPrice     emeralds asked for one
 * @param poolPicks      how many of {@code pool} are on offer per visit
 * @param playersPerRoll how many players count as one more roll of his visit
 * @param maxLoaded      how many Punkphöz can be loaded at once
 * @param pool           the extra things he sells
 */
public record PunkphozConfig(boolean enabled, double share, int chapterOvves, int ovvePrice, int patches, int patchPrice,
			int poolPicks, int playersPerRoll, int maxLoaded, List<PoolEntry> pool) {
	public static final PunkphozConfig DEFAULT = new PunkphozConfig(true, 0.25, 3, 24, 2, 6, 4, 5, 3, kulturPatterns());

	/** Kultur's banner patterns that need an item (its catalogue order): the IT clubs' and Pirkko, 4 emeralds each, once per visit. */
	private static List<PoolEntry> kulturPatterns() {
		// Every kultur pattern with an item; the chapter logos are free in the loom, so none of theirs. With
		// pool_picks 4 he offers all four each visit. Checked by defaultPoolMatchesKultur under -PwithKultur.
		return Stream.of("itk", "qmisk", "tmeit", "pirkko")
				.map(id -> new PoolEntry(Identifier.fromNamespaceAndPath("kultur", id + "_banner_pattern"), 4, 1, 1))
				.toList();
	}

	/** One thing in his pool: {@code count} of {@code item} for {@code price} emeralds, {@code maxUses} times. */
	public record PoolEntry(Identifier item, int price, int count, int maxUses) {
		public static final Codec<PoolEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("item").forGetter(PoolEntry::item),
				Codec.intRange(1, 64).fieldOf("price").forGetter(PoolEntry::price),
				Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(PoolEntry::count),
				ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_uses", 1).forGetter(PoolEntry::maxUses)
		).apply(i, PoolEntry::new));
	}

	public static final MapCodec<PunkphozConfig> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			ConfigFields.field(Codec.BOOL, "enabled", DEFAULT.enabled, "Whether Punkphoz, a wandering trader with his own stock, turns up.").forGetter(PunkphozConfig::enabled),
			ConfigFields.field(Codec.doubleRange(0, 0.5), "share", DEFAULT.share, "His share of all wanderer visits; vanilla wanderers come as often as without him.").forGetter(PunkphozConfig::share),
			ConfigFields.field(Codec.intRange(0, 20), "chapter_ovves", DEFAULT.chapterOvves, "How many chapters' ovvar (only he sells them) are on offer per visit.").forGetter(PunkphozConfig::chapterOvves),
			ConfigFields.field(Codec.intRange(1, 64), "ovve_price", DEFAULT.ovvePrice, "Emeralds asked for one ovve.").forGetter(PunkphozConfig::ovvePrice),
			ConfigFields.field(Codec.intRange(0, 10), "patches", DEFAULT.patches, "How many loot patches are on offer per visit.").forGetter(PunkphozConfig::patches),
			ConfigFields.field(Codec.intRange(1, 64), "patch_price", DEFAULT.patchPrice, "Emeralds asked for one patch.").forGetter(PunkphozConfig::patchPrice),
			ConfigFields.field(Codec.intRange(0, 20), "pool_picks", DEFAULT.poolPicks, "How many things from the pool are on offer per visit.").forGetter(PunkphozConfig::poolPicks),
			ConfigFields.field(Codec.intRange(1, 100), "players_per_roll", DEFAULT.playersPerRoll, "How many players online count as one roll for his visit.").forGetter(PunkphozConfig::playersPerRoll),
			ConfigFields.field(Codec.intRange(0, 20), "max_loaded", DEFAULT.maxLoaded, "How many Punkphoz can be loaded at once.").forGetter(PunkphozConfig::maxLoaded),
			ConfigFields.field(PoolEntry.CODEC.listOf(), "pool", DEFAULT.pool, "The things he sells besides ovvar and patches: item (registry id), price (emeralds, 1-64),", "count (1-64, default 1) and max_uses (default 1). An item that isn't registered is left out and logged.").forGetter(PunkphozConfig::pool)
	).apply(i, PunkphozConfig::new));

	/** The pool's entries that can be offered, and why the others can't. */
	public record Resolved(List<PoolEntry> entries, List<String> warnings) {}

	/** The config whose pool {@link #poolOnOffer()} last resolved. */
	@Nullable
	private static PunkphozConfig resolvedFor;
	private static List<PoolEntry> resolvedEntries = List.of();
	/** How many times pool warnings have been logged (a test checks it counts once per load). */
	private static final AtomicInteger WARNINGS_LOGGED = new AtomicInteger();

	/** For the test of once-per-load. */
	public static int warningsLogged() {
		return WARNINGS_LOGGED.get();
	}

	/** The pool minus what isn't registered and every patch that is not loot. Pure. */
	public static Resolved resolve(PunkphozConfig config) {
		List<PoolEntry> entries = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		for (PoolEntry entry : config.pool()) {
			Identifier id = entry.item();
			if (!BuiltInRegistries.ITEM.containsKey(id)) {
				warnings.add("Punkphöz: leaving out " + id + " from the pool: it isn't registered");
				continue;
			}
			String path = id.getPath();
			if (id.getNamespace().equals(Ovvar.MOD_ID) && path.startsWith("patch_")) {
				String patch = path.substring("patch_".length());
				if (!PatchLoot.LOOT.contains(patch)) {
					warnings.add("Punkphöz: leaving out " + id + " from the pool: patch '" + patch + "' is given-only (PatchLoot.NON_LOOT)");
					continue;
				}
			}
			entries.add(entry);
		}
		return new Resolved(List.copyOf(entries), List.copyOf(warnings));
	}

	/** The loaded config's pool on offer; worked out, and its warnings logged, once per config instance. */
	public static synchronized List<PoolEntry> poolOnOffer() {
		return poolOnOffer(OvvarConfig.get().punkphoz());
	}

	/** As {@link #poolOnOffer()}, for this config. */
	public static synchronized List<PoolEntry> poolOnOffer(PunkphozConfig config) {
		if (resolvedFor != config) {
			Resolved resolved = resolve(config);
			resolved.warnings().forEach(Ovvar.LOGGER::warn);
			if (!resolved.warnings().isEmpty()) WARNINGS_LOGGED.addAndGet(resolved.warnings().size());
			resolvedEntries = resolved.entries();
			resolvedFor = config;
		}
		return resolvedEntries;
	}
}
