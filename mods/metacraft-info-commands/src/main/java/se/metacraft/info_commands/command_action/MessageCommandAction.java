package se.metacraft.info_commands.command_action;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import se.metacraft.config.util.CommentCodec;

public record MessageCommandAction(Component message) implements CommandAction {

	public static final MapCodec<MessageCommandAction> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			CommentCodec.comment(
				ComponentSerialization.CODEC.fieldOf("message"),
				"The message to display, this is a fully featured raw text component."
			).forGetter(MessageCommandAction::message)
		).apply(instance, MessageCommandAction::new)
	);

	@Override
	public MapCodec<? extends CommandAction> codec() {
		return CODEC;
	}

	@Override
	public int run(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> message, false);
		return 1;
	}

}
