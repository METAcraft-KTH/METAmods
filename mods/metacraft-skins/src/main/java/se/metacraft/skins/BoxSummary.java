package se.metacraft.skins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * One Skin Box file as the server has it since the last reload: its id, its name ({@code name}
 * without formatting codes, {@code title} with them and its gradient) and description (once it
 * loads), and, for a Dormant box, why it didn't load.
 * {@code entries} are its usable skins (loaded on the server when the box was read), highest chance
 * first, ties in file order; {@code leftOut} the ids of the skins it lists that are unknown or
 * Dormant, in file order.
 */
public record BoxSummary(String id, Optional<String> name, Optional<Component> title, Optional<String> description,
		List<Entry> entries, List<String> leftOut, Optional<String> dormantReason) {

	/** A usable skin of the box, its weight and its chance worded for players ({@link #chance(long, long)}). */
	public record Entry(String skin, int weight, String chance) {}

	public boolean dormant() {
		return dormantReason.isPresent();
	}

	/** What players and admins read: the name, or the id while the box is Dormant. */
	public String displayName() {
		return name.orElse(id);
	}

	/** The name in its colours ({@link SkinName}); the id, gray, while the box is Dormant. */
	public Component styledName() {
		return title.orElseGet(() -> Component.literal(id).withStyle(ChatFormatting.GRAY));
	}

	/** How many skins the file lists, usable or left out. */
	public int listed() {
		return entries.size() + leftOut.size();
	}

	/**
	 * One skin drawn from {@code entries} (at least one) by weight: each skin's chance is its weight in
	 * their total. The total can pass an int (thousands of skins at the largest weight).
	 */
	public static String draw(List<Entry> entries, RandomSource random) {
		long total = entries.stream().mapToLong(Entry::weight).sum();
		long r = total <= Integer.MAX_VALUE ? random.nextInt((int) total) : Math.floorMod(random.nextLong(), total);
		for (Entry entry : entries) {
			r -= entry.weight();
			if (r < 0) return entry.skin();
		}
		throw new IllegalArgumentException("nothing to draw from: " + entries);
	}

	/**
	 * A chance of {@code weight} in {@code total}, as players read it: a percentage rounded half-up to
	 * one decimal place, without a trailing {@code .0} ({@code 20%}, {@code 7.5%}, {@code 0.3%}), and
	 * {@code <0.1%} for anything that rounds to nothing.
	 */
	public static String chance(long weight, long total) {
		BigDecimal percent = BigDecimal.valueOf(100 * weight).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
		if (percent.signum() == 0) return "<0.1%";
		return percent.stripTrailingZeros().toPlainString() + "%";
	}
}
