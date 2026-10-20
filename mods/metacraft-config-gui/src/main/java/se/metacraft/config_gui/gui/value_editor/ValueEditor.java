package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import se.metacraft.config_gui.ClickHandlerGUI;

import java.util.List;
import java.util.Optional;

public interface ValueEditor<T> extends ClickHandlerGUI {

	DataResult<Optional<T>> getValue(HolderLookup.Provider lookup);

	static MutableComponent highlight(Component text) {
		return text.copy().withStyle(style -> style.withColor(TextColor.YELLOW).withUnderlined(true));
	}

	static List<DialogBody> removeMessage(Component value) {
		return List.of(
			new PlainMessage(Component.literal("Are you sure you want to remove ").append(value).append("?"), 300)
		);
	}

	static CommonButtonData removeButtonData() {
		return new CommonButtonData(
			Component.literal("\uD83D\uDDD1").withStyle(style -> style.withColor(TextColor.RED)),
			20
		);
	}

}
