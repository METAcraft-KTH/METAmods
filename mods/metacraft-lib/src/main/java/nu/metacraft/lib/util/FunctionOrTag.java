package nu.metacraft.lib.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.IdentifierException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import org.jspecify.annotations.NonNull;

import java.util.stream.Stream;

public record FunctionOrTag(Identifier key, Type type) {

	public static final Codec<FunctionOrTag> CODEC = Codec.STRING.comapFlatMap(
			s -> {
				try {
					if (s.startsWith("#")) {
						return DataResult.success(new FunctionOrTag(Identifier.parse(s.substring(1)), Type.TAG));
					} else {
						return DataResult.success(new FunctionOrTag(Identifier.parse(s), Type.FUNCTION));
					}
				} catch (IdentifierException e) {
					return DataResult.error(e::getMessage);
				}
			},
			FunctionOrTag::toString
	);

	public static FunctionOrTag fromArgument(CommandContext<CommandSourceStack> commandContext, String string) throws CommandSyntaxException {
		var arg = FunctionArgument.getFunctionOrTag(commandContext, string);
		return arg.getSecond().map(
				l -> new FunctionOrTag(arg.getFirst(), Type.FUNCTION),
				l -> new FunctionOrTag(arg.getFirst(), Type.TAG)
		);
	}

	public Stream<CommandFunction<CommandSourceStack>> getFunctions(ServerFunctionManager functions) {
		return switch (type) {
			case FUNCTION -> functions.get(key).stream();
			case TAG -> functions.getTag(key).stream();
		};
	}

	public Stream<CommandFunction<CommandSourceStack>> getFunctions(MinecraftServer server) {
		return getFunctions(server.getFunctions());
	}

	@Override
	public @NonNull String toString() {
		if (type == Type.TAG) return "#" + key.toString();
		return key.toString();
	}

	public enum Type {
		FUNCTION,
		TAG
	}

}
