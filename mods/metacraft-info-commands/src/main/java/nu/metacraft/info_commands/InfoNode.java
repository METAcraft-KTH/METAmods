package nu.metacraft.info_commands;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import nu.metacraft.lib.config.CommentCodec;

public record InfoNode(Component message, Map<String, InfoNode> subCommands) {
	public static final Codec<InfoNode> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			CommentCodec.comment(
				ComponentSerialization.CODEC.fieldOf("message"),
				"The message to display, this is a fully featured raw text component."
			).forGetter(InfoNode::message),
			CommentCodec.comment(
				Codec.unboundedMap(Codec.STRING, Codec.lazyInitialized(InfoNode::getCodec)).fieldOf("subCommands").orElse(new HashMap<>()),
				"Additional subcommands in the same format as this one."
			).forGetter(InfoNode::subCommands)
	).apply(instance, InfoNode::new));

	public static final Codec<InfoNode> CODEC = Codec.either(ComponentSerialization.CODEC, RECORD_CODEC).xmap(
			either -> either.map(InfoNode::new, node -> node),
			node -> node.subCommands().isEmpty() ? Either.left(node.message()) : Either.right(node)
	);

	private static Codec<InfoNode> getCodec() {
		return CODEC;
	}

	public InfoNode(Component message) {
		this(message, new HashMap<>());
	}
}
