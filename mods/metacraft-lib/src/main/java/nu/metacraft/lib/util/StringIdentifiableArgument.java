package nu.metacraft.lib.util;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.Keyable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import static net.minecraft.commands.Commands.argument;

public class StringIdentifiableArgument {

	private static final Dynamic2CommandExceptionType INVALID_KEY = new Dynamic2CommandExceptionType(
			(value, name) -> Component.literal(value + " is not a valid value for " + name + "!")
	);

	private static SuggestionProvider<CommandSourceStack> getSuggestions(Keyable keys) {
		return (ctx, builder) -> {
			return SharedSuggestionProvider.suggest(
				keys.keys(JavaOps.INSTANCE).map(Object::toString), builder
			);
		};
	}


	public static ArgumentBuilder<CommandSourceStack, ?> stringIdentifiable(String name, Keyable keys) {
		return argument(name, StringArgumentType.word()).suggests(getSuggestions(keys));
	}

	public static <T extends StringRepresentable> T getStringIdentifiable(
			CommandContext<CommandSourceStack> ctx, String name, Codec<T> codec
	) throws CommandSyntaxException {
		var key = StringArgumentType.getString(ctx, name);
		return codec.parse(JavaOps.INSTANCE, key).getOrThrow(
				err -> INVALID_KEY.create(key, name)
		);
	}

}
