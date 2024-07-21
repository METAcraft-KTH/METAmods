package se.datasektionen.mc.metacraft_lib.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ColumnPosArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColumnPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ExplorerMapCommand {

	private static final DynamicCommandExceptionType INVALID_MAP_DECORATION_TYPE = new DynamicCommandExceptionType(
			o -> Text.literal( o + " is not a valid map decoration type!")
	);

	private static final SuggestionProvider<ServerCommandSource> MAP_DECORATION_TYPES = (ctx, builder) -> {
		return CommandSource.suggestMatching(
				Registries.MAP_DECORATION_TYPE.getIds().stream().map(
						id -> id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()
				), builder
		);
	};

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
				literal("explorer-map").requires(Permissions.require("metacraft.command.explorer_map", 2)).then(
						literal("create").requires(Permissions.require("metacraft.command.explorer_map.create", 2)).then(
								argument("target", ColumnPosArgumentType.columnPos()).then(
										mapDecorationType("decoration").executes(ctx -> {
											return createMap(
													ctx, ColumnPosArgumentType.getColumnPos(ctx, "target"),
													getMapDecorationType(ctx, "decoration"), (byte) 2, true, true,
													true, "+"
											);
										}).then(
												argument("scale", IntegerArgumentType.integer(0, 4)).executes(ctx -> {
													return createMap(
															ctx, ColumnPosArgumentType.getColumnPos(ctx, "target"),
															getMapDecorationType(ctx, "decoration"),
															(byte) IntegerArgumentType.getInteger(ctx, "scale"),
															true, true, true, "+"
													);
												}).then(
														argument("draw-biomes", BoolArgumentType.bool()).executes(ctx -> {
															return createMap(
																	ctx, ColumnPosArgumentType.getColumnPos(ctx, "target"),
																	getMapDecorationType(ctx, "decoration"),
																	(byte) IntegerArgumentType.getInteger(ctx, "scale"),
																	BoolArgumentType.getBool(ctx, "draw-biomes"),
																	true, true, "+"
															);
														}).then(
																argument("show-icons", BoolArgumentType.bool()).executes(ctx -> {
																	return createMap(
																			ctx, ColumnPosArgumentType.getColumnPos(ctx, "target"),
																			getMapDecorationType(ctx, "decoration"),
																			(byte) IntegerArgumentType.getInteger(ctx, "scale"),
																			BoolArgumentType.getBool(ctx, "draw-biomes"),
																			BoolArgumentType.getBool(ctx, "show-icons"),
																			true, "+"
																	);
																}).then(
																		argument("unlimited-tracking", BoolArgumentType.bool()).executes(ctx -> {
																			return createMap(
																					ctx, ColumnPosArgumentType.getColumnPos(ctx, "target"),
																					getMapDecorationType(ctx, "decoration"),
																					(byte) IntegerArgumentType.getInteger(ctx, "scale"),
																					BoolArgumentType.getBool(ctx, "draw-biomes"),
																					BoolArgumentType.getBool(ctx, "show-icons"),
																					BoolArgumentType.getBool(ctx, "unlimited-tracking"), "+"
																			);
																		}).then(
																				argument("id", StringArgumentType.string()).executes(ctx -> {
																					return createMap(
																							ctx, ColumnPosArgumentType.getColumnPos(ctx, "target"),
																							getMapDecorationType(ctx, "decoration"),
																							(byte) IntegerArgumentType.getInteger(ctx, "scale"),
																							BoolArgumentType.getBool(ctx, "draw-biomes"),
																							BoolArgumentType.getBool(ctx, "show-icons"),
																							BoolArgumentType.getBool(ctx, "unlimited-tracking"),
																							StringArgumentType.getString(ctx, "id")
																					);
																				})
																		)
																)
														)
												)
										)
								)
						)
				).then(
						literal("add").then(
								argument("target", ColumnPosArgumentType.columnPos()).then(
										mapDecorationType("decoration").executes(ctx -> {
											return addToMap(
													ctx, ctx.getSource().getPlayerOrThrow().getMainHandStack(),
													ColumnPosArgumentType.getColumnPos(ctx, "target"),
													getMapDecorationType(ctx, "decoration")
											);
										})
								)
						)
				)
		);
	}

	private static ArgumentBuilder<ServerCommandSource, ?> mapDecorationType(String name) {
		return argument(name, IdentifierArgumentType.identifier()).suggests(MAP_DECORATION_TYPES);
	}

	private static RegistryEntry<MapDecorationType> getMapDecorationType(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		var id = IdentifierArgumentType.getIdentifier(ctx, name);
		try {
			return Registries.MAP_DECORATION_TYPE.entryOf(RegistryKey.of(
					RegistryKeys.MAP_DECORATION_TYPE, id
			));
		} catch (IllegalStateException ignored) {
			throw INVALID_MAP_DECORATION_TYPE.create(id);
		}
	}
	private static int addToMap(
			CommandContext<ServerCommandSource> ctx, ItemStack stack,
			ColumnPos pos, RegistryEntry<MapDecorationType> symbol
	) {
		String toInsert = "+";
		if (stack.contains(DataComponentTypes.MAP_DECORATIONS)) {
			var decorations = stack.get(DataComponentTypes.MAP_DECORATIONS);
			while (decorations.decorations().containsKey(toInsert)) {
				toInsert += "+";
			}
		}
		return addToMap(ctx, stack, pos, symbol, toInsert);
	}


	private static int addToMap(
			CommandContext<ServerCommandSource> ctx, ItemStack stack,
			ColumnPos pos, RegistryEntry<MapDecorationType> symbol, String id
	) {
		if (!stack.isOf(Items.FILLED_MAP)) {
			ctx.getSource().sendError(Text.literal("Not a map!"));
			return 0;
		}
		MapState.addDecorationsNbt(stack, new BlockPos(pos.x(), 0, pos.z()), id, symbol);
		return 1;
	}

	private static int createMap(
			CommandContext<ServerCommandSource> ctx, ColumnPos pos, RegistryEntry<MapDecorationType> symbol,
			byte scale, boolean drawBiomes, boolean showIcons, boolean unlimitedTracking, String id
	) throws CommandSyntaxException {
		var player = ctx.getSource().getPlayerOrThrow();

		ctx.getSource().getServer().execute(() -> {
			ItemStack map = FilledMapItem.createMap(ctx.getSource().getWorld(), pos.x(), pos.z(), scale, showIcons, unlimitedTracking);
			if (drawBiomes) {
				FilledMapItem.fillExplorationMap(ctx.getSource().getWorld(), map);
			}
			MapState.addDecorationsNbt(map, new BlockPos(pos.x(), 0, pos.z()), id, symbol);
			if (!player.getInventory().insertStack(map) && !map.isEmpty()) {
				var item = player.dropItem(map, false, false);
				if (item != null) {
					item.resetPickupDelay();
					item.setOwner(player.getUuid());
				}
			}
			player.sendMessage(Text.literal("If the map is empty, drop it on the ground and pick it up again."), true);
		});

		return 1;
	}

}
