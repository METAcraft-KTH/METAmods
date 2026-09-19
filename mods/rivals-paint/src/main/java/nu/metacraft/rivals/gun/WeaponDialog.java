package nu.metacraft.rivals.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import nu.metacraft.rivals.Dialogs;
import nu.metacraft.rivals.PaintColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The weapon picker: a 26.3 dialog with a picture of each weapon, what it is for under it, and a button
 * to take it — and, last of all, a button out to {@link SpecialDialog}, because the weapon and the thing
 * on F are two choices and this is where a player is already standing when they make the first.
 *
 * <p>Each picture is the <em>real</em> weapon stack dyed in the viewer's own team colour, the way one in
 * their hand is, so the screen is four paint guns drawn by their own models rather than four stand-in
 * vanilla items. It was a one-row chest menu; a chest row could only ever be nine icons and a tooltip,
 * and what a weapon is for is a sentence, not a hover.
 *
 * <p>Buttons run {@code /rivals weapons pick <id>} as the player, which is
 * {@link WeaponPicks#pick} — so the command line, the compass and the dialog are one path.
 *
 * <p>Opened by {@code /rivals weapons} (any player: picking your own weapon is not an admin act), by
 * right-clicking the {@link WeaponSelector} the lobby hands out, and by a match start for anybody who
 * has never picked.
 */
public final class WeaponDialog {
	/** Four buttons to a row: the four weapons side by side under their pictures, and the special on a row of its own. */
	public static final int COLUMNS = 4;

	private WeaponDialog() {}

	public static void open(ServerPlayer player) {
		Dialogs.open(player, build(player));
	}

	/**
	 * The screen for one viewer: their team's colour on the weapons, their own pick marked. Built without
	 * touching the connection, so a test can read it off a mock player.
	 */
	public static MultiActionDialog build(ServerPlayer player) {
		Weapon chosen = WeaponChoice.of(player.level().getServer()).orDefault(player);
		Special special = SpecialChoice.of(player.level().getServer()).orDefault(player);
		return build(player.getTeam(), chosen, special);
	}

	/** The same over a bare team and pick, which is the whole of what the screen depends on. */
	public static MultiActionDialog build(@Nullable PlayerTeam team, Weapon chosen) {
		return build(team, chosen, SpecialChoice.DEFAULT);
	}

	/** The same, told which special the viewer throws, which is what the last button says. */
	public static MultiActionDialog build(@Nullable PlayerTeam team, Weapon chosen, Special special) {
		Optional<PaintColor> color = PaintColor.byTeam(team);
		List<DialogBody> body = new ArrayList<>();
		List<ActionButton> buttons = new ArrayList<>();
		for (Weapon weapon : Weapon.values()) {
			boolean current = weapon == chosen;
			ItemStack stack = PaintWeapon.withTankColor(new ItemStack(PaintWeapon.of(weapon)), team);
			body.add(Dialogs.item(stack, Component.literal((current ? "✔ " : "") + weapon.displayName + (current ? " — yours" : ""))
					.withStyle(current ? ChatFormatting.GOLD : ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD)
					.append(Component.literal("\n" + WeaponPicks.blurb(weapon)).withStyle(ChatFormatting.GRAY))));
			buttons.add(Dialogs.command(
					Component.literal(current ? "✔ " + weapon.displayName : "Take the " + weapon.displayName)
							.withStyle(current ? ChatFormatting.GOLD : ChatFormatting.WHITE),
					Component.literal(WeaponPicks.blurb(weapon)),
					command(weapon)));
		}
		// And the other half of a loadout, on a button of its own rather than a fifth picture: what F
		// throws is not a weapon and does not belong in a row of them, but it is the next thing a player
		// wants after they have picked one, and the only place they would think to look for it is here.
		buttons.add(Dialogs.command(
				Component.literal("Special: " + special.displayName + " \u2192 change").withStyle(ChatFormatting.AQUA),
				Component.literal(SpecialDialog.blurb(special)),
				SPECIAL_COMMAND));
		Component title = Component.literal("Pick your weapon")
				.withStyle(style -> color.map(c -> style.withColor(c.teamColor.textColor())).orElse(style));
		// The way out says what staying costs nothing: the weapon they already have.
		ActionButton keep = Dialogs.close(Component.literal("Keep the " + chosen.displayName),
				Component.literal("Change nothing"));
		return Dialogs.buttons(title, body, buttons, Optional.of(keep), COLUMNS, true);
	}

	/** The command one weapon's button runs. No leading slash: the client parses it with its dispatcher. */
	public static String command(Weapon weapon) {
		return "rivals weapons pick " + weapon.commandId();
	}

	/** What the last button runs: the special picker, on the player's own screen. */
	public static final String SPECIAL_COMMAND = "rivals special";
}
