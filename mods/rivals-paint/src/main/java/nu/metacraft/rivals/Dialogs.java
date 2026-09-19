package nu.metacraft.rivals;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The plumbing under the two screens this mod puts in front of a player: 26.3's server-sent dialogs.
 *
 * <p>A dialog is a screen the <em>server</em> describes and a vanilla client draws — a title, a body of
 * text and item pictures, and a row of buttons — sent as one {@code ClientboundShowDialogPacket} and
 * needing nothing of the client but the version. That is why the chapter pick and the weapon picker are
 * dialogs rather than the chest menu the picker used to be: a chest row can only ever be nine icons with
 * a tooltip, while a dialog has real prose under each picture, labelled buttons, and a title that is not
 * a container name.
 *
 * <p>Buttons carry {@link ClickEvent.RunCommand} actions, so a click is the player running
 * {@code /rivals …} themselves. That needs no confirmation screen and no custom packet: the client's
 * {@code sendUnattendedCommand} only stops to ask when the command fails to parse against the command
 * tree it was sent, needs a permission it was not given, or has signable (chat) arguments. Every command
 * behind these buttons is literals and one word, and none of them is permission-gated — that is exactly
 * why {@code metacraft.rivals} sits on each admin subcommand rather than on the {@code rivals} root.
 * Commands are written <em>without</em> a leading slash, because the client parses the string straight
 * with its dispatcher.
 *
 * <p>The item pictures may be this mod's own Polymer items. Polymer 0.18 patches
 * {@code ItemStackTemplate}'s packet codec (its {@code ItemStackTemplateMixin}), which is the type an
 * {@link ItemBody} holds, so a paint gun in a dialog reaches a vanilla client already translated into the
 * stick or spyglass it is disguised as — the same as one in an inventory slot.
 */
public final class Dialogs {
	/** A button as wide as vanilla's own, which is what two of them side by side are laid out for. */
	public static final int BUTTON_WIDTH = CommonButtonData.DEFAULT_WIDTH;
	/** The width a line of body text is wrapped at, vanilla's default. */
	public static final int TEXT_WIDTH = PlainMessage.DEFAULT_WIDTH;
	/** An item picture, in pixels: twice vanilla's 16, so a weapon is worth looking at. */
	public static final int ICON_SIZE = 32;

	private Dialogs() {}

	/** A button that runs a command as the player who clicked it. */
	public static ActionButton command(Component label, @Nullable Component tooltip, String command) {
		return new ActionButton(new CommonButtonData(label, Optional.ofNullable(tooltip), BUTTON_WIDTH),
				Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
	}

	/** A button that only closes the dialog. */
	public static ActionButton close(Component label, @Nullable Component tooltip) {
		return new ActionButton(new CommonButtonData(label, Optional.ofNullable(tooltip), BUTTON_WIDTH),
				Optional.empty());
	}

	/** A picture of a stack with a paragraph under it. */
	public static ItemBody item(ItemStack stack, Component description) {
		return item(stack, description, ICON_SIZE);
	}

	/**
	 * The same at a given size, for a row of pictures that are meant to be to scale with each other
	 * rather than all as big as the frame allows — the three specials, which really are three sizes of
	 * bomb.
	 */
	public static ItemBody item(ItemStack stack, Component description, int size) {
		return new ItemBody(ItemStackTemplate.fromStack(stack),
				Optional.of(new PlainMessage(description, TEXT_WIDTH)), true, true, size, size);
	}

	public static PlainMessage text(Component contents) {
		return new PlainMessage(contents, TEXT_WIDTH);
	}

	/**
	 * A dialog of buttons. {@code escapable} false is what makes a screen come back until it is answered:
	 * the client will not let Escape out of it, so the only way on is a button.
	 *
	 * <p>{@code pause} is false throughout. It only means anything to a single-player client, and a
	 * paused world is not what either of these screens is for.
	 */
	public static MultiActionDialog buttons(Component title, List<DialogBody> body, List<ActionButton> buttons,
			Optional<ActionButton> exit, int columns, boolean escapable) {
		return new MultiActionDialog(new CommonDialogData(title, Optional.empty(), escapable, false,
				DialogAction.CLOSE, body, List.of()), buttons, exit, columns);
	}

	/** Put a dialog on one player's screen. Silently does nothing for a player with no connection. */
	public static void open(ServerPlayer player, Dialog dialog) {
		if (player.connection == null) return;
		player.openDialog(Holder.direct(dialog));
	}
}
