package metacraft.pirkko;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.JsonOps;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Commands for handing out the shader-rendered (player head) Pirkkos. */
public final class PirkkoCommands {
	/** Skin names under {@code assets/pirkko/textures/entity}; wall heads get their {@code _wall} twin automatically. */
	public static final List<String> VARIANTS = List.of(
			"pirkko_marker", "marker_green", "marker_cerise", "marker_laserviolett", "marker_rainbow");

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pirkko")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("give")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("variant", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(VARIANTS, builder))
										.executes(ctx -> give(ctx.getSource(),
												EntityArgument.getPlayers(ctx, "targets"),
												StringArgumentType.getString(ctx, "variant")))))));
	}

	private static int give(CommandSourceStack source, Collection<ServerPlayer> targets, String variant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!VARIANTS.contains(variant)) {
			throw new SimpleCommandExceptionType(Component.literal("Unknown variant '" + variant + "'; one of " + VARIANTS)).create();
		}
		var json = new JsonObject();
		json.addProperty("texture", "pirkko:entity/" + variant);
		var profile = ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);
		for (var player : targets) {
			var stack = new ItemStack(Items.PLAYER_HEAD);
			stack.set(DataComponents.PROFILE, profile);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal("Pirkko (" + variant + ")"));
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
		source.sendSuccess(() -> Component.literal("Gave " + variant + " to " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private PirkkoCommands() {}
}
