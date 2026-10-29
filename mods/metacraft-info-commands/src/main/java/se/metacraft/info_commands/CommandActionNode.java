package se.metacraft.info_commands;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import se.metacraft.config.util.CommentCodec;
import se.metacraft.info_commands.command_action.CommandAction;
import se.metacraft.info_commands.command_action.MessageCommandAction;

public record CommandActionNode(CommandAction action, Map<String, CommandActionNode> subCommands) {

	private static final MapCodec<CommandActionNode> RECORD_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		CommandAction.REGISTRY_CODEC.forGetter(CommandActionNode::action),
		CommentCodec.comment(
			Codec.unboundedMap(Codec.STRING, Codec.lazyInitialized(CommandActionNode::getCodec)).optionalFieldOf("subCommands", Map.of()).orElse(new HashMap<>()),
			"Additional subcommands in the same format as this one."
		).forGetter(CommandActionNode::subCommands)
	).apply(instance, CommandActionNode::new));

	public static final Codec<CommandActionNode> CODEC = Codec.either(RECORD_CODEC.codec(), ComponentSerialization.CODEC).xmap(
			either -> either.map(node -> node, text -> new CommandActionNode(new MessageCommandAction(text), Map.of())),
			node -> node.subCommands().isEmpty() && node.action() instanceof MessageCommandAction(Component message) ?
				Either.right(message) : Either.left(node)
	);

	private static Codec<CommandActionNode> getCodec() {
		return CODEC;
	}

	public CommandActionNode(CommandAction action) {
		this(action, new HashMap<>());
	}
}
