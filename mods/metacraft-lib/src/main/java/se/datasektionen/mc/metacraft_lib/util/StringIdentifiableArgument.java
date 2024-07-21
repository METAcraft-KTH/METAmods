package se.datasektionen.mc.metacraft_lib.util;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.Keyable;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;

import static net.minecraft.server.command.CommandManager.argument;

public class StringIdentifiableArgument {

	private static final Dynamic2CommandExceptionType INVALID_KEY = new Dynamic2CommandExceptionType(
			(value, name) -> Text.literal(value + " is not a valid value for " + name + "!")
	);

	private static SuggestionProvider<ServerCommandSource> getSuggestions(Keyable keys) {
		return (ctx, builder) -> {
			return CommandSource.suggestMatching(
				keys.keys(JavaOps.INSTANCE).map(Object::toString), builder
			);
		};
	}


	public static ArgumentBuilder<ServerCommandSource, ?> stringIdentifiable(String name, Keyable keys) {
		return argument(name, StringArgumentType.word()).suggests(getSuggestions(keys));
	}

	public static <T extends StringIdentifiable> T getStringIdentifiable(
			CommandContext<ServerCommandSource> ctx, String name, Codec<T> codec
	) throws CommandSyntaxException {
		var key = StringArgumentType.getString(ctx, name);
		return codec.parse(JavaOps.INSTANCE, key).getOrThrow(
				err -> INVALID_KEY.create(key, name)
		);
	}

}
