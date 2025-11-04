package se.metacraft.portalopening;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class Commands {

	private static final SuggestionProvider<CommandSourceStack> WAVES = (ctx, builder) -> {
		return SharedSuggestionProvider.suggest(
				IntStream.iterate(
						0, num -> num < PortalOpening.getConfig().getWaves().size(), num -> num + 1
				).mapToObj(String::valueOf),
				builder
		);
	};

	private static final SuggestionProvider<CommandSourceStack> SPAWN_NAMES = (ctx, builder) -> {
		return SharedSuggestionProvider.suggest(
				PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel()).getCurrentSpawnNames(),
				builder
		);
	};

	private static final SuggestionProvider<CommandSourceStack> X_OR_Z_AXIS = (ctx, builder) -> {
		return SharedSuggestionProvider.suggest(
				ImmutableList.of(Direction.Axis.X.getName(), Direction.Axis.Z.getName()),
				builder
		);
	};

	private static final SuggestionProvider<CommandSourceStack> DIRECTION = (ctx, builder) -> {
		return SharedSuggestionProvider.suggest(
				Stream.concat(Arrays.stream(Direction.values()).map(Direction::getName), Stream.of("null")),
				builder
		);
	};

	private static final SimpleCommandExceptionType AXIS_ERROR = new SimpleCommandExceptionType(
			Component.literal("Invalid Axis")
	);

	private static final DynamicCommandExceptionType RIFT_NOT_FOUND = new DynamicCommandExceptionType(
			object -> Component.literal("Unable to find rift ").append(object.toString())
	);

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("rift").requires(Permissions.require("metacraft.portal_opening.rift", 2)).then(
					literal("raid").then(
						literal("next-wave").executes(ctx -> {
							var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
							data.nextWave();
							data.getWave().ifPresentOrElse(
								wave -> ctx.getSource().sendSuccess(() -> Component.literal("Starting wave " + wave), true),
								() -> ctx.getSource().sendSuccess(() -> Component.literal("Raid is now over"), true)
							);
							return 1;
						})
					).then(
						literal("set-wave").then(
							argument("wave", IntegerArgumentType.integer(0)).suggests(WAVES).executes(ctx -> {
								int waveIndex = IntegerArgumentType.getInteger(ctx, "wave");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
								data.setWave(waveIndex);
								data.getWave().ifPresentOrElse(
										wave -> ctx.getSource().sendSuccess(() -> Component.literal("Starting wave " + wave), true),
										() -> ctx.getSource().sendSuccess(() -> Component.literal("Raid is now over"), true)
								);
								return 1;
							})
						)
					).then(
						literal("spawn").then(
							argument("name", StringArgumentType.string()).suggests(SPAWN_NAMES).executes(ctx -> {
								String name = StringArgumentType.getString(ctx, "name");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
								if (data.isRaidHappening()) {
									if (data.getCurrentSpawnNames().contains(name)) {
										data.spawnFromName(name);
										return 1;
									} else {
										ctx.getSource().sendFailure(Component.literal("That is not a valid spawn name for wave " + data.getWave().getAsInt()));
									}
								} else {
									ctx.getSource().sendFailure(Component.literal("There is no raid going on..."));
								}
								return 0;
							})
						)
					)
				).then(
					literal("reload").executes(ctx -> {
						PortalOpening.reloadConfig();
						ctx.getSource().sendSuccess(() -> Component.literal("Reloaded Portal Opening!"), true);
						return 1;
					})
				).then(
					literal("create").then(
						argument("pos", BlockPosArgument.blockPos()).then(
							argument("size", IntegerArgumentType.integer()).executes(ctx -> {
								var pos = BlockPosArgument.getBlockPos(ctx, "pos");
								boolean createdRift = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel()).createNetherPortalRift(
										pos, IntegerArgumentType.getInteger(ctx, "size"), null
								);
								if (createdRift) {
									ctx.getSource().sendSuccess(() -> Component.literal("Spawned Nether Rift at " + pos), true);
									return 1;
								} else {
									ctx.getSource().sendFailure(Component.literal("Could not spawn Nether Rift at " + pos));
									return 0;
								}
							}).then(
								xOrZAxis("axis").executes(ctx -> {
									var pos = BlockPosArgument.getBlockPos(ctx, "pos");
									boolean createdRift = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel()).createNetherPortalRift(
											pos, IntegerArgumentType.getInteger(ctx, "size"), getXOrZAxis(ctx, "axis")
									);
									if (createdRift) {
										ctx.getSource().sendSuccess(() -> Component.literal("Spawned Nether Rift at " + pos), true);
										return 1;
									} else {
										ctx.getSource().sendFailure(Component.literal("Could not spawn Nether Rift at " + pos));
										return 0;
									}
								})
							)
						)
					)
				).then(
					literal("apocalypse").then(
						argument("players", EntityArgument.players()).then(
							argument("range", IntegerArgumentType.integer(0)).then(
								argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
									var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
									int riftCount = IntegerArgumentType.getInteger(ctx, "amount");
									var random = ctx.getSource().getLevel().getRandom();
									var players = new ArrayList<>(EntityArgument.getPlayers(ctx, "players"));
									int numRifts = 0;
									int range = IntegerArgumentType.getInteger(ctx, "range");
									riftLoop: for (int i = 0; i < riftCount; i++) {
										int size = (int) Math.round(Math.abs(random.nextGaussian() * 10));
										var player = players.get(random.nextInt(players.size()));
										BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos().set(
												player.blockPosition().offset(
														random.nextIntBetweenInclusive(-range, range), 0, random.nextIntBetweenInclusive(-range, range)
												)
										);
										boolean blocked = !player.level().getBlockState(pos).isAir();
										for (int y = 0; y < range; y++) {
											if (blocked) {
												if (
														player.level().isInWorldBounds(pos.setY((int) player.getY() - y)) &&
														player.level().getBlockState(pos).isAir()
												) {
													blocked = false;
												} else if (
														player.level().isInWorldBounds(pos.setY((int) player.getY() + y)) &&
														player.level().getBlockState(pos).isAir()
												) {
													break;
												}
											} else if (
													player.level().isInWorldBounds(pos.setY((int) player.getY() - y)) &&
													player.level().getBlockState(pos).isAir()
											) {
												break;
											} else {
												continue riftLoop;
											}
										}

										pos.setY(pos.getY() + Math.min(size, 5));
										if (data.createNetherPortalRift(pos, size, null)) {
											numRifts++;
										}
									}
									if (numRifts > 1) {
										int finalNumRifts = numRifts;
										ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + finalNumRifts + " rifts"), true);
									} else if (numRifts == 1) {
										ctx.getSource().sendSuccess(() -> Component.literal("Spawned 1 rift"), true);
									} else {
										ctx.getSource().sendFailure(Component.literal("Unable to spawn any rifts"));
									}
									return numRifts;
								})
							)
						)
					)
				).then(
					literal("add").then(
						argument("pos", BlockPosArgument.blockPos()).then(
							argument("size", IntegerArgumentType.integer()).executes(ctx -> {
								BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
								int size = IntegerArgumentType.getInteger(ctx, "size");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
								if (data.addExistingNetherPortalRift(pos, size)) {
									ctx.getSource().sendSuccess(
											() -> Component.literal("Rift added at " + pos + " successfully!"),
											true
									);
									return 1;
								} else {
									ctx.getSource().sendFailure(Component.literal("Unable to detect valid rift."));
									return 0;
								}
							})
						)
					)
				).then(
					literal("add-manual").then(
						argument("pos1", BlockPosArgument.blockPos()).then(
							argument("pos2", BlockPosArgument.blockPos()).executes(ctx -> {
								BlockPos pos1 = BlockPosArgument.getBlockPos(ctx, "pos1");
								BlockPos pos2 = BlockPosArgument.getBlockPos(ctx, "pos2");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
								data.addManualRift(pos1, pos2);
								ctx.getSource().sendSuccess(() -> Component.literal("Added manual rift"), true);
								return 1;
							})
						)
					)
				).then(
					literal("combine").then(
						argument("pos1", BlockPosArgument.blockPos()).then(
							argument("pos2", BlockPosArgument.blockPos()).executes(ctx -> {
								BlockPos pos1 = BlockPosArgument.getBlockPos(ctx, "pos1");
								BlockPos pos2 = BlockPosArgument.getBlockPos(ctx, "pos2");
								var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
								var rift1 = data.getRiftAt(pos1).orElseThrow(() -> RIFT_NOT_FOUND.create(1));
								var rift2 = data.getRiftAt(pos2).orElseThrow(() -> RIFT_NOT_FOUND.create(2));
								if (rift1 == rift2) {
									ctx.getSource().sendFailure(Component.literal("Both rifts were the same rift!"));
									return 0;
								}
								data.combineRifts(rift1, rift2);
								ctx.getSource().sendSuccess(() -> Component.literal("Combined two rifts!"), true);
								return 1;
							})
						)
					)
				).then(
					literal("set-main-rift").then(
						argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
							var pos = BlockPosArgument.getBlockPos(ctx, "pos");
							var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
							if (data.setMainRift(pos)) {
								ctx.getSource().sendSuccess(() -> Component.literal(
										"Set main rift to rift at " + pos
								), true);
								return 1;
							} else {
								ctx.getSource().sendFailure(Component.literal("No rift found at pos!"));
								return 0;
							}
						})
					)
				).then(
					literal("delete").then(
						literal("all").executes(ctx -> {
							var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
							data.closeAllRifts(true);
							ctx.getSource().sendSuccess(() -> Component.literal("Closed all rifts"), true);
							return 1;
						})
					).then(
						literal("all-except-main").executes(ctx -> {
							var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
							data.closeAllRifts(false);
							ctx.getSource().sendSuccess(() -> Component.literal("Closed all rifts except main rift"), true);
							return 1;
						})
					)
				).then(
					literal("set-launch").then(
						argument("pos", BlockPosArgument.blockPos()).then(
							argument("direction", StringArgumentType.string()).suggests(DIRECTION).then(
								argument("strength", DoubleArgumentType.doubleArg(0)).then(
									argument("offset", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
										var directionString = StringArgumentType.getString(ctx, "direction");
										Direction direction;
										if (directionString.equals("null")) {
											direction = null;
										} else {
											direction = Direction.byName(directionString);
										}
										var pos = BlockPosArgument.getBlockPos(ctx, "pos");
										var strength = DoubleArgumentType.getDouble(ctx, "strength");
										var offset = DoubleArgumentType.getDouble(ctx, "offset");
										var data = PortalOpeningDimensionData.getInstance(ctx.getSource().getLevel());
										MutableInt num = new MutableInt(0);
										data.getRiftAt(pos).ifPresentOrElse(rift -> {
											rift.setLaunch(direction, strength, offset);
											ctx.getSource().sendSuccess(
													() -> Component.literal("Set launch to " + direction + " " + strength + " " + offset),
													true
											);
											num.setValue(1);
										}, () -> {
											ctx.getSource().sendFailure(Component.literal("Not a rift"));
										});
										return num.getValue();
									})
								)
							)
						)
					)
				)
			);
		});
	}

	private static Direction.Axis getXOrZAxis(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		var axis = Direction.Axis.byName(StringArgumentType.getString(ctx, name));
		if (axis == null || axis == Direction.Axis.Y) {
			throw AXIS_ERROR.create();
		}
		return axis;
	}

	private static ArgumentBuilder<CommandSourceStack, ?> xOrZAxis(String name) {
		return argument(name, StringArgumentType.word()).suggests(X_OR_Z_AXIS);
	}

}
