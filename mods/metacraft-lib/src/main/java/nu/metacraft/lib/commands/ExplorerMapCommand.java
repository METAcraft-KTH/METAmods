package nu.metacraft.lib.commands;

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
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ExplorerMapCommand {

	private static final DynamicCommandExceptionType INVALID_MAP_DECORATION_TYPE = new DynamicCommandExceptionType(
			o -> Component.literal( o + " is not a valid map decoration type!")
	);

	private static final SuggestionProvider<CommandSourceStack> MAP_DECORATION_TYPES = (ctx, builder) -> {
		return SharedSuggestionProvider.suggest(
				BuiltInRegistries.MAP_DECORATION_TYPE.keySet().stream().map(
						id -> id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()
				), builder
		);
	};

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
				literal("explorer-map").requires(Permissions.require("metacraft.explorer-map", 2)).then(
						literal("create").requires(Permissions.require("metacraft.explorer-map.create", 2)).then(
								argument("target", ColumnPosArgument.columnPos()).then(
										mapDecorationType("decoration").executes(ctx -> {
											return createMap(
													ctx, ColumnPosArgument.getColumnPos(ctx, "target"),
													getMapDecorationType(ctx, "decoration"), (byte) 2, true, true,
													true, "+"
											);
										}).then(
												argument("scale", IntegerArgumentType.integer(0, 4)).executes(ctx -> {
													return createMap(
															ctx, ColumnPosArgument.getColumnPos(ctx, "target"),
															getMapDecorationType(ctx, "decoration"),
															(byte) IntegerArgumentType.getInteger(ctx, "scale"),
															true, true, true, "+"
													);
												}).then(
														argument("draw-biomes", BoolArgumentType.bool()).executes(ctx -> {
															return createMap(
																	ctx, ColumnPosArgument.getColumnPos(ctx, "target"),
																	getMapDecorationType(ctx, "decoration"),
																	(byte) IntegerArgumentType.getInteger(ctx, "scale"),
																	BoolArgumentType.getBool(ctx, "draw-biomes"),
																	true, true, "+"
															);
														}).then(
																argument("show-icons", BoolArgumentType.bool()).executes(ctx -> {
																	return createMap(
																			ctx, ColumnPosArgument.getColumnPos(ctx, "target"),
																			getMapDecorationType(ctx, "decoration"),
																			(byte) IntegerArgumentType.getInteger(ctx, "scale"),
																			BoolArgumentType.getBool(ctx, "draw-biomes"),
																			BoolArgumentType.getBool(ctx, "show-icons"),
																			true, "+"
																	);
																}).then(
																		argument("unlimited-tracking", BoolArgumentType.bool()).executes(ctx -> {
																			return createMap(
																					ctx, ColumnPosArgument.getColumnPos(ctx, "target"),
																					getMapDecorationType(ctx, "decoration"),
																					(byte) IntegerArgumentType.getInteger(ctx, "scale"),
																					BoolArgumentType.getBool(ctx, "draw-biomes"),
																					BoolArgumentType.getBool(ctx, "show-icons"),
																					BoolArgumentType.getBool(ctx, "unlimited-tracking"), "+"
																			);
																		}).then(
																				argument("id", StringArgumentType.string()).executes(ctx -> {
																					return createMap(
																							ctx, ColumnPosArgument.getColumnPos(ctx, "target"),
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
								argument("target", ColumnPosArgument.columnPos()).then(
										mapDecorationType("decoration").executes(ctx -> {
											return addToMap(
													ctx, ctx.getSource().getPlayerOrException().getMainHandItem(),
													ColumnPosArgument.getColumnPos(ctx, "target"),
													getMapDecorationType(ctx, "decoration")
											);
										})
								)
						)
				)
		);
	}

	private static ArgumentBuilder<CommandSourceStack, ?> mapDecorationType(String name) {
		return argument(name, ResourceLocationArgument.id()).suggests(MAP_DECORATION_TYPES);
	}

	private static Holder<MapDecorationType> getMapDecorationType(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		var id = ResourceLocationArgument.getId(ctx, name);
		return BuiltInRegistries.MAP_DECORATION_TYPE.get(id).orElseThrow(
				() -> INVALID_MAP_DECORATION_TYPE.create(id)
		);
	}
	private static int addToMap(
			CommandContext<CommandSourceStack> ctx, ItemStack stack,
			ColumnPos pos, Holder<MapDecorationType> symbol
	) {
		String toInsert = "+";
		if (stack.has(DataComponents.MAP_DECORATIONS)) {
			var decorations = stack.get(DataComponents.MAP_DECORATIONS);
			while (decorations.decorations().containsKey(toInsert)) {
				toInsert += "+";
			}
		}
		return addToMap(ctx, stack, pos, symbol, toInsert);
	}


	private static int addToMap(
			CommandContext<CommandSourceStack> ctx, ItemStack stack,
			ColumnPos pos, Holder<MapDecorationType> symbol, String id
	) {
		if (!stack.is(Items.FILLED_MAP)) {
			ctx.getSource().sendFailure(Component.literal("Not a map!"));
			return 0;
		}
		MapItemSavedData.addTargetDecoration(stack, new BlockPos(pos.x(), 0, pos.z()), id, symbol);
		return 1;
	}

	private static int createMap(
			CommandContext<CommandSourceStack> ctx, ColumnPos pos, Holder<MapDecorationType> symbol,
			byte scale, boolean drawBiomes, boolean showIcons, boolean unlimitedTracking, String id
	) throws CommandSyntaxException {
		var player = ctx.getSource().getPlayerOrException();

		ctx.getSource().getServer().execute(() -> {
			ItemStack map = MapItem.create(ctx.getSource().getLevel(), pos.x(), pos.z(), scale, showIcons, unlimitedTracking);
			if (drawBiomes) {
				MapItem.renderBiomePreviewMap(ctx.getSource().getLevel(), map);
			}
			MapItemSavedData.addTargetDecoration(map, new BlockPos(pos.x(), 0, pos.z()), id, symbol);
			if (!player.getInventory().add(map) && !map.isEmpty()) {
				var item = player.drop(map, false, false);
				if (item != null) {
					item.setNoPickUpDelay();
					item.setTarget(player.getUUID());
				}
			}
			player.displayClientMessage(Component.literal("If the map is empty, drop it on the ground and pick it up again."), true);
		});

		return 1;
	}

}
