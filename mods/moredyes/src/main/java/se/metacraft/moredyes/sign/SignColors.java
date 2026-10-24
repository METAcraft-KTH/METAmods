package se.metacraft.moredyes.sign;

import se.metacraft.moredyes.color.ModColor;
import se.metacraft.moredyes.color.ModColors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignText;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Sign text in our colours. A sign's own colour is a {@link DyeColor}, but its lines are text
 * components, and a component's colour is any RGB, which a vanilla client draws as it is. So a
 * sign dyed with our dye keeps vanilla's colour slot on the nearest vanilla dye (glow ink's outline
 * uses it) and carries our colour on every line. Editing a sign replaces its lines with plain text;
 * {@link #keep} paints the new lines the colour the old ones had. A vanilla dye takes our colour off
 * the lines ({@link #unpaint}) before it sets its own.
 */
public final class SignColors {
	private SignColors() {}

	/** Every line, front or filtered, in this colour; the sign's own colour the nearest vanilla dye. */
	public static SignText paint(SignText text, ModColor color) {
		return new SignText(paint(text.getMessages(false), color), paint(text.getMessages(true), color),
				nearestDye(color), text.hasGlowingText());
	}

	private static java.util.List<Component> paint(java.util.List<Component> lines, ModColor color) {
		return lines.stream().map(line -> (Component) line.copy().withStyle(s -> s.withColor(TextColor.fromRgb(color.rgb() & 0xFFFFFF)))).toList();
	}

	/**
	 * Every line in one of our colours back to plain text, which shows the sign's own colour: what a
	 * vanilla dye needs first, since it only sets that colour and our lines would hide it.
	 */
	public static SignText unpaint(SignText text) {
		return new SignText(unpaint(text.getMessages(false)), unpaint(text.getMessages(true)), text.getColor(), text.hasGlowingText());
	}

	private static java.util.List<Component> unpaint(java.util.List<Component> lines) {
		return lines.stream().map(line -> isOurs(line.getStyle().getColor())
				? (Component) line.copy().withStyle(s -> s.withColor((TextColor) null)) : line).toList();
	}

	private static boolean isOurs(@Nullable TextColor c) {
		return c != null && ModColors.all().stream().anyMatch(color -> (color.rgb() & 0xFFFFFF) == c.getValue());
	}

	/** Our colour, if the sign's lines are in one. */
	public static @Nullable ModColor colourOf(SignText text) {
		for (Component line : text.getMessages(false)) {
			TextColor c = line.getStyle().getColor();
			if (c == null) continue;
			for (ModColor color : ModColors.all()) {
				if ((color.rgb() & 0xFFFFFF) == c.getValue()) return color;
			}
		}
		return null;
	}

	/** {@code edited}, painted the colour {@code before} was in, if it was in one of ours. */
	public static SignText keep(SignText before, SignText edited) {
		ModColor color = colourOf(before);
		return color == null ? edited : paint(edited, color);
	}

	/** The vanilla dye standing in for ours: colors.json's {@code vanilla}, else the one whose text colour is nearest. */
	public static DyeColor nearestDye(ModColor color) {
		return color.vanilla().orElseGet(() -> Arrays.stream(DyeColor.values())
				.min(Comparator.comparingInt(d -> distance(d.getTextColor(), color.rgb())))
				.orElse(DyeColor.BLACK));
	}

	static int distance(int a, int b) {
		int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
		return dr * dr + dg * dg + db * db;
	}
}
