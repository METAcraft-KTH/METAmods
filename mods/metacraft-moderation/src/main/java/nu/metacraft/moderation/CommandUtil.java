package nu.metacraft.moderation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import org.apache.commons.lang3.mutable.MutableInt;
import nu.metacraft.moderation.exile.mixin.StringRangeAccessor;
import nu.metacraft.moderation.exile.mixin.SuggestionAccessor;

import static net.minecraft.commands.Commands.argument;

public class CommandUtil {

	public static final SuggestionProvider<CommandSourceStack> ROOT_COMMAND_SUGGEST = (ctx, suggestionsBuilder) -> {
		return SharedSuggestionProvider.suggest(
				ctx.getRootNode().getChildren().stream().map(CommandNode::getName), suggestionsBuilder
		);
	};

	public static SuggestionProvider<CommandSourceStack> getCommandSuggest(
			int pos, CommandDispatcher<CommandSourceStack> dispatcher
	) {
		return (ctx, suggestionsBuilder) -> {
			StringBuilder builder = new StringBuilder();
			int startPos = ctx.getNodes().get(ctx.getNodes().size() - pos).getRange().getStart();
			for (int i = pos; i > 0; i--) {
				builder.append(StringArgumentType.escapeIfRequired(
						StringArgumentType.getString(
								ctx, ctx.getNodes().get(ctx.getNodes().size() - i).getNode().getName()
						)
				)).append(" ");
			}
			var result = dispatcher.parse(builder.toString(), ctx.getSource());
			return dispatcher.getCompletionSuggestions(result).thenApply(suggestions -> {
				MutableInt totalOffset = new MutableInt(0);
				suggestions.getList().forEach(suggestion -> {
					var newString = StringArgumentType.escapeIfRequired(suggestion.getText());
					int offset = newString.length() - suggestion.getText().length();
					((SuggestionAccessor) suggestion).setText(newString);
					((StringRangeAccessor) suggestion.getRange()).setStart(suggestion.getRange().getStart()+startPos);
					((StringRangeAccessor) suggestion.getRange()).setEnd(suggestion.getRange().getEnd()+startPos + offset);
					totalOffset.setValue(Math.max(offset, totalOffset.getValue()));
				});
				((StringRangeAccessor) suggestions.getRange()).setStart(suggestions.getRange().getStart()+startPos);
				((StringRangeAccessor) suggestions.getRange()).setEnd(suggestions.getRange().getEnd()+startPos + totalOffset.getValue());
				return suggestions;
			});
		};
	}

	@FunctionalInterface
	public interface PassCommand {
		int run(CommandContext<CommandSourceStack> context, String command) throws CommandSyntaxException;
	}

	public static ArgumentBuilder<CommandSourceStack, ?> command(
			String prefix, int maxLength, CommandDispatcher<CommandSourceStack> dispatcher, PassCommand passCommand
	) {
		ArgumentBuilder<CommandSourceStack, ?> current = argument("the_rest", StringArgumentType.greedyString()).executes(ctx -> {
			StringBuilder builder = new StringBuilder(StringArgumentType.getString(ctx, prefix + "0"));
			for (int j = 1; j <= maxLength; j++) {
				builder.append(" ").append(StringArgumentType.getString(ctx, prefix + j));
			}
			builder.append(" ").append(StringArgumentType.getString(ctx, "the_rest"));
			return passCommand.run(ctx, builder.toString());
		});
		for (int i = maxLength; i > 0; i--) {
			int argsThusFar = i;
			var newBottom = argument(
					prefix + i, StringArgumentType.string()
			).suggests(getCommandSuggest(i, dispatcher)).executes(ctx -> {
				StringBuilder builder = new StringBuilder(StringArgumentType.getString(ctx, prefix + "0"));
				for (int j = 1; j <= argsThusFar; j++) {
					builder.append(" ").append(StringArgumentType.getString(ctx, prefix + j));
				}
				return passCommand.run(ctx, builder.toString());
			});
			current = newBottom.then(current);
		}
		return argument(prefix + "0", StringArgumentType.string()).suggests(ROOT_COMMAND_SUGGEST).executes(ctx -> {
			return passCommand.run(ctx, StringArgumentType.getString(ctx, prefix + "0"));
		}).then(current);
	}

}
