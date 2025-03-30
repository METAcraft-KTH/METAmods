package se.datasektionen.mc.metacraft_core.preferences;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.stream.Stream;

public class PreferenceMenu extends SelectorMenu {

	public static final String COMMAND = "metaprefs";

	private static <T, V, P extends Preference<? extends T, ? extends V, ?>> boolean isVisibleTo(RegistryEntry<P> pref, ServerPlayerEntity player) {
		return PreferenceData.downcast(pref).value().type().isVisibleTo(player, PreferenceData.downcast(pref));
	}

	private static Stream<RegistryEntry.Reference<Preference<?, ?, ?>>> getPreferences(ServerPlayerEntity player) {
		return player.getRegistryManager().getOrThrow(Preference.REGISTRY_KEY).streamEntries().filter(
				pref -> isVisibleTo(pref, player)
		);
	}

	private final ServerPlayerEntity player;

	public <T, V, P extends Preference<? extends T, ? extends V, ?>> GuiElementInterface getGuiElement(
			ServerPlayerEntity player, RegistryEntry<P> entry
	) {
		var icon = PreferenceData.downcast(entry).value().icons().stream().filter(
				i -> i.shouldShowIcon(
						PreferenceData.getForPlayer(player).get(PreferenceData.downcast(entry))
				)
		).findFirst().map(PreferenceType.Icon::items).orElse(PreferenceType.IconData.EMPTY);
		return icon.createBuilder(player).setCallback(
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

	public PreferenceMenu(ServerPlayerEntity player) {
		super((int) getPreferences(player).count(), player);
		this.player = player;
		this.setTitle(Text.literal("Preferences Menu"));
		refreshButtons();
	}

}
