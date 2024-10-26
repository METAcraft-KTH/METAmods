package se.datasektionen.mc.zones;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.command.argument.RegistryPredicateArgumentType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.predicate.entity.EntityTypePredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.mutable.MutableInt;
import se.datasektionen.mc.zones.mixin.AccessorStringRange;
import se.datasektionen.mc.zones.mixin.AccessorSuggestion;
import se.datasektionen.mc.zones.spawns.BetterSpawnEntry;
import se.datasektionen.mc.zones.spawns.SpawnRemoverRegistry;
import se.datasektionen.mc.zones.util.ZoneCommandUtils;
import se.datasektionen.mc.zones.zone.RealZone;
import se.datasektionen.mc.zones.zone.ZoneRegistry;
import se.datasektionen.mc.zones.zone.data.AdditionalSpawnsZoneData;
import se.datasektionen.mc.zones.zone.data.MessageZoneData;
import se.datasektionen.mc.zones.zone.data.ZoneDataRegistry;
import se.datasektionen.mc.zones.zone.types.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ZoneManagementCommand {

	private static final Map<String, ZoneEntry> zonesToRemove = new ConcurrentHashMap<>();

	public static final String NAME = "name";

	public static final SimpleCommandExceptionType ZONE_ALREADY_EXISTS = new SimpleCommandExceptionType(
			Text.literal("Another zone already exists with that name.")
	);

	public static final SimpleCommandExceptionType INVALID_SPAWN_GROUP = new SimpleCommandExceptionType(
			Text.literal("Invalid Spawn Group")
	);

	public static final SuggestionProvider<ServerCommandSource> SUGGEST_SPAWN_GROUP = (ctx, builder) -> {
		return CommandSource.suggestMatching(StringIdentifiable.toKeyable(SpawnGroup.values()).keys(NbtOps.INSTANCE).map(NbtElement::asString), builder);
	};

	private static final DynamicCommandExceptionType ENTITY_FAIL = new DynamicCommandExceptionType(id -> Text.literal(id + " is not a valid entity or entity tag!"));

	private static final Dynamic2CommandExceptionType CONTAINS_FAIL = new Dynamic2CommandExceptionType((pos, zone) -> Text.literal(pos + " is not inside " + zone));

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			zonesToRemove.values().removeIf(
					entry -> entry.timeLeft().decrementAndGet() <= 0
			);
		});
	}

	private record ZoneEntry(String name, MutableInt timeLeft) {}

	private static int contains(CommandContext<ServerCommandSource> ctx, BlockPos pos) throws CommandSyntaxException {
		var zone = getZone(ctx);
		if (zone.contains(pos)) {
			ctx.getSource().sendFeedback(
					() -> Text.literal(pos.toShortString() + " is indeed inside " + zone.getName()),
					false
			);
			return 1;
		} else {
			throw CONTAINS_FAIL.create(pos.toShortString(), zone.getName());
		}
	}

	static void registerCommand(
			LiteralArgumentBuilder<ServerCommandSource> builder, CommandRegistryAccess registryAccess,
			CommandDispatcher<ServerCommandSource> dispatcher
	) {
		builder.then(
			literal("create").then(
				createCreateCommandsFromRegistry(argument(NAME, StringArgumentType.string()), registryAccess)
			)
		).then(
			literal("extend").then(
				getZoneCreator(zone(), registryAccess, (zoneCreator, ctx) -> {
					var zone = getZone(ctx);
					var newZone = zoneCreator.create();
					if (zone.getZone() instanceof UnionZone union) {
						union.addZone(newZone);
					} else {
						ZoneType prevZone = zone.getZone();
						zone.setZone(new UnionZone(prevZone, newZone));
					}
					ctx.getSource().sendFeedback(
							() -> Text.literal("Expanded zone " + zone.getName() + " to include " + newZone),
							true
					);
					return 1;
				})
			)
		).then(
			literal("restrict-to").then(
				getZoneCreator(zone(), registryAccess, (zoneCreator, ctx) -> {
					var zone = getZone(ctx);
					var newZone = zoneCreator.create();
					if (zone.getZone() instanceof IntersectZone intersect) {
						intersect.addZone(newZone);
					} else {
						ZoneType prevZone = zone.getZone();
						zone.setZone(new IntersectZone(prevZone, newZone));
					}
					ctx.getSource().sendFeedback(
							() -> Text.literal("Limited zone " + zone.getName() + " to " + newZone),
							true
					);
					return 1;
				})
			)
		).then(
			literal("contains").then(
				zone().executes(ctx -> contains(ctx, BlockPos.ofFloored(ctx.getSource().getPosition()))).then(
					argument("pos", BlockPosArgumentType.blockPos()).executes(
						ctx -> contains(ctx, BlockPosArgumentType.getBlockPos(ctx, "pos"))
					)
				)
			)
		).then(
			literal("replace").then(
				getZoneCreator(zone(), registryAccess, (zoneCreator, ctx) -> {
					var zone = getZone(ctx);
					var newZone = zoneCreator.create();
					zone.setZone(newZone);
					ctx.getSource().sendFeedback(
							() -> Text.literal("Replaced zone shape of " + zone.getName() + " with " + newZone),
							true
					);
					return 1;
				})
			)
		).then(
			literal("remove-shape").then(
				zone().then(
					argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
						var zone = getZone(ctx);
						int index = IntegerArgumentType.getInteger(ctx, "index");

						if (zone.getZone() instanceof CombinedZone combined) {
							if (combined.zoneCount() > index) {
								var removed = combined.removeZone(index);
								ctx.getSource().sendFeedback(
										() -> Text.literal("Removed shape " + removed + " from " + zone.getName()),
										true
								);
								if (combined.zoneCount() == 1) {
									zone.setZone(combined.getZone(0));
								}
								return 1;
							} else {
								ctx.getSource().sendError(Text.literal(
										"Index too high"
								));
							}
						} else {
							ctx.getSource().sendError(Text.literal(
									"Not a combined zone"
							));
						}

						return 0;
					})
				).then(
					literal("all-except").then(
						argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
							var zone = getZone(ctx);
							int index = IntegerArgumentType.getInteger(ctx, "index");

							if (zone.getZone() instanceof CombinedZone combined) {
								if (combined.zoneCount() > index) {
									var toKeep = combined.getZone(index);
									zone.setZone(toKeep);
									ctx.getSource().sendFeedback(
											() -> Text.literal("Set shape to " + toKeep + " for " + zone.getName()),
											true
									);
									return combined.zoneCount()-1;
								} else {
									ctx.getSource().sendError(Text.literal(
											"Index too high"
									));
								}
							} else {
								ctx.getSource().sendError(Text.literal(
										"Not a combined zone"
								));
							}

							return 0;
						})
					)
				)
			)
		).then(
			literal("negate").then(
				zone().executes(ctx -> {
					var zone = getZone(ctx);
					if (zone.getZone() instanceof NegateZone negate) {
						zone.setZone(negate.getZone());
					} else {
						zone.setZone(new NegateZone(zone.getZone()));
					}
					ctx.getSource().sendFeedback(() -> Text.literal("Negated " + zone.getName()), true);
					return 1;
				})
			)
		).then(
			literal("set-priority").then(
				zone().then(
					argument("priority", IntegerArgumentType.integer()).executes(ctx -> {
						RealZone zone = getZone(ctx);
						int priority = IntegerArgumentType.getInteger(ctx, "priority");
						zone.setPriority(priority);
						getSettings(ctx).updatePriority(zone);
						ctx.getSource().sendFeedback(
								() -> Text.literal("Set zone priority for " + zone.getName() + " to " + priority),
								true
						);
						return 1;
					})
				)
			)
		).then(
			ZoneCommandUtils.queryZoneMulti(literal("get"), zone -> {
				return ImmutableList.of(
					Text.literal("Name: " + zone.getName()),
					Text.literal("Dimension: " + zone.getDim().getValue()),
					Text.literal("Zone: " + zone.getZone()),
					Text.literal("Priority: " + zone.getPriority()),
					Text.literal("AdditionalDimensions: " + zone.getRemoteZones().stream().map(type -> {
						return type.getDim().getValue().toString();
					}).collect(Collectors.joining(", "))),
					Text.literal("Data: ").append(
						join(zone.getAllData().stream().map(data -> Text.literal(" ").append(data.toText(zone.getWorld().getRegistryManager()))).iterator(), Text.literal(",\n"))
					)
				);
			})
		).then(
			literal("dimension").then(
				addRemoveSubDim(literal("add"), true)
			).then(
				addRemoveSubDim(literal("remove"), false)
			)
		).then(
			literal("remove").then(
				zone().executes(ctx -> {
					String name = StringArgumentType.getString(ctx, NAME);
					var settings = getSettings(ctx);
					if (settings.containsZone(name)) {
						zonesToRemove.put(ctx.getSource().getName(), new ZoneEntry(name, new MutableInt(60*20)));
						ctx.getSource().sendFeedback(
								() -> Text.literal("Are you sure you want to remove " + name + "?"),
								false
						);
						ctx.getSource().sendFeedback(
								() -> Text.literal("If yes, please type ").append(
										Text.literal("/zone confirm-remove " + name).fillStyle(
												Style.EMPTY.withColor(Formatting.RED).withClickEvent(new ClickEvent(
														ClickEvent.Action.SUGGEST_COMMAND,
														"/zone confirm-remove " + name
												))
										)
								),
								false
						);
						return 1;
					} else {
						ctx.getSource().sendFeedback(
								() -> Text.literal("No zone with " + name + " exists!"),
								false
						);
						return 0;
					}
				})
			)
		).then(
			literal("confirm-remove").then(
				argument(NAME, StringArgumentType.string()).executes(ctx -> {
					String name = StringArgumentType.getString(ctx, NAME);
					var toRemove = zonesToRemove.get(ctx.getSource().getName());
					if (toRemove == null) {
						ctx.getSource().sendError(
							Text.literal("Please type ").append(
								Text.literal("/zone remove " + name).fillStyle(
									Style.EMPTY.withColor(Formatting.YELLOW)
											.withClickEvent(new ClickEvent(
												ClickEvent.Action.SUGGEST_COMMAND,
												"/zone remove " + name
											))
								)
							)
						);
						return 0;
					}

					if (toRemove.name.equals(name)) {
						if (getSettings(ctx).removeZone(name)) {
							ctx.getSource().sendFeedback(
									() -> Text.literal("Removed " + name),
									true
							);
							return 1;
						} else {
							ctx.getSource().sendFeedback(
									() -> Text.literal("No zone with " + name + " exists!"),
									false
							);
							return 0;
						}
					} else {
						ctx.getSource().sendError(
							Text.literal("Are you sure you're trying to remove the right zone? Because you specified another zone in the remove command...")
						);
					}
					return 0;
				})
			)
		).then(
			literal("list").executes(ctx -> {
				if (getSettings(ctx).getZoneNames().isEmpty()) {
					ctx.getSource().sendFeedback(() -> Text.literal("There are no zones"), false);
				}
				getSettings(ctx).getZoneNames().forEach(name -> {
					ctx.getSource().sendFeedback(() -> Text.literal(name), false);
				});
				return 1;
			})
		).then(
				messageCommand("entry-command", MessageZoneData::getEnterCommand, MessageZoneData::setEnterCommand, dispatcher)
		).then(
				messageCommand("exit-command", MessageZoneData::getLeaveCommand, MessageZoneData::setLeaveCommand, dispatcher)
		).then(
			literal("spawns").then(
				literal("add").then(
					zone().then(
						spawnGroup("spawnGroup").then(
							argument("data", NbtCompoundArgumentType.nbtCompound()).executes(ctx -> {
								var spawnEntry = BetterSpawnEntry.CODEC.parse(
										ctx.getSource().getRegistryManager().getOps(NbtOps.INSTANCE),
										NbtCompoundArgumentType.getNbtCompound(ctx, "data")
								).resultOrPartial(
										err -> ctx.getSource().sendError(Text.literal(err))
								);
								return addSpawnRule(
										ctx, "spawn", spawnEntry,
										data -> data.getSpawns(getSpawnGroup(ctx, "spawnGroup"))
								);
							})
						)
					)
				)
			).then(
				literal("remove").then(
					zone().then(
						spawnGroup("spawnGroup").then(
							argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
								return removeSpawnRule(
										ctx, "spawn", BetterSpawnEntry::toString,
										data -> data.getSpawns(getSpawnGroup(ctx, "spawnGroup"))
								);
							})
						)
					)
				)
			).then(
				literal("list").then(
					zone().then(
						spawnGroup("spawnGroup").executes(ctx -> {
							return listSpawnRules(
									ctx, "spawn", BetterSpawnEntry::toString,
									data -> data.getSpawns(getSpawnGroup(ctx, "spawnGroup"))
							);
						})
					).executes(ctx -> {
						int returnNum = 0;
						for (SpawnGroup group : SpawnGroup.values()) {
							ctx.getSource().sendFeedback(() -> Text.literal("\n" + group.getName() + ":"), false);
							returnNum += listSpawnRules(
									ctx, "spawn", BetterSpawnEntry::toString,
									data -> data.getSpawns(group)
							);
						}
						return returnNum;
					})
				)
			)
		).then(
			literal("spawnremovers").then(
				literal("add").then(
					zone().then(
						literal("types").then(
							argument("entity", RegistryPredicateArgumentType.registryPredicate(RegistryKeys.ENTITY_TYPE)).executes(ctx -> {
								return addSpawnRule(
										ctx, "spawn remover",
										RegistryPredicateArgumentType.getPredicate(
												ctx, "entity", RegistryKeys.ENTITY_TYPE, ENTITY_FAIL
										).getKey().map(
												entity -> Optional.ofNullable(Registries.ENTITY_TYPE.get(entity)).map(
														e -> EntityTypePredicate.create(
																registryAccess.getOrThrow(RegistryKeys.ENTITY_TYPE), e
														)
												),
												entityTag -> Optional.of(
														EntityTypePredicate.create(
																registryAccess.getOrThrow(RegistryKeys.ENTITY_TYPE),
																entityTag
														)
												)
										).map(SpawnRemoverRegistry.TypesSpawnRemover::new),
										AdditionalSpawnsZoneData::getSpawnRemovers
								);
							})
						)
					).then(
						spawnGroup("spawnGroup").executes(ctx -> {
							return addSpawnRule(
									ctx, "spawn remover",
									Optional.of(new SpawnRemoverRegistry.SpawnGroupSpawnRemover(
											getSpawnGroup(ctx, "spawnGroup")
									)),
									AdditionalSpawnsZoneData::getSpawnRemovers
							);
						})
					).then(
						literal("*").executes(ctx -> {
							return addSpawnRule(
									ctx, "spawn remover",
									Optional.of(SpawnRemoverRegistry.AllSpawnRemover.INSTANCE),
									AdditionalSpawnsZoneData::getSpawnRemovers
							);
						})
					)
				)
			).then(
				literal("remove").then(
					zone().then(
						argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
							return removeSpawnRule(
									ctx, "spawn remover",
									blocker -> SpawnRemoverRegistry.SpawnRemover.REGISTRY_CODEC.encodeStart(
											ctx.getSource().getRegistryManager().getOps(NbtOps.INSTANCE), blocker
									).resultOrPartial(
											METAcraftZones.LOGGER::error
									).map(NbtElement::asString).orElse("Error"),
									AdditionalSpawnsZoneData::getSpawnRemovers
							);
						})
					)
				)
			).then(
				literal("list").then(
					zone().executes(ctx -> {
						return listSpawnRules(
								ctx, "spawn remover",
								blocker -> SpawnRemoverRegistry.SpawnRemover.REGISTRY_CODEC.encodeStart(
										ctx.getSource().getRegistryManager().getOps(NbtOps.INSTANCE), blocker
								).resultOrPartial(
										METAcraftZones.LOGGER::error
								).map(NbtElement::asString).orElse("Error"),
								AdditionalSpawnsZoneData::getSpawnRemovers
						);
					})
				)
			)
		).then(
			literal("spawnrules").then(
				literal("add").then(
					zone().then(
						argument("entity", RegistryPredicateArgumentType.registryPredicate(RegistryKeys.ENTITY_TYPE)).then(
							argument("data", NbtCompoundArgumentType.nbtCompound()).executes(ctx -> {
								return addSpawnRule(
									ctx, "spawn rule",
									RegistryPredicateArgumentType.getPredicate(ctx, "entity", RegistryKeys.ENTITY_TYPE, ENTITY_FAIL).getKey().map(
											entity -> Optional.ofNullable(Registries.ENTITY_TYPE.get(entity)).map(
													e -> EntityTypePredicate.create(
															registryAccess.getOrThrow(RegistryKeys.ENTITY_TYPE), e
													)
											),
											entityTag -> Optional.of(
													EntityTypePredicate.create(
															registryAccess.getOrThrow(RegistryKeys.ENTITY_TYPE),
															entityTag
													)
											)
									).flatMap(entity -> {
										return LootCondition.CODEC.parse(
												ctx.getSource().getRegistryManager().getOps(NbtOps.INSTANCE),
												NbtCompoundArgumentType.getNbtCompound(ctx, "data")).resultOrPartial(
												error -> ctx.getSource().sendError(Text.literal(error))
										).map(rule -> new AdditionalSpawnsZoneData.SpawnRuleEntry(entity, rule));
									}),
									AdditionalSpawnsZoneData::getSpawnRules
								);
							})
						)
					)
				)
			).then(
				literal("remove").then(
					zone().then(
						argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
							return removeSpawnRule(
									ctx, "spawn rule",
									rule -> AdditionalSpawnsZoneData.SpawnRuleEntry.CODEC.encodeStart(
											ctx.getSource().getRegistryManager().getOps(NbtOps.INSTANCE), rule
									).resultOrPartial(
											METAcraftZones.LOGGER::error
									).map(NbtElement::asString).orElse("Error"),
									AdditionalSpawnsZoneData::getSpawnRules
							);
						})
					)
				)
			).then(
				literal("list").then(
					zone().executes(ctx -> {
						return listSpawnRules(
								ctx, "spawn rule",
								rule -> AdditionalSpawnsZoneData.SpawnRuleEntry.CODEC.encodeStart(
										ctx.getSource().getRegistryManager().getOps(NbtOps.INSTANCE), rule
								).resultOrPartial(
										METAcraftZones.LOGGER::error
								).map(NbtElement::asString).orElse("Error"),
								AdditionalSpawnsZoneData::getSpawnRules
						);
					})
				)
			)
		);
	}

	@FunctionalInterface
	public interface FunctionForCommands<T, R> {
		R apply(T var1) throws CommandSyntaxException;
	}

	private static <T> int addSpawnRule(
			CommandContext<ServerCommandSource> ctx,
			String nameOfObject,
			Optional<T> creatorFromArgs,
			FunctionForCommands<AdditionalSpawnsZoneData, AdditionalSpawnsZoneData.ListAccessor<T>> dataGetter
	) throws CommandSyntaxException {
		ctx.getSource().sendError(Text.literal("WARNING: Zone spawns have been disabled due to performance issues!"));
		var zone = getZone(ctx);
		var data = creatorFromArgs.orElse(null);
		if (data != null) {
			var spawnData = zone.getOrCreate(ZoneDataRegistry.SPAWN);
			dataGetter.apply(spawnData).add(data);
			ctx.getSource().sendFeedback(() -> Text.literal("Added " + nameOfObject + " to " + zone.getName()), true);
			return 1;
		} else {
			return 0;
		}
	}

	private static <T> int removeSpawnRule(
			CommandContext<ServerCommandSource> ctx,
			String nameOfObject, Function<T, String> printer,
			FunctionForCommands<AdditionalSpawnsZoneData, AdditionalSpawnsZoneData.ListAccessor<T>> dataGetter
	) throws CommandSyntaxException {
		ctx.getSource().sendError(Text.literal("WARNING: Zone spawns have been disabled due to performance issues!"));
		int index = IntegerArgumentType.getInteger(ctx, "index");
		var zone = getZone(ctx);
		var data = zone.get(ZoneDataRegistry.SPAWN).orElse(null);
		if (data != null) {
			var list = dataGetter.apply(data);
			if (index >= list.size()) {
				ctx.getSource().sendError(Text.literal("Index too large"));
				return 0;
			}
			var entry = list.remove(index);
			ctx.getSource().sendFeedback(() -> Text.literal("Removed " + nameOfObject + " " + printer.apply(entry) + " successfully from " + zone.getName()), true);
		} else {
			ctx.getSource().sendFeedback(() -> Text.literal("No data"), false);
			return 0;
		}
		return 1;
	}

	private static <T> int listSpawnRules(
			CommandContext<ServerCommandSource> ctx,
			String nameOfObject, Function<T, String> printer,
			FunctionForCommands<AdditionalSpawnsZoneData, AdditionalSpawnsZoneData.ListAccessor<T>> dataGetter
	) throws CommandSyntaxException {
		ctx.getSource().sendError(Text.literal("WARNING: Zone spawns have been disabled due to performance issues!"));
		var zone = getZone(ctx);
		var data = zone.get(ZoneDataRegistry.SPAWN).orElse(null);
		if (data != null) {
			var objects = dataGetter.apply(data);
			MutableInt i = new MutableInt(0);
			objects.forEach(o -> {
				ctx.getSource().sendFeedback(() -> Text.literal(i.getAndIncrement() + ": " + printer.apply(o)), false);
			});
			if (objects.isEmpty()) {
				ctx.getSource().sendFeedback(() -> Text.literal("No " + nameOfObject + "s defined"), false);
			}
			return objects.size();
		} else {
			ctx.getSource().sendFeedback(() -> Text.literal("No data"), false);
			return 0;
		}
	}

	private static ArgumentBuilder<ServerCommandSource, ?> spawnGroup(String name) {
		return argument(name, StringArgumentType.word()).suggests(SUGGEST_SPAWN_GROUP);
	}

	private static SpawnGroup getSpawnGroup(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		String argVal = StringArgumentType.getString(ctx, name);
		return SpawnGroup.CODEC.parse(
				NbtOps.INSTANCE,
				NbtString.of(argVal)
		).resultOrPartial(err -> ctx.getSource().sendError(Text.literal(err))).orElseThrow(INVALID_SPAWN_GROUP::create);
	}

	static Text join(Iterator<? extends Text> text, Text delimiter) {
		MutableText full = Text.empty();
		if (text.hasNext()) {
			full.append(text.next());
		}
		text.forEachRemaining(part -> {
			full.append(delimiter).append(part);
		});
		return full;
	}


	@FunctionalInterface
	public interface PassCommand {
		int run(CommandContext<ServerCommandSource> context, String command) throws CommandSyntaxException;
	}

	public static SuggestionProvider<ServerCommandSource> getCommandSuggest(
			int pos, CommandDispatcher<ServerCommandSource> dispatcher
	) {
		return (ctx, suggestionsBuilder) -> {
			StringBuilder builder = new StringBuilder();
			int startPos = ctx.getNodes().get(ctx.getNodes().size() - pos).getRange().getStart();
			for (int i = pos; i > 0; i--) {
				builder.append(StringArgumentType.escapeIfRequired(
						StringArgumentType.getString(
								ctx, ctx.getNodes().get(ctx.getNodes().size() - i).getNode().getName()
						)
				)).append(" ");
			}
			var result = dispatcher.parse(builder.toString(), ctx.getSource());
			return dispatcher.getCompletionSuggestions(result).thenApply(suggestions -> {
				MutableInt totalOffset = new MutableInt(0);
				suggestions.getList().forEach(suggestion -> {
					var newString = StringArgumentType.escapeIfRequired(suggestion.getText());
					int offset = newString.length() - suggestion.getText().length();
					((AccessorSuggestion) suggestion).setText(newString);
					((AccessorStringRange) suggestion.getRange()).setStart(suggestion.getRange().getStart()+startPos);
					((AccessorStringRange) suggestion.getRange()).setEnd(suggestion.getRange().getEnd()+startPos + offset);
					totalOffset.setValue(Math.max(offset, totalOffset.getValue()));
				});
				((AccessorStringRange) suggestions.getRange()).setStart(suggestions.getRange().getStart()+startPos);
				((AccessorStringRange) suggestions.getRange()).setEnd(suggestions.getRange().getEnd()+startPos + totalOffset.getValue());
				return suggestions;
			});
		};
	}

	public static final SuggestionProvider<ServerCommandSource> ROOT_COMMAND_SUGGEST = (ctx, suggestionsBuilder) -> {
		return CommandSource.suggestMatching(
				ctx.getRootNode().getChildren().stream().map(CommandNode::getName), suggestionsBuilder
		);
	};

	public static ArgumentBuilder<ServerCommandSource, ?> command(
			String prefix, int maxLength, CommandDispatcher<ServerCommandSource> dispatcher, PassCommand passCommand
	) {
		ArgumentBuilder<ServerCommandSource, ?> current = argument("the_rest", StringArgumentType.greedyString()).executes(ctx -> {
			StringBuilder builder = new StringBuilder(StringArgumentType.getString(ctx, prefix + "0"));
			for (int j = 1; j <= maxLength; j++) {
				builder.append(" ").append(StringArgumentType.getString(ctx, prefix + j));
			}
			builder.append(" ").append(StringArgumentType.getString(ctx, "the_rest"));
			return passCommand.run(ctx, builder.toString());
		});
		for (int i = maxLength; i > 0; i--) {
			int argsThusFar = i;
			var newBottom = argument(
					prefix + i, StringArgumentType.string()
			).suggests(getCommandSuggest(i, dispatcher)).executes(ctx -> {
				StringBuilder builder = new StringBuilder(StringArgumentType.getString(ctx, prefix + "0"));
				for (int j = 1; j <= argsThusFar; j++) {
					builder.append(" ").append(StringArgumentType.getString(ctx, prefix + j));
				}
				return passCommand.run(ctx, builder.toString());
			});
			current = newBottom.then(current);
		}
		return argument(prefix + "0", StringArgumentType.string()).suggests(ROOT_COMMAND_SUGGEST).executes(ctx -> {
			return passCommand.run(ctx, StringArgumentType.getString(ctx, prefix + "0"));
		}).then(current);
	}

	static ArgumentBuilder<ServerCommandSource, ?> messageCommand(
			String name,
			Function<MessageZoneData, Optional<String>> messageGetter,
			BiConsumer<MessageZoneData, Optional<String>> messageSetter,
			CommandDispatcher<ServerCommandSource> dispatcher
	) {
		return literal(name).then(
			literal("get").then(
				zone().executes(ctx -> {
					var opt = getZone(ctx).get(ZoneDataRegistry.MESSAGE).flatMap(messageGetter);
					opt.ifPresentOrElse(text -> {
						ctx.getSource().sendFeedback(() -> Text.literal("Message: ").append(text), false);
					}, () -> {
						ctx.getSource().sendFeedback(() -> Text.literal("No message set for this zone"), false);
					});
					return opt.isPresent() ? 1 : 0;
				})
			)
		).then(
			literal("set").then(
				zone().then(
					command("command", 10, dispatcher, (ctx, command) -> {
						var zone = getZone(ctx);
						messageSetter.accept(
								zone.getOrCreate(ZoneDataRegistry.MESSAGE),
								Optional.of(command)
						);
						ctx.getSource().sendFeedback(
								() -> Text.literal("Set " + name + " in zone " + zone.getName() + " to " + command),
								true
						);
						return 1;
					})
				)
			)
		).then(
			literal("remove").then(
				zone().executes(ctx -> {
					var zone = getZone(ctx);
					messageSetter.accept(
							zone.getOrCreate(ZoneDataRegistry.MESSAGE),
						Optional.empty()
					);
					ctx.getSource().sendFeedback(
							() -> Text.literal("Removed " + name + " from " + zone.getName()),
							true
					);
					return 1;
				})
			)
		);
	}

	static ArgumentBuilder<ServerCommandSource, ?> addRemoveSubDim(LiteralArgumentBuilder<ServerCommandSource> name, boolean add) {
		return name.then(
			zone().then(
				argument("dim", DimensionArgumentType.dimension()).executes(ctx -> {
					ServerWorld dim = DimensionArgumentType.getDimensionArgument(ctx, "dim");
					var dimKey = dim.getRegistryKey();
					RealZone zone = getZone(ctx);
					if (add) {
						if (zone.getDim() == dimKey || zone.hasRemoteZone(dimKey)) {
							ctx.getSource().sendFeedback(() -> Text.literal(
									"Dimension already covered!"
							), false);
						} else {
							zone.addRemoteDimension(dim);
							ctx.getSource().sendFeedback(() -> Text.literal(
									"Added dimension " + dimKey.getValue() + " to " + zone.getName()
							), true);
						}
					} else {
						if (zone.getDim() == dimKey) {
							ctx.getSource().sendFeedback(() -> Text.literal(
											"Cannot remove source dimension, delete the zone instead!"
									), false
							);
						} else if (zone.hasRemoteZone(dimKey)) {
							zone.removeRemoteDimension(dim);
							ctx.getSource().sendFeedback(() -> Text.literal(
									"Removed dimension " + dimKey.getValue() + " from " + zone.getName()
							), true);
						} else {
							ctx.getSource().sendFeedback(() -> Text.literal(
									"Dimension not covered!"
							), false);
						}
					}
					return 1;
				})
			)
		);
	}

	static RealZone getZone(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		return ZoneCommandUtils.getZone(ctx, NAME);
	}

	static RequiredArgumentBuilder<ServerCommandSource, ?> zone() {
		return ZoneCommandUtils.zone(NAME);
	}

	static ArgumentBuilder<ServerCommandSource, ?> createCreateCommandsFromRegistry(
			ArgumentBuilder<ServerCommandSource, ?> builder, CommandRegistryAccess registryAccess
	) {
		return getZoneCreator(builder, registryAccess, (zoneCreator, ctx) -> {
			World world = ctx.getSource().getWorld();
			ZoneType zone = zoneCreator.create();
			String name = StringArgumentType.getString(ctx, NAME);
			if (getSettings(ctx).containsZone(name)) {
				throw ZONE_ALREADY_EXISTS.create();
			}
			RealZone container = new RealZone(
					name, world, zone, new HashMap<>(), 0, getSettings(ctx)::markDirty
			);
			getSettings(ctx).addZone(container);
			ctx.getSource().sendFeedback(
					() -> Text.literal("Added " + container.getName()), true
			);
			return 1;
		});
	}

	static ArgumentBuilder<ServerCommandSource, ?> getZoneCreator(
			ArgumentBuilder<ServerCommandSource, ?> builder, CommandRegistryAccess registryAccess, ZoneAdder zoneAdder
	) {
		ZoneRegistry.REGISTRY.streamEntries().forEach(entry -> {
			if(entry.value().commandCreator() != null) {
				builder.then(
						entry.value().commandCreator().createCommand(
								literal(Commands.getIDAsString(entry.registryKey().getValue())), registryAccess, zoneAdder
						)
				);
			}
		});
		return builder;
	}

	@FunctionalInterface
	public interface ZoneAdder {
		int add(ZoneCreator zoneCreator, CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException;
	}

	@FunctionalInterface
	public interface ZoneCreator {
		ZoneType create() throws CommandSyntaxException;
	}

	public static ZoneManager getSettings(CommandContext<ServerCommandSource> ctx) {
		return ZoneManager.getInstance(ctx.getSource().getServer());
	}

}
