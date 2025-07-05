package nu.metacraft.season_4.util;

import net.minecraft.text.ParsedSelector;
import net.minecraft.text.Text;

import java.util.Optional;

public class DialogueHelper {

	public static Text makeDialogue(Text message) {
		return Text.literal("<").append(
				Text.selector(ParsedSelector.parse("@s").getOrThrow(), Optional.empty())
		).append("> ").append(message);
	}

}
