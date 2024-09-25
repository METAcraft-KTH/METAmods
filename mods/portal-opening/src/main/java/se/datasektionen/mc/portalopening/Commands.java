package se.datasektionen.mc.portalopening;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.stream.IntStream;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	private static final SuggestionProvider<ServerCommandSource> WAVES = (ctx, builder) -> {
		return CommandSource.suggestMatching(
				IntStream.iterate(
						0, num -> num < PortalOpening.getConfig().getWaves().size(), num -> num + 1
				).mapToObj(String::valueOf),
				builder
		);
	};

	private static final SuggestionProvider<ServerCommandSource> SPAWN_NAMES = (ctx, builder) -> {
		return CommandSource.suggestMatching(
				PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld()).getCurrentSpawnNames(),
				builder
		);
	};

	private static final SuggestionProvider<ServerCommandSource> X_OR_Z_AXIS = (ctx, builder) -> {
		return CommandSource.suggestMatching(
				ImmutableList.of(Direction.Axis.X.getName(), Direction.Axis.Z.getName()),
				builder
		);
	};

	private static final SimpleCommandExceptionType AXIS_ERROR = new SimpleCommandExceptionType(
			Text.literal("Invalid Axis")
	);

	private static final DynamicCommandExceptionType RIFT_NOT_FOUND = new DynamicCommandExceptionType(
			object -> Text.literal("Unable to find rift ").append(object.toString())
	);

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("rift").requires(Permissions.require("se.datasektionen.mc.rift", 2)).then(
					literal("raid").then(
						literal("next-wave").executes(ctx -> {
							var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
							data.nextWave();
							data.getWave().ifPresentOrElse(
								wave -> ctx.getSource().sendFeedback(() -> Text.literal("Starting wave " + wave), true),
								() -> ctx.getSource().sendFeedback(() -> Text.literal("Raid is now over"), true)
							);
							return 1;
						})
					).then(
						literal("set-wave").then(
							argument("wave", IntegerArgumentType.integer(0)).suggests(WAVES).executes(ctx -> {
								int waveIndex = IntegerArgumentType.getInteger(ctx, "wave");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
								data.setWave(waveIndex);
								data.getWave().ifPresentOrElse(
										wave -> ctx.getSource().sendFeedback(() -> Text.literal("Starting wave " + wave), true),
										() -> ctx.getSource().sendFeedback(() -> Text.literal("Raid is now over"), true)
								);
								return 1;
							})
						)
					).then(
						literal("spawn").then(
							argument("name", StringArgumentType.string()).suggests(SPAWN_NAMES).executes(ctx -> {
								String name = StringArgumentType.getString(ctx, "name");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
								if (data.isRaidHappening()) {
									if (data.getCurrentSpawnNames().contains(name)) {
										data.spawnFromName(name);
										return 1;
									} else {
										ctx.getSource().sendError(Text.literal("That is not a valid spawn name for wave " + data.getWave().getAsInt()));
									}
								} else {
									ctx.getSource().sendError(Text.literal("There is no raid going on..."));
								}
								return 0;
							})
						)
					)
				).then(
					literal("reload").executes(ctx -> {
						PortalOpening.reloadConfig();
						ctx.getSource().sendFeedback(() -> Text.literal("Reloaded Portal Opening!"), true);
						return 1;
					})
				).then(
					literal("create").then(
						argument("pos", BlockPosArgumentType.blockPos()).then(
							argument("size", IntegerArgumentType.integer()).executes(ctx -> {
								var pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
								boolean createdRift = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld()).createNetherPortalRift(
										pos, IntegerArgumentType.getInteger(ctx, "size"), null
								);
								if (createdRift) {
									ctx.getSource().sendFeedback(() -> Text.literal("Spawned Nether Rift at " + pos), true);
									return 1;
								} else {
									ctx.getSource().sendError(Text.literal("Could not spawn Nether Rift at " + pos));
									return 0;
								}
							}).then(
								xOrZAxis("axis").executes(ctx -> {
									var pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
									boolean createdRift = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld()).createNetherPortalRift(
											pos, IntegerArgumentType.getInteger(ctx, "size"), getXOrZAxis(ctx, "axis")
									);
									if (createdRift) {
										ctx.getSource().sendFeedback(() -> Text.literal("Spawned Nether Rift at " + pos), true);
										return 1;
									} else {
										ctx.getSource().sendError(Text.literal("Could not spawn Nether Rift at " + pos));
										return 0;
									}
								})
							)
						)
					)
				).then(
					literal("apocalypse").then(
						argument("players", EntityArgumentType.players()).then(
							argument("range", IntegerArgumentType.integer(0)).then(
								argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
									var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
									int riftCount = IntegerArgumentType.getInteger(ctx, "amount");
									var random = ctx.getSource().getWorld().getRandom();
									var players = new ArrayList<>(EntityArgumentType.getPlayers(ctx, "players"));
									int numRifts = 0;
									int range = IntegerArgumentType.getInteger(ctx, "range");
									for (int i = 0; i < riftCount; i++) {
										int size = (int) Math.round(Math.abs(random.nextGaussian() * 10));
										var player = players.get(random.nextInt(players.size()));
										BlockPos.Mutable pos = new BlockPos.Mutable().set(
												player.getBlockPos().add(
														random.nextBetween(-range, range), 0, random.nextBetween(-range, range)
												)
										);
										pos.setY(ctx.getSource().getWorld().getTopY(
												Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()
										) + Math.min(size, 5));
										if (data.createNetherPortalRift(pos, size, null)) {
											numRifts++;
										}
									}
									if (numRifts > 1) {
										int finalNumRifts = numRifts;
										ctx.getSource().sendFeedback(() -> Text.literal("Spawned " + finalNumRifts + " rifts"), true);
									} else if (numRifts == 1) {
										ctx.getSource().sendFeedback(() -> Text.literal("Spawned 1 rift"), true);
									} else {
										ctx.getSource().sendError(Text.literal("Unable to spawn any rifts"));
									}
									return numRifts;
								})
							)
						)
					)
				).then(
					literal("add").then(
						argument("pos", BlockPosArgumentType.blockPos()).then(
							argument("size", IntegerArgumentType.integer()).executes(ctx -> {
								BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
								int size = IntegerArgumentType.getInteger(ctx, "size");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
								if (data.addExistingNetherPortalRift(pos, size)) {
									ctx.getSource().sendFeedback(
											() -> Text.literal("Rift added at " + pos + " successfully!"),
											true
									);
									return 1;
								} else {
									ctx.getSource().sendError(Text.literal("Unable to detect valid rift."));
									return 0;
								}
							})
						)
					)
				).then(
					literal("add-manual").then(
						argument("pos1", BlockPosArgumentType.blockPos()).then(
							argument("pos2", BlockPosArgumentType.blockPos()).executes(ctx -> {
								BlockPos pos1 = BlockPosArgumentType.getBlockPos(ctx, "pos1");
								BlockPos pos2 = BlockPosArgumentType.getBlockPos(ctx, "pos2");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
								data.addManualRift(pos1, pos2);
								ctx.getSource().sendFeedback(() -> Text.literal("Added manual rift"), true);
								return 1;
							})
						)
					)
				).then(
					literal("combine").then(
						argument("pos1", BlockPosArgumentType.blockPos()).then(
							argument("pos2", BlockPosArgumentType.blockPos()).executes(ctx -> {
								BlockPos pos1 = BlockPosArgumentType.getBlockPos(ctx, "pos1");
								BlockPos pos2 = BlockPosArgumentType.getBlockPos(ctx, "pos2");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
								var rift1 = data.getRiftAt(pos1).orElseThrow(() -> RIFT_NOT_FOUND.create(1));
								var rift2 = data.getRiftAt(pos2).orElseThrow(() -> RIFT_NOT_FOUND.create(2));
								if (rift1 == rift2) {
									ctx.getSource().sendError(Text.literal("Both rifts were the same rift!"));
									return 0;
								}
								data.combineRifts(rift1, rift2);
								ctx.getSource().sendFeedback(() -> Text.literal("Combined two rifts!"), true);
								return 1;
							})
						)
					)
				).then(
					literal("set-main-rift").then(
						argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> {
							var pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
							var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
							if (data.setMainRift(pos)) {
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Set main rift to rift at " + pos
								), true);
								return 1;
							} else {
								ctx.getSource().sendError(Text.literal("No rift found at pos!"));
								return 0;
							}
						})
					)
				).then(
					literal("delete").then(
						literal("all").executes(ctx -> {
							var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
							data.closeAllRifts(true);
							ctx.getSource().sendFeedback(() -> Text.literal("Closed all rifts"), true);
							return 1;
						})
					).then(
						literal("all-except-main").executes(ctx -> {
							var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getWorld());
							data.closeAllRifts(false);
							ctx.getSource().sendFeedback(() -> Text.literal("Closed all rifts except main rift"), true);
							return 1;
						})
					)
				)
			);
		});
	}

	private static Direction.Axis getXOrZAxis(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		var axis = Direction.Axis.fromName(StringArgumentType.getString(ctx, name));
		if (axis == null || axis == Direction.Axis.Y) {
			throw AXIS_ERROR.create();
		}
		return axis;
	}

	private static ArgumentBuilder<ServerCommandSource, ?> xOrZAxis(String name) {
		return argument(name, StringArgumentType.word()).suggests(X_OR_Z_AXIS);
	}

}
