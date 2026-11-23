package se.metacraft.skins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one Perk-line generator, shared by Skin Tokens, the Skin Menu, the claim menu and the
 * Skinned Item tooltip block: one line per Perk, in plain words ("While worn: +2 max hearts and a
 * particle aura"). A Perk's {@code tooltip} replaces its generated line. Effect and attribute names
 * are vanilla's translations, so each client reads them in its own language.
 */
public final class PerkLines {
	private PerkLines() {}

	public static final ChatFormatting COLOUR = ChatFormatting.BLUE;
	private static final DecimalFormat NUMBER = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

	public static List<Component> of(List<Perk> perks) {
		List<Component> lines = new ArrayList<>();
		for (Perk perk : perks) lines.add(line(perk));
		return lines;
	}

	public static Component line(Perk perk) {
		MutableComponent line = perk.tooltip().<MutableComponent>map(Component::literal).orElseGet(() -> generated(perk));
		return line.withStyle(s -> s.withItalic(false).withColor(COLOUR));
	}

	private static MutableComponent generated(Perk perk) {
		StringBuilder head = new StringBuilder(perk.trigger().phrase());
		List<String> terms = new ArrayList<>();
		if (perk.chance() < 1) terms.add(NUMBER.format(perk.chance() * 100) + "%");
		if (perk.cooldownSeconds() > 0) terms.add(NUMBER.format(perk.cooldownSeconds()) + "s cooldown");
		if (!terms.isEmpty()) head.append(" (").append(String.join(", ", terms)).append(")");
		MutableComponent line = Component.literal(head + ": ");
		List<Component> effects = perk.effects().stream().map(e -> effect(e, perk.trigger())).toList();
		for (int i = 0; i < effects.size(); i++) {
			if (i > 0) line.append(i == effects.size() - 1 ? " and " : ", ");
			line.append(effects.get(i));
		}
		return line;
	}

	private static Component effect(Perk.Effect effect, Perk.Trigger trigger) {
		return switch (effect) {
			case Perk.Potion p -> {
				MutableComponent text = Component.translatable(p.effect().value().getDescriptionId()).append(" " + roman(p.level()));
				if (p.target() == Perk.Target.OTHER) text.append(" on " + other(trigger));
				if (!trigger.lasting()) text.append(" for " + NUMBER.format(p.durationSeconds()) + "s");
				yield text;
			}
			case Perk.AttributeChange a -> attribute(a);
			case Perk.Fire f -> Component.literal("sets " + other(trigger) + " on fire for " + NUMBER.format(f.seconds()) + "s");
			case Perk.Knockback k -> Component.literal(k.lift() > 0 && k.strength() == 0 ? "launches " + other(trigger) : "knocks " + other(trigger) + " back");
			case Perk.Heal h -> Component.literal("heals " + hearts(h.amount()));
			case Perk.Particle p -> Component.literal(!trigger.lasting() ? "particles" : p.whileMoving() ? "a particle aura while moving" : "a particle aura");
			case Perk.Sound s -> Component.literal(trigger.lasting() ? "an occasional sound" : "a sound");
		};
	}

	private static Component attribute(Perk.AttributeChange a) {
		String sign = a.amount() < 0 ? "-" : "+";
		double amount = Math.abs(a.amount());
		if (a.attribute().equals(Attributes.MAX_HEALTH) && a.operation() == AttributeModifier.Operation.ADD_VALUE) {
			return Component.literal(sign + NUMBER.format(amount / 2) + " max heart" + (amount == 2 ? "" : "s"));
		}
		String value = a.operation() == AttributeModifier.Operation.ADD_VALUE ? NUMBER.format(amount) : NUMBER.format(amount * 100) + "%";
		return Component.literal(sign + value + " ").append(Component.translatable(a.attribute().value().getDescriptionId()));
	}

	/** Who {@code other} is for this Trigger. */
	private static String other(Perk.Trigger trigger) {
		return trigger == Perk.Trigger.HURT ? "the attacker" : "the target";
	}

	private static String hearts(double healthPoints) {
		return NUMBER.format(healthPoints / 2) + " heart" + (healthPoints == 2 ? "" : "s");
	}

	/** Potion levels as vanilla writes them: I, II, … (and plain numbers past 3999). */
	static String roman(int n) {
		if (n < 1 || n > 3999) return Integer.toString(n);
		int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			while (n >= values[i]) {
				out.append(numerals[i]);
				n -= values[i];
			}
		}
		return out.toString();
	}
}
