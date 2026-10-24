package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.TreePSet;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Search;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Select;
import se.metacraft.config_gui.gui.value_editor.trait.Selectable;
import se.metacraft.config_gui.gui.value_editor.trait.WithSearchBar;

import java.util.*;
import java.util.stream.Stream;

public record DropdownEditor<T>(
	Codec<T> codec, T value, Map<String, T> possibleValues,
	Optional<String> search,
	Optional<ValueEditor.ParentInfo> parent
) implements ValueEditor<T>, Selectable, WithSearchBar {

	private static final String SEARCH_KEY = "search";

	@Override
	public DataResult<Optional<T>> getValue(HolderLookup.Provider lookup) {
		return DataResult.success(Optional.of(value));
	}

	private Component name(String key) {
		if (possibleValues.get(key) == value) {
			return Component.literal(key).withStyle(style -> style.withUnderlined(true).withColor(ChatFormatting.YELLOW));
		} else {
			return Component.literal(key);
		}
	}

	@Override
	public Holder<Dialog> createDialog(ServerPlayer player) {
		List<Input> inputs = List.of();
		var keys = possibleValues.keySet();

		int columnCount = Math.min((keys.size() / 20) + 1, 2);

		List<ActionButton> extraButtons = List.of();
		if (search.isPresent()) {
			inputs = List.of(
				new Input(
					SEARCH_KEY,
					new TextInput(
						200, Component.translatable("debug.options.search"),
						false, search.get(), 200, Optional.empty()
					)
				)
			);
			keys = PCollectionsHelper.collect(
				keys.stream().filter(
					v -> v.toLowerCase(Locale.ROOT).contains(search.get().toLowerCase(Locale.ROOT))
				),
				TreePSet.empty()
			);
			extraButtons = List.of(
				new ActionButton(
					new CommonButtonData(
						Component.translatable("debug.options.search"),
						200
					),
					Optional.of(
						ClickHandler.click(player.registryAccess(), new Search(SEARCH_KEY, search.get()), true)
					)
				)
			);
			columnCount = 1;
		}
		return CodecDialog.template(
			List.of(), inputs, Stream.concat(
				extraButtons.stream(), keys.stream().map(
					key -> new ActionButton(
						new CommonButtonData(name(key), CommonButtonData.DEFAULT_WIDTH),
						Optional.of(ClickHandler.click(player.registryAccess(), new Select(key), false))
					)
				)
			).toList(),
			player.registryAccess(), columnCount
		);
	}

	@Override
	public DialogGUI onSelect(String key, ServerPlayer player) {
		if (possibleValues.containsKey(key) && possibleValues.get(key) != value) {
			return new DropdownEditor<>(
				codec, possibleValues.get(key), possibleValues, search, parent
			);
		}
		return this;
	}

	@Override
	public DialogGUI onSearch(ServerPlayer player, String searchBarKey, String search) {
		if (searchBarKey.equals(SEARCH_KEY)) {
			return new DropdownEditor<>(
				codec, value, possibleValues, Optional.of(search), parent
			);
		}
		return this;
	}
}
