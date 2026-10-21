package se.metacraft.kth_auth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.kth_auth.KTHAuthTask;
import se.metacraft.kth_auth.verify.Verifier;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PlusOneCommand {
	private final Verifier verifier;

	public PlusOneCommand(Verifier verifier) {
		this.verifier = verifier;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("+1")
				.then(
					Commands.argument("name", StringArgumentType.greedyString())
						.suggests(this::suggestUnverifiedPlayers)
						.executes(ctx -> {
							String name = StringArgumentType.getString(ctx, "name");
							return this.run(ctx, name);
						})
				)
		);
	}

	private int run(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		ServerPlayer inviter = ctx.getSource().getPlayerOrException();

		if (!this.verifier.isVerifiedByEmail(inviter.getUUID())) {
			ctx.getSource().sendFailure(Component.translatable("command.kth_auth.plus_one.not_verified_by_email"));
			return 0;
		}

		Optional<KTHAuthTask> toInvite = this.verifier.getUnverifiedPlayers()
			.stream()
			.filter(task -> task.getPlayerName().equalsIgnoreCase(name))
			.findAny();

		if (toInvite.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("command.kth_auth.plus_one.not_on_server", name));
			return 0;
		}

		if (this.verifier.getVerificationDetails(toInvite.get().getUUID()).isPresent()) {
			ctx.getSource().sendFailure(Component.translatable("command.kth_auth.plus_one.already_verified", name));
			return 0;
		}

		this.verifier.verifyPlusOne(toInvite.get().getUUID(), toInvite.get().getPlayerName(), inviter.getUUID(), inviter.getPlainTextName());

		ctx.getSource().sendSuccess(() -> Component.translatable("command.kth_auth.plus_one.success", toInvite.get().getPlayerName()), true);

		return 1;
	}

	private CompletableFuture<Suggestions> suggestUnverifiedPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		String query = builder.getRemainingLowerCase();
		for (KTHAuthTask task : this.verifier.getUnverifiedPlayers()) {
			String name = task.getPlayerName().toLowerCase(Locale.ROOT);
			if (name.startsWith(query)) {
				builder.suggest(task.getPlayerName());
			}
		}
		return builder.buildFuture();
	}
}
