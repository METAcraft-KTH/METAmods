package se.metacraft.east_vs_west;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.lib.custom_message.CustomMessageRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The team selection dialog: what a player on no team gets instead of registering an item (or with
 * {@code /evw join}). A vanilla dialog with a button per team; a button sends a custom click action back,
 * {@code east-vs-west:join_<team>}, which metacraft-lib's {@link CustomMessageRegistry} hands to
 * {@link EventService#join}. The client only names a team: whether the player may still pick one is decided
 * on the server, against the database.
 */
final class TeamSelect {

	private TeamSelect() {}

	private static Identifier action(Team team) {
		return EastVsWest.getID("join_" + team.id());
	}

	static void init() {
		for (var team : Team.values()) {
			Registry.register(CustomMessageRegistry.REGISTRY, action(team), (payload, server, player) -> {
				var service = EastVsWest.service();
				if (service != null && service.isRunning()) {
					player.getPlayer(server).ifPresent(online -> service.join(online, team));
				}
			});
		}
	}

	static void open(ServerPlayer player) {
		var buttons = new ArrayList<ActionButton>();
		for (var team : Team.values()) {
			buttons.add(new ActionButton(
					new CommonButtonData(team.displayName().withStyle(ChatFormatting.BOLD), 200),
					Optional.of(new CustomAll(action(team), Optional.empty()))
			));
		}
		var data = new CommonDialogData(
				Component.literal("Choose your team"), Optional.empty(), true, false, DialogAction.CLOSE,
				List.of(new PlainMessage(Component.literal(
						"East vs West vs Rest: every item you register with the collector counts for your team. You choose once, and the choice is final."
				), 250)),
				List.of()
		);
		var later = new ActionButton(new CommonButtonData(Component.literal("Not now"), 150), Optional.empty());
		player.openDialog(Holder.direct(new MultiActionDialog(data, buttons, Optional.of(later), 1)));
	}
}
