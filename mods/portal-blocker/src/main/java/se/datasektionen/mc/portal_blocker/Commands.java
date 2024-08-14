package se.datasektionen.mc.portal_blocker;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.portal_blocker.portal_type.PortalType;
import se.datasektionen.mc.portal_blocker.portal_type.PortalTypeRegistry;

import java.util.Locale;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	public static final SimpleCommandExceptionType BLOCK_OR_ALLOW = new SimpleCommandExceptionType(
			Text.literal("Please type either block or allow!")
	);

	public static final DynamicCommandExceptionType INVALID_TYPE = new DynamicCommandExceptionType(
			type -> Text.literal(type + " is not a valid type!")
	);

	public static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<ServerCommandSource> rootBuilder = literal("portal-blocker")
					.requires(Permissions.require("metacraft.portal-blocker", 2));
			registerSetGetGlobal(rootBuilder);
			ZoneManagementCommand.registerCommand(rootBuilder, registryAccess);
			dispatcher.register(rootBuilder);
		});
	}

	public static final String PORTAL = "portal";
	public static final String TYPE = "type";

	private static void registerSetGetGlobal(LiteralArgumentBuilder<ServerCommandSource> builder) {
		builder.then(
				literal("set").then(
						PortalType.argument(PORTAL).then(
								boolAllowBlockArgument("state").executes(ctx -> setState(
										ctx, PortalBlockType.BOTH
								))
						).then(
								boolAllowBlockArgument("state").then(
										PortalBlockType.blockTypeArgument(TYPE).executes(ctx -> setState(
												ctx, PortalBlockType.getBoolAllowBlockArgument(ctx, TYPE)
										))
								)
						)
				)
		).then(
				literal("get")
					.executes(Commands::getAllStates)
					.then(
						PortalType.argument(PORTAL)
							.then(
								PortalBlockType.blockTypeArgument(TYPE).executes(
										ctx -> getState(ctx, PortalBlockType.getBoolAllowBlockArgument(ctx, TYPE))
								)
							).executes(ctx -> getState(ctx, PortalBlockType.BOTH))
					)
		);
	}

	private static int setState(CommandContext<ServerCommandSource> context, PortalBlockType blockingType) throws CommandSyntaxException {
		PortalType type = PortalType.getArgument(context, PORTAL);
		boolean stateValue = getBoolAllowBlockArgument(context, "state");

		for (PortalState.BlockingType bType : blockingType.blockingTypes) {
			if (PortalBlockerSettings.getInstance(context.getSource().getServer()).isPortalBlockedGlobally(type, bType) == stateValue) {
				context.getSource().sendFeedback(() -> Text.of(type + " is already " + getBlockStateText(stateValue) + " for " + bType), false);
				continue;
			}

			PortalBlockerSettings.getInstance(context.getSource().getServer()).setPortalBlockedGlobally(type, bType, stateValue);
			context.getSource().sendFeedback(() -> Text.of("Set " + type + " to " + getBlockStateText(stateValue) + " for " + bType), true);
		}
		return 1;
	}

	private static int getState(CommandContext<ServerCommandSource> context, PortalBlockType blockingType) throws CommandSyntaxException {
		PortalType type = PortalType.getArgument(context, PORTAL);
		context.getSource().sendFeedback(
			() -> Text.of(
				type + " is currently " + blockingType.getBlockingString(
					bType -> PortalBlockerSettings.getInstance(context.getSource().getServer()).isPortalBlockedGlobally(type, bType)
				)
			),
			false
		);
		return 1;
	}

	private static int getAllStates(CommandContext<ServerCommandSource> context) {
		PortalTypeRegistry.REGISTRY.stream().forEach(type -> {
			context.getSource().sendFeedback(() -> Text.of(
					type + " is currently " + PortalBlockType.BOTH.getBlockingString(
							bType -> PortalBlockerSettings.getInstance(context.getSource().getServer()).isPortalBlockedGlobally(type, bType)
					)),
					false
			);
		});
		return PortalTypeRegistry.REGISTRY.size();
	}

	public static String getBlockStateText(boolean blockState) {
		return getBlockStateText(blockState, "ed");
	}

	public static String getBlockStateText(boolean blockState, String suffix) {
		return (blockState ? "block" : "allow") + suffix;
	}

	public static RequiredArgumentBuilder<ServerCommandSource, String> boolAllowBlockArgument(String name) {
		return CommandManager.argument(name, StringArgumentType.word()).suggests((context, builder) -> {
			builder.suggest("allow");
			builder.suggest("block");
			return builder.buildFuture();
		});
	}

	public static boolean getBoolAllowBlockArgument(CommandContext<ServerCommandSource> context, String name) throws CommandSyntaxException {
		var type = StringArgumentType.getString(context, name);
		if (type.equals("block")) {
			return true;
		} else if (type.equals("allow")) {
			return false;
		} else {
			throw BLOCK_OR_ALLOW.create();
		}
	}

	public static String getIDAsString(Identifier id) {
		if (id.getNamespace().equals("minecraft")) {
			return id.getPath();
		} else {
			return id.toString();
		}
	}

	public enum PortalBlockType {
		CREATION(PortalState.BlockingType.CREATION),
		TRAVEL(PortalState.BlockingType.TRAVEL),
		BOTH(PortalState.BlockingType.CREATION, PortalState.BlockingType.TRAVEL);

		public final PortalState.BlockingType[] blockingTypes;

		PortalBlockType(PortalState.BlockingType... blockingTypes) {
			this.blockingTypes = blockingTypes;
		}

		public static RequiredArgumentBuilder<ServerCommandSource, String> blockTypeArgument(String name) {
			return CommandManager.argument(name, StringArgumentType.word()).suggests((context, builder) -> {
				for (PortalBlockType type : PortalBlockType.values()) {
					builder.suggest(type.name().toLowerCase(Locale.ROOT));
				}
				return builder.buildFuture();
			});
		}

		@Override
		public String toString() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static PortalBlockType getBoolAllowBlockArgument(CommandContext<ServerCommandSource> context, String name) throws CommandSyntaxException {
			var type = StringArgumentType.getString(context, name);
			try {
				return PortalBlockType.valueOf(type.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw INVALID_TYPE.create(type);
			}
		}

		public String getBlockingString(Function<PortalState.BlockingType, Boolean> valueGetter) {
			return getBlockingString(valueGetter, "ed");
		}

		public String getBlockingString(Function<PortalState.BlockingType, Boolean> valueGetter, String suffix) {
			StringBuilder builder = new StringBuilder();
			for (PortalState.BlockingType bType : blockingTypes) {
				builder.append(getBlockStateText(valueGetter.apply(bType), suffix)).append(" for ").append(bType).append(" and ");
			}
			return builder.substring(0, builder.length()-5);
		}
	}
}
