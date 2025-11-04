package nu.metacraft.core.preferences;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import nu.metacraft.lib.util.DisplayItemData;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PreferenceMenu extends SelectorMenu {

	public static final String COMMAND = "metaprefs";

	private static <T, V, P extends Preference<? extends T, ? extends V, ?>> boolean isVisibleTo(Holder<P> pref, ServerPlayer player) {
		return PreferenceData.downcast(pref).value().type().isVisibleTo(player, PreferenceData.downcast(pref));
	}

	private static Stream<Holder.Reference<Preference<?, ?, ?>>> getPreferences(ServerPlayer player) {
		return player.registryAccess().lookupOrThrow(Preference.REGISTRY_KEY).listElements().filter(
				pref -> isVisibleTo(pref, player)
		);
	}

	private final ServerPlayer player;

	public <T, V, P extends Preference<? extends T, ? extends V, ?>> GuiElementInterface getGuiElement(
			ServerPlayer player, Holder<P> entry
	) {
		var icon = PreferenceData.downcast(entry).value().icons().stream().filter(
				i -> i.shouldShowIcon(
						PreferenceData.getForPlayer(player).get(PreferenceData.downcast(entry))
				)
		).findFirst().map(PreferenceType.Icon::items).orElse(DisplayItemData.EMPTY);
		return PreferenceType.Icon.createBuilder(icon, player).setCallback(
				() -> {
					PreferenceData.downcast(entry).value().type().onClicked(player, PreferenceData.downcast(entry), this);
				}
		).build();
	}

	public void refreshButtons() {
		List<GuiElementInterface> buttons = getPreferences(player).map(
				pref -> getGuiElement(player, pref)
		).toList();
		pages.setElements(buttons);
	}

	public PreferenceMenu(ServerPlayer player) {
		super((int) getPreferences(player).count(), player);
		this.player = player;
		this.setTitle(Component.literal("Preferences Menu"));
		refreshButtons();
	}

}
