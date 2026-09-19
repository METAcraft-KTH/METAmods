package nu.metacraft.rivals.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import nu.metacraft.rivals.Dialogs;
import nu.metacraft.rivals.PaintColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The special picker: {@link WeaponDialog} for the thing on F. One picture per {@link Special}, what it
 * is for and what it costs under it, and a button to take it.
 *
 * <p>The picture is the blob the bomb itself flies as — {@link PaintBall#blobModel}, the same model the
 * item display wears in the air — dyed in the viewer's team colour and drawn at the size that special is
 * actually thrown at, so the three pictures are the three bombs rather than three copies of one icon.
 * There is no item to show instead: a special is not a thing a player holds.
 *
 * <p>Opened by {@code /rivals special} (any player), by the weapon picker's last button, and on its own
 * right after a weapon pick by somebody who has never picked a special — once, because being asked the
 * same question every time you change guns is not a choice, it is a nag.
 */
public final class SpecialDialog {
	/** Three specials, so three pictures in a row and three buttons under them. */
	public static final int COLUMNS = 3;
	/** The smallest and largest a special's picture is drawn, in pixels. */
	public static final int MIN_ICON = 16;

	private SpecialDialog() {}

	public static void open(ServerPlayer player) {
		Dialogs.open(player, build(player));
	}

	/** The screen for one viewer: their team's colour on the bombs, their own pick marked. */
	public static MultiActionDialog build(ServerPlayer player) {
		return build(player.getTeam(), SpecialChoice.of(player.level().getServer()).orDefault(player));
	}

	/** The same over a bare team and pick, which is the whole of what the screen depends on. */
	public static MultiActionDialog build(@Nullable PlayerTeam team, Special chosen) {
		Optional<PaintColor> color = PaintColor.byTeam(team);
		List<DialogBody> body = new ArrayList<>();
		List<ActionButton> buttons = new ArrayList<>();
		for (Special special : Special.values()) {
			boolean current = special == chosen;
			body.add(Dialogs.item(icon(special, color.orElse(null)),
					Component.literal(special.displayName + (current ? " (current)" : ""))
							.withStyle(current ? ChatFormatting.GOLD : ChatFormatting.WHITE)
							.append(Component.literal("\n" + blurb(special)).withStyle(ChatFormatting.GRAY)),
					iconSize(special)));
			buttons.add(Dialogs.command(
					Component.literal(special.displayName).withStyle(current ? ChatFormatting.GOLD : ChatFormatting.WHITE),
					Component.literal(blurb(special)),
					command(special)));
		}
		Component title = Component.literal("Pick your special (F)")
				.withStyle(style -> color.map(c -> style.withColor(c.teamColor.textColor())).orElse(style));
		ActionButton keep = Dialogs.close(Component.literal("Keep the " + chosen.displayName),
				Component.literal("Change nothing"));
		return Dialogs.buttons(title, body, buttons, Optional.of(keep), COLUMNS, true);
	}

	/**
	 * The one line under a special's picture: what it does, what it costs and how long before the next
	 * one. Both numbers come from the live {@link SpecialTuning} rather than the enum's defaults, so a
	 * server that has retuned a special describes the special its players are actually throwing.
	 */
	public static String blurb(Special special) {
		SpecialTuning tuning = SpecialTuning.get(special);
		String wait = WeaponTuning.number(tuning.value(SpecialTuning.Param.COOLDOWN) / 20.0);
		return special.blurb + " — " + tuning.intValue(SpecialTuning.Param.INK) + " ink, " + wait + " s wait";
	}

	/** A special's picture: the bomb it flies as, in the viewer's colour, named. */
	public static ItemStack icon(Special special, @Nullable PaintColor color) {
		ItemStack stack = PaintBall.blobModel(color, special);
		stack.set(DataComponents.ITEM_NAME, Component.literal(special.displayName));
		return stack;
	}

	/**
	 * How big to draw it: the size the bomb is actually thrown at, against the splat bomb's, so the
	 * pictures are to scale with each other. A burst bomb <em>is</em> a smaller bomb, and a row of three
	 * identical blobs would have said the opposite.
	 */
	public static int iconSize(Special special) {
		double scale = SpecialTuning.get(special).value(SpecialTuning.Param.SCALE);
		long size = Math.round(Dialogs.ICON_SIZE * scale / Weapon.SPECIAL_SCALE);
		return (int) Math.clamp(size, MIN_ICON, Dialogs.ICON_SIZE);
	}

	/** The command one special's button runs. No leading slash: the client parses it with its dispatcher. */
	public static String command(Special special) {
		return "rivals special pick " + special.commandId();
	}

	/**
	 * Take the special: remembered, and said out loud. No inventory work at all, which is why this lives
	 * next to the screen rather than in a {@link WeaponPicks} of its own — a special is not a thing that
	 * has to be put in a slot, it is the answer to what F does.
	 */
	public static void pick(ServerPlayer player, Special special) {
		SpecialChoice.of(player.level().getServer()).set(player, special);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7f, 1.4f);
		player.sendSystemMessage(Component.literal("F throws the " + special.displayName.toLowerCase()
				+ ". " + blurb(special)).withStyle(ChatFormatting.AQUA));
	}
}
