package se.metacraft.skins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A skin's {@code name} as written in {@code skin.json5}, with Minecraft's formatting codes:
 * {@code &} (or {@code §}) and one of {@code 0}–{@code 9}, {@code a}–{@code f} for a colour,
 * {@code k}–{@code o} for obfuscated, bold, strikethrough, underlined and italic, {@code r} to reset,
 * or {@code &#RRGGBB} for any colour. As in vanilla, a colour clears the formats before it, so
 * {@code &6&lCowboy Hat} is bold gold and {@code &l&6Cowboy Hat} only gold. Text before any colour
 * is yellow. An {@code &} not followed by a code is plain text ({@code Salt & Pepper}).
 * <p>
 * Or the name is coloured by a {@link Gradient}, letter by letter, and its codes only format it.
 */
public final class SkinName {
	private SkinName() {}

	/**
	 * Colours running through the name, left to right: the first on its first letter, the last on
	 * its last. {@code mirror} runs them to the middle and back, the first on both ends.
	 */
	public record Gradient(List<TextColor> colours, boolean mirror) {
		/** The colour at {@code t}, 0 the first letter and 1 the last. */
		TextColor at(double t) {
			if (mirror) t = 1 - Math.abs(2 * t - 1);
			double position = t * (colours.size() - 1);
			int from = Math.min((int) position, colours.size() - 2);
			double f = position - from;
			int a = colours.get(from).getValue(), b = colours.get(from + 1).getValue();
			return TextColor.fromRgb(channel(a, b, 16, f) | channel(a, b, 8, f) | channel(a, b, 0, f));
		}

		private static int channel(int a, int b, int shift, double f) {
			int x = a >> shift & 0xFF, y = b >> shift & 0xFF;
			return (int) Math.round(x + (y - x) * f) << shift;
		}
	}

	/** The style of a name before any code, and after {@code &r}: yellow, and never italic as an item's custom name otherwise is. */
	private static final Style BASE = Style.EMPTY.withItalic(false).withColor(ChatFormatting.YELLOW);
	private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{6}");
	/** The codes that format rather than colour. */
	private static final String FORMATS = "klmnorKLMNOR";

	/** A stretch of the name in one style. */
	private record Run(String text, Style style) {}

	/** The name with its codes applied. */
	public static Component styled(String written) {
		return styled(written, Optional.empty());
	}

	/** The name with its codes applied, and coloured by {@code gradient} if there is one. */
	public static Component styled(String written, Optional<Gradient> gradient) {
		MutableComponent name = Component.empty().withStyle(BASE);
		List<Run> runs = runs(written);
		if (gradient.isEmpty()) {
			runs.forEach(run -> name.append(Component.literal(run.text()).withStyle(run.style())));
			return name;
		}
		int letters = runs.stream().mapToInt(run -> run.text().length()).sum();
		int i = 0;
		for (Run run : runs) {
			for (char c : run.text().toCharArray()) {
				TextColor colour = gradient.get().at(letters == 1 ? 0 : (double) i++ / (letters - 1));
				name.append(Component.literal(String.valueOf(c)).withStyle(run.style().withColor(colour)));
			}
		}
		return name;
	}

	/** The name without its codes: what chat messages, menu titles and logs say. */
	public static String plain(String written) {
		return styled(written).getString();
	}

	/** Whether the name has a colour code ({@code &6}, {@code &#FF8C00}), which a gradient leaves no room for. */
	public static boolean hasColourCode(String written) {
		return codes(written).stream().anyMatch(code -> FORMATS.indexOf(code) < 0);
	}

	private static List<Run> runs(String written) {
		List<Run> runs = new ArrayList<>();
		StringBuilder run = new StringBuilder();
		Style style = BASE;
		for (int i = 0; i < written.length(); i++) {
			int length = codeLength(written, i);
			if (length == 0) {
				run.append(written.charAt(i));
				continue;
			}
			if (!run.isEmpty()) runs.add(new Run(run.toString(), style));
			run.setLength(0);
			if (length == 8) {
				style = BASE.withColor(TextColor.fromRgb(Integer.parseInt(written.substring(i + 2, i + 8), 16)));
			} else {
				ChatFormatting format = ChatFormatting.getByCode(written.charAt(i + 1));
				// Vanilla's rules: a colour clears the formats before it. Its reset would clear the base too.
				style = format == ChatFormatting.RESET ? BASE : style.applyLegacyFormat(format);
			}
			i += length - 1;
		}
		if (!run.isEmpty()) runs.add(new Run(run.toString(), style));
		return runs;
	}

	/** Each code's character after the {@code &} ({@code #} for a hex colour). */
	private static List<Character> codes(String written) {
		List<Character> codes = new ArrayList<>();
		for (int i = 0; i < written.length(); i++) {
			int length = codeLength(written, i);
			if (length > 0) codes.add(written.charAt(i + 1));
			i += Math.max(length - 1, 0);
		}
		return codes;
	}

	/** How long the code at {@code i} is: 2 ({@code &l}), 8 ({@code &#FF8C00}), or 0 for no code. */
	private static int codeLength(String written, int i) {
		char c = written.charAt(i);
		if (c != '&' && c != '§' || i + 1 >= written.length()) return 0;
		if (written.charAt(i + 1) == '#' && i + 8 <= written.length() && HEX.matcher(written.substring(i + 2, i + 8)).matches()) return 8;
		return ChatFormatting.getByCode(written.charAt(i + 1)) != null ? 2 : 0;
	}
}
