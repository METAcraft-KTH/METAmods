package nu.metacraft.lib.util.helper;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;
import java.util.SequencedCollection;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class TextHelper {

	public static String combine(SequencedCollection<String> toCombine) {
		return combine(toCombine, s -> s);
	}

	public static <T> String combine(SequencedCollection<T> toCombine, Function<T, String> stringifier) {
		String last = toCombine.isEmpty() ? "" : " and " + stringifier.apply(toCombine.getLast());
		return toCombine.stream().map(stringifier).limit(toCombine.size()-1).collect(Collectors.joining(", ")) + last;
	}

	public static Component url(URI url) {
		return url(url.toString(), url);
	}

	public static Component url(String name, URI url) {
		return Component.literal(name).withStyle(
			style -> style.withClickEvent(
				new ClickEvent.OpenUrl(url)
			).withUnderlined(true).withHoverEvent(
				new HoverEvent.ShowText(Component.translatable("narration.link.usage.hovered"))
			)
		);
	}

}
