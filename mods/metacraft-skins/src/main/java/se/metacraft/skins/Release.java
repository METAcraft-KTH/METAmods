package se.metacraft.skins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * When a skin came out and whether players can still get it, as the admin writes it in
 * {@code skin.json5} ({@code released: "2026/10/31"}, {@code obtainable: false}). Both are optional:
 * a skin without a date shows only whether it's obtainable, and one that doesn't say is obtainable.
 */
public record Release(Optional<LocalDate> released, boolean obtainable) {
	/** How a date is written in {@code skin.json5} and shown: year/month/day. Strict, so 2026/02/30 isn't a date. */
	public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT);

	/** The line Skinned Items and the menus show: "Released: 2026/10/31 - Obtainable" (gray, then green or red). */
	public Component line() {
		var line = Component.empty().withStyle(s -> s.withItalic(false));
		released.ifPresent(date -> line.append(Component.literal("Released: " + DATE.format(date) + " - ").withStyle(ChatFormatting.GRAY)));
		return line.append(obtainable ? Component.literal("Obtainable").withStyle(ChatFormatting.GREEN)
				: Component.literal("Unobtainable").withStyle(ChatFormatting.RED));
	}
}
