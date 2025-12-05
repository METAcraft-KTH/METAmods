package nu.metacraft.portal_blocker;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import nu.metacraft.portal_blocker.portal_type.PortalType;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import nu.metacraft.portal_blocker.zone.PortalZoneData;

import java.util.Locale;
import java.util.function.Function;

import static net.minecraft.commands.Commands.literal;

public class Commands {

	public static final SimpleCommandExceptionType BLOCK_OR_ALLOW = new SimpleCommandExceptionType(
			Component.literal("Please type either block or allow!")
	);

	public static final DynamicCommandExceptionType INVALID_TYPE = new DynamicCommandExceptionType(
			type -> Component.literal(type + " is not a valid type!")
	);

	public static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> rootBuilder = literal("portal-blocker")
					.requires(Permissions.require("metacraft.portal-blocker", 2));
			registerSetGetGlobal(rootBuilder);
			registerSetGetBlockOutsideBorder(rootBuilder);
			ZoneManagementCommand.registerCommand(rootBuilder, registryAccess);
			dispatcher.register(rootBuilder);
		});
	}

	public static final String PORTAL = "portal";
	public static final String TYPE = "type";

	private static void registerSetGetGlobal(LiteralArgumentBuilder<CommandSourceStack> builder) {
		builder.then(
				literal("set").then(
						PortalType.argument(PORTAL).then(
								allowBlockArgument("state", false).executes(ctx -> setState(
										ctx, PortalBlockType.ALL
								))
						).then(
								allowBlockArgument("state", false).then(
										PortalBlockType.blockTypeArgument(TYPE).executes(ctx -> setState(
												ctx, PortalBlockType.getAllowBlockArgument(ctx, TYPE)
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
										ctx -> getState(ctx, PortalBlockType.getAllowBlockArgument(ctx, TYPE))
								)
							).executes(ctx -> getState(ctx, PortalBlockType.ALL))
					)
		);
	}

	private static void registerSetGetBlockOutsideBorder(LiteralArgumentBuilder<CommandSourceStack> builder) {
		builder.then(
				literal("block-outside-border")
						.then(
							literal("set")
									.then(
										literal("block").executes(ctx -> {
												PortalBlockerSettings.getInstance(ctx.getSource().getServer()).setBlockPortalCreationOutsideBorder(true);
												ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Portal creation outside world border is now blocked."), true);
												return 1;
										})
									)
									.then(
											literal("allow").executes(ctx -> {
													PortalBlockerSettings.getInstance(ctx.getSource().getServer()).setBlockPortalCreationOutsideBorder(false);
													ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Portal creation outside world border is now allowed."), true);
													return 1;
											})
									)
						)
						.then(
								literal("get").executes(ctx -> {
										boolean isBlocked = PortalBlockerSettings.getInstance(ctx.getSource().getServer()).blockPortalCreationOutsideBorder();
										ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Portal creation outside world border is currently " + getBlockStateText(isBlocked) + "."), false);
										return 1;
								})
						)
		);
	}

	private static int setState(CommandContext<CommandSourceStack> context, PortalBlockType blockingType) throws CommandSyntaxException {
		PortalType type = PortalType.getArgument(context, PORTAL);
		boolean stateValue = getAllowBlockArgument(context, "state", false) == PortalZoneData.BlockResult.BLOCKED;

		for (PortalState.BlockingType bType : blockingType.blockingTypes) {
			if (PortalBlockerSettings.getInstance(context.getSource().getServer()).isPortalBlockedGlobally(type, bType) == stateValue) {
				context.getSource().sendSuccess(() -> Component.nullToEmpty(type + " is already " + getBlockStateText(stateValue) + " for " + bType), false);
				continue;
			}

			PortalBlockerSettings.getInstance(context.getSource().getServer()).setPortalBlockedGlobally(type, bType, stateValue);
			context.getSource().sendSuccess(() -> Component.nullToEmpty("Set " + type + " to " + getBlockStateText(stateValue) + " for " + bType), true);
		}
		return 1;
	}

	private static int getState(CommandContext<CommandSourceStack> context, PortalBlockType blockingType) throws CommandSyntaxException {
		PortalType type = PortalType.getArgument(context, PORTAL);
		context.getSource().sendSuccess(
			() -> Component.nullToEmpty(
				type + " is currently " + blockingType.getBlockingString(
					bType -> PortalBlockerSettings.getInstance(context.getSource().getServer()).isPortalBlockedGlobally(type, bType)
				)
			),
			false
		);
		return 1;
	}

	private static int getAllStates(CommandContext<CommandSourceStack> context) {
		PortalTypeRegistry.REGISTRY.stream().forEach(type -> {
			context.getSource().sendSuccess(() -> Component.nullToEmpty(
					type + " is currently " + PortalBlockType.ALL.getBlockingString(
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

	public static RequiredArgumentBuilder<CommandSourceStack, String> allowBlockArgument(String name, boolean includeDefault) {
		return net.minecraft.commands.Commands.argument(name, StringArgumentType.word()).suggests((context, builder) -> {
			for (var key : PortalZoneData.BlockResult.values()) {
				if (key == PortalZoneData.BlockResult.DEFAULT && !includeDefault) continue;
				builder.suggest(key.getSerializedName());
			}
			return builder.buildFuture();
		});
	}

	public static PortalZoneData.BlockResult getAllowBlockArgument(CommandContext<CommandSourceStack> context, String name, boolean allowDefault) throws CommandSyntaxException {
		var type = StringArgumentType.getString(context, name);
		var result = PortalZoneData.BlockResult.fromString(type).orElseThrow(BLOCK_OR_ALLOW::create);
		if (!allowDefault && result == PortalZoneData.BlockResult.DEFAULT) throw BLOCK_OR_ALLOW.create();
		return result;
	}

	public static String getIDAsString(Identifier id) {
		if (id.getNamespace().equals("minecraft")) {
			return id.getPath();
		} else {
			return id.toString();
		}
	}

	public enum PortalBlockType {
		ACTIVATION(PortalState.BlockingType.ACTIVATION),
		TRAVEL(PortalState.BlockingType.TRAVEL),
		GENERATION(PortalState.BlockingType.GENERATION),
		CREATION(PortalState.BlockingType.GENERATION, PortalState.BlockingType.ACTIVATION),
		ALL(PortalState.BlockingType.GENERATION, PortalState.BlockingType.ACTIVATION, PortalState.BlockingType.TRAVEL);

		public final PortalState.BlockingType[] blockingTypes;

		PortalBlockType(PortalState.BlockingType... blockingTypes) {
			this.blockingTypes = blockingTypes;
		}

		public static RequiredArgumentBuilder<CommandSourceStack, String> blockTypeArgument(String name) {
			return net.minecraft.commands.Commands.argument(name, StringArgumentType.word()).suggests((context, builder) -> {
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

		public static PortalBlockType getAllowBlockArgument(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
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
