package nu.metacraft.info_commands;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public record InfoNode(Component message, Map<String, InfoNode> subCommands) {
	public static final Codec<InfoNode> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ComponentSerialization.CODEC.fieldOf("message").forGetter(InfoNode::message),
			Codec.unboundedMap(Codec.STRING, Codec.lazyInitialized(InfoNode::getCodec)).fieldOf("subCommands")
					.orElse(new HashMap<>()).forGetter(InfoNode::subCommands)
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
