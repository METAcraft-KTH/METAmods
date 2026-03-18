package nu.metacraft.zones;

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
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.criterion.EntityTypePredicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import nu.metacraft.lib.util.StoredCondition;
import nu.metacraft.zones.zone.types.*;
import org.apache.commons.lang3.mutable.MutableInt;
import nu.metacraft.zones.mixin.StringRangeAccessor;
import nu.metacraft.zones.mixin.SuggestionAccessor;
import nu.metacraft.zones.spawns.BetterSpawnEntry;
import nu.metacraft.zones.spawns.SpawnRemoverRegistry;
import nu.metacraft.zones.util.ZoneCommandUtils;
import nu.metacraft.zones.zone.RealZone;
import nu.metacraft.zones.zone.ZoneRegistry;
import nu.metacraft.zones.zone.data.AdditionalSpawnsZoneData;
import nu.metacraft.zones.zone.data.MessageZoneData;
import nu.metacraft.zones.zone.data.ZoneDataRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ZoneManagementCommand {

	private static final Map<String, ZoneEntry> zonesToRemove = new ConcurrentHashMap<>();

	public static final String NAME = "name";

	public static final SimpleCommandExceptionType ZONE_ALREADY_EXISTS = new SimpleCommandExceptionType(
			Component.literal("Another zone already exists with that name.")
	);

	public static final SimpleCommandExceptionType INVALID_SPAWN_GROUP = new SimpleCommandExceptionType(
			Component.literal("Invalid Spawn Group")
	);

	public static final SuggestionProvider<CommandSourceStack> SUGGEST_SPAWN_GROUP = (ctx, builder) -> {
		return SharedSuggestionProvider.suggest(StringRepresentable.keys(MobCategory.values()).keys(NbtOps.INSTANCE).map(Tag::toString), builder);
	};

	private static final DynamicCommandExceptionType ENTITY_FAIL = new DynamicCommandExceptionType(id -> Component.literal(id + " is not a valid entity or entity tag!"));

	private static final DynamicCommandExceptionType ANY = new DynamicCommandExceptionType(s -> Component.literal(s.toString()));

	private static final Dynamic2CommandExceptionType CONTAINS_FAIL = new Dynamic2CommandExceptionType((pos, zone) -> Component.literal(pos + " is not inside " + zone));

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			zonesToRemove.values().removeIf(
					entry -> entry.timeLeft().decrementAndGet() <= 0
			);
		});
	}

	private record ZoneEntry(String name, MutableInt timeLeft) {}

	private static int contains(CommandContext<CommandSourceStack> ctx, BlockPos pos) throws CommandSyntaxException {
		var zone = getZone(ctx);
		if (zone.contains(ctx.getSource().getLevel().dimension(), pos)) {
			ctx.getSource().sendSuccess(
					() -> Component.literal(pos.toShortString() + " is indeed inside " + zone.getName()),
					false
			);
			return 1;
		} else {
			throw CONTAINS_FAIL.create(pos.toShortString(), zone.getName());
		}
	}

	static void registerCommand(
			LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext registryAccess,
			CommandDispatcher<CommandSourceStack> dispatcher
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
					ctx.getSource().sendSuccess(
							() -> Component.literal("Expanded zone " + zone.getName() + " to include " + newZone),
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
					ctx.getSource().sendSuccess(
							() -> Component.literal("Limited zone " + zone.getName() + " to " + newZone),
							true
					);
					return 1;
				})
			)
		).then(
			literal("contains").then(
				zone().executes(ctx -> contains(ctx, BlockPos.containing(ctx.getSource().getPosition()))).then(
					argument("pos", BlockPosArgument.blockPos()).executes(
						ctx -> contains(ctx, BlockPosArgument.getBlockPos(ctx, "pos"))
					)
				)
			)
		).then(
			literal("replace").then(
				getZoneCreator(zone(), registryAccess, (zoneCreator, ctx) -> {
					var zone = getZone(ctx);
					var newZone = zoneCreator.create();
					zone.setZone(newZone);
					ctx.getSource().sendSuccess(
							() -> Component.literal("Replaced zone shape of " + zone.getName() + " with " + newZone),
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
								ctx.getSource().sendSuccess(
										() -> Component.literal("Removed shape " + removed + " from " + zone.getName()),
										true
								);
								if (combined.zoneCount() == 1) {
									zone.setZone(combined.getZone(0));
								}
								return 1;
							} else {
								ctx.getSource().sendFailure(Component.literal(
										"Index too high"
								));
							}
						} else {
							ctx.getSource().sendFailure(Component.literal(
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
									ctx.getSource().sendSuccess(
											() -> Component.literal("Set shape to " + toKeep + " for " + zone.getName()),
											true
									);
									return combined.zoneCount()-1;
								} else {
									ctx.getSource().sendFailure(Component.literal(
											"Index too high"
									));
								}
							} else {
								ctx.getSource().sendFailure(Component.literal(
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
					ctx.getSource().sendSuccess(() -> Component.literal("Negated " + zone.getName()), true);
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
						ctx.getSource().sendSuccess(
								() -> Component.literal("Set zone priority for " + zone.getName() + " to " + priority),
								true
						);
						return 1;
					})
				)
			)
		).then(
			ZoneCommandUtils.queryZoneMulti(literal("get"), zone -> {
				return ImmutableList.of(
					Component.literal("Name: " + zone.getName()),
					Component.literal("Dimension: " + zone.getDim().identifier()),
					Component.literal("Zone: " + zone.getZone()),
					Component.literal("Priority: " + zone.getPriority()),
					Component.literal("AdditionalDimensions: " + zone.getRemoteZones().stream().map(type -> {
						return type.getDim().identifier().toString();
					}).collect(Collectors.joining(", "))),
					Component.literal("Data: ").append(
						join(zone.getAllData().stream().map(data -> Component.literal(" ").append(data.toText(zone.getWorld().registryAccess()))).iterator(), Component.literal(",\n"))
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
						zonesToRemove.put(ctx.getSource().getTextName(), new ZoneEntry(name, new MutableInt(60*20)));
						ctx.getSource().sendSuccess(
								() -> Component.literal("Are you sure you want to remove " + name + "?"),
								false
						);
						ctx.getSource().sendSuccess(
								() -> Component.literal("If yes, please type ").append(
										Component.literal("/zone confirm-remove " + name).withStyle(
												Style.EMPTY.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent.SuggestCommand(
														"/zone confirm-remove " + name
												))
										)
								),
								false
						);
						return 1;
					} else {
						ctx.getSource().sendSuccess(
								() -> Component.literal("No zone with " + name + " exists!"),
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
					var toRemove = zonesToRemove.get(ctx.getSource().getTextName());
					if (toRemove == null) {
						ctx.getSource().sendFailure(
							Component.literal("Please type ").append(
								Component.literal("/zone remove " + name).withStyle(
									Style.EMPTY.withColor(ChatFormatting.YELLOW)
											.withClickEvent(new ClickEvent.SuggestCommand(
												"/zone remove " + name
											))
								)
							)
						);
						return 0;
					}

					if (toRemove.name.equals(name)) {
						if (getSettings(ctx).removeZone(name)) {
							ctx.getSource().sendSuccess(
									() -> Component.literal("Removed " + name),
									true
							);
							return 1;
						} else {
							ctx.getSource().sendSuccess(
									() -> Component.literal("No zone with " + name + " exists!"),
									false
							);
							return 0;
						}
					} else {
						ctx.getSource().sendFailure(
							Component.literal("Are you sure you're trying to remove the right zone? Because you specified another zone in the remove command...")
						);
					}
					return 0;
				})
			)
		).then(
			literal("list").executes(ctx -> {
				if (getSettings(ctx).getZoneNames().isEmpty()) {
					ctx.getSource().sendSuccess(() -> Component.literal("There are no zones"), false);
				}
				getSettings(ctx).getZoneNames().forEach(name -> {
					ctx.getSource().sendSuccess(() -> Component.literal(name), false);
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
							argument("data", CompoundTagArgument.compoundTag()).executes(ctx -> {
								var spawnEntry = BetterSpawnEntry.WEIGHTED_CODEC.parse(
										ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE),
										CompoundTagArgument.getCompoundTag(ctx, "data")
								).resultOrPartial(
										err -> ctx.getSource().sendFailure(Component.literal(err))
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
						for (MobCategory group : MobCategory.values()) {
							ctx.getSource().sendSuccess(() -> Component.literal("\n" + group.getName() + ":"), false);
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
							argument("entity", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ENTITY_TYPE)).executes(ctx -> {
								return addSpawnRule(
										ctx, "spawn remover",
										ResourceOrTagKeyArgument.getResourceOrTagKey(
												ctx, "entity", Registries.ENTITY_TYPE, ENTITY_FAIL
										).unwrap().map(
												entity -> Optional.ofNullable(BuiltInRegistries.ENTITY_TYPE.getValue(entity)).map(
														e -> EntityTypePredicate.of(
																registryAccess.lookupOrThrow(Registries.ENTITY_TYPE), e
														)
												),
												entityTag -> Optional.of(
														EntityTypePredicate.of(
																registryAccess.lookupOrThrow(Registries.ENTITY_TYPE),
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
											ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE), blocker
									).resultOrPartial(
											METAcraftZones.LOGGER::error
									).map(Tag::toString).orElse("Error"),
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
										ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE), blocker
								).resultOrPartial(
										METAcraftZones.LOGGER::error
								).map(Tag::toString).orElse("Error"),
								AdditionalSpawnsZoneData::getSpawnRemovers
						);
					})
				)
			)
		).then(
			literal("prevent-entry").then(
					literal("clear").then(
							zone().executes(ctx -> {
								getZone(ctx).removeZoneData(ZoneDataRegistry.PREVENT_ENTRY);
								return 1;
							})
					)
			).then(
					literal("set").then(
							zone().then(
									argument("data", CompoundTagArgument.compoundTag()).executes(ctx -> {
										var data = CompoundTagArgument.getCompoundTag(ctx, "data");
										var condition = StoredCondition.CODEC.parse(
												ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE),
												data
										).getOrThrow(ANY::create);
										getZone(ctx).getOrCreate(ZoneDataRegistry.PREVENT_ENTRY).setStoredCondition(condition);
										return 1;
									})
							)
					)
			)
		).then(
			literal("spawnrules").then(
				literal("add").then(
					zone().then(
						argument("entity", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ENTITY_TYPE)).then(
							argument("data", CompoundTagArgument.compoundTag()).executes(ctx -> {
								return addSpawnRule(
									ctx, "spawn rule",
									ResourceOrTagKeyArgument.getResourceOrTagKey(ctx, "entity", Registries.ENTITY_TYPE, ENTITY_FAIL).unwrap().map(
											entity -> Optional.ofNullable(BuiltInRegistries.ENTITY_TYPE.getValue(entity)).map(
													e -> EntityTypePredicate.of(
															registryAccess.lookupOrThrow(Registries.ENTITY_TYPE), e
													)
											),
											entityTag -> Optional.of(
													EntityTypePredicate.of(
															registryAccess.lookupOrThrow(Registries.ENTITY_TYPE),
															entityTag
													)
											)
									).flatMap(entity -> {
										return LootItemCondition.DIRECT_CODEC.parse(
												ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE),
												CompoundTagArgument.getCompoundTag(ctx, "data")).resultOrPartial(
												error -> ctx.getSource().sendFailure(Component.literal(error))
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
											ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE), rule
									).resultOrPartial(
											METAcraftZones.LOGGER::error
									).map(Tag::toString).orElse("Error"),
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
										ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE), rule
								).resultOrPartial(
										METAcraftZones.LOGGER::error
								).map(Tag::toString).orElse("Error"),
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
			CommandContext<CommandSourceStack> ctx,
			String nameOfObject,
			Optional<T> creatorFromArgs,
			FunctionForCommands<AdditionalSpawnsZoneData, AdditionalSpawnsZoneData.ListAccessor<T>> dataGetter
	) throws CommandSyntaxException {
		var zone = getZone(ctx);
		var data = creatorFromArgs.orElse(null);
		if (data != null) {
			var spawnData = zone.getOrCreate(ZoneDataRegistry.SPAWN);
			dataGetter.apply(spawnData).add(data);
			ctx.getSource().sendSuccess(() -> Component.literal("Added " + nameOfObject + " to " + zone.getName()), true);
			return 1;
		} else {
			return 0;
		}
	}

	private static <T> int removeSpawnRule(
			CommandContext<CommandSourceStack> ctx,
			String nameOfObject, Function<T, String> printer,
			FunctionForCommands<AdditionalSpawnsZoneData, AdditionalSpawnsZoneData.ListAccessor<T>> dataGetter
	) throws CommandSyntaxException {
		int index = IntegerArgumentType.getInteger(ctx, "index");
		var zone = getZone(ctx);
		var data = zone.get(ZoneDataRegistry.SPAWN).orElse(null);
		if (data != null) {
			var list = dataGetter.apply(data);
			if (index >= list.size()) {
				ctx.getSource().sendFailure(Component.literal("Index too large"));
				return 0;
			}
			var entry = list.remove(index);
			ctx.getSource().sendSuccess(() -> Component.literal("Removed " + nameOfObject + " " + printer.apply(entry) + " successfully from " + zone.getName()), true);
		} else {
			ctx.getSource().sendSuccess(() -> Component.literal("No data"), false);
			return 0;
		}
		return 1;
	}

	private static <T> int listSpawnRules(
			CommandContext<CommandSourceStack> ctx,
			String nameOfObject, Function<T, String> printer,
			FunctionForCommands<AdditionalSpawnsZoneData, AdditionalSpawnsZoneData.ListAccessor<T>> dataGetter
	) throws CommandSyntaxException {
		var zone = getZone(ctx);
		var data = zone.get(ZoneDataRegistry.SPAWN).orElse(null);
		if (data != null) {
			var objects = dataGetter.apply(data);
			MutableInt i = new MutableInt(0);
			objects.forEach(o -> {
				ctx.getSource().sendSuccess(() -> Component.literal(i.getAndIncrement() + ": " + printer.apply(o)), false);
			});
			if (objects.isEmpty()) {
				ctx.getSource().sendSuccess(() -> Component.literal("No " + nameOfObject + "s defined"), false);
			}
			return objects.size();
		} else {
			ctx.getSource().sendSuccess(() -> Component.literal("No data"), false);
			return 0;
		}
	}

	private static ArgumentBuilder<CommandSourceStack, ?> spawnGroup(String name) {
		return argument(name, StringArgumentType.word()).suggests(SUGGEST_SPAWN_GROUP);
	}

	private static MobCategory getSpawnGroup(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		String argVal = StringArgumentType.getString(ctx, name);
		return MobCategory.CODEC.parse(
				NbtOps.INSTANCE,
				StringTag.valueOf(argVal)
		).resultOrPartial(err -> ctx.getSource().sendFailure(Component.literal(err))).orElseThrow(INVALID_SPAWN_GROUP::create);
	}

	static Component join(Iterator<? extends Component> text, Component delimiter) {
		MutableComponent full = Component.empty();
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
		int run(CommandContext<CommandSourceStack> context, String command) throws CommandSyntaxException;
	}

	public static SuggestionProvider<CommandSourceStack> getCommandSuggest(
			int pos, CommandDispatcher<CommandSourceStack> dispatcher
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
					((SuggestionAccessor) suggestion).setText(newString);
					((StringRangeAccessor) suggestion.getRange()).setStart(suggestion.getRange().getStart()+startPos);
					((StringRangeAccessor) suggestion.getRange()).setEnd(suggestion.getRange().getEnd()+startPos + offset);
					totalOffset.setValue(Math.max(offset, totalOffset.getValue()));
				});
				((StringRangeAccessor) suggestions.getRange()).setStart(suggestions.getRange().getStart()+startPos);
				((StringRangeAccessor) suggestions.getRange()).setEnd(suggestions.getRange().getEnd()+startPos + totalOffset.getValue());
				return suggestions;
			});
		};
	}

	public static final SuggestionProvider<CommandSourceStack> ROOT_COMMAND_SUGGEST = (ctx, suggestionsBuilder) -> {
		return SharedSuggestionProvider.suggest(
				ctx.getRootNode().getChildren().stream().map(CommandNode::getName), suggestionsBuilder
		);
	};

	public static ArgumentBuilder<CommandSourceStack, ?> command(
			String prefix, int maxLength, CommandDispatcher<CommandSourceStack> dispatcher, PassCommand passCommand
	) {
		ArgumentBuilder<CommandSourceStack, ?> current = argument("the_rest", StringArgumentType.greedyString()).executes(ctx -> {
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

	static ArgumentBuilder<CommandSourceStack, ?> messageCommand(
			String name,
			Function<MessageZoneData, Optional<String>> messageGetter,
			BiConsumer<MessageZoneData, Optional<String>> messageSetter,
			CommandDispatcher<CommandSourceStack> dispatcher
	) {
		return literal(name).then(
			literal("get").then(
				zone().executes(ctx -> {
					var opt = getZone(ctx).get(ZoneDataRegistry.MESSAGE).flatMap(messageGetter);
					opt.ifPresentOrElse(text -> {
						ctx.getSource().sendSuccess(() -> Component.literal("Message: ").append(text), false);
					}, () -> {
						ctx.getSource().sendSuccess(() -> Component.literal("No message set for this zone"), false);
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
						ctx.getSource().sendSuccess(
								() -> Component.literal("Set " + name + " in zone " + zone.getName() + " to " + command),
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
					ctx.getSource().sendSuccess(
							() -> Component.literal("Removed " + name + " from " + zone.getName()),
							true
					);
					return 1;
				})
			)
		);
	}

	static ArgumentBuilder<CommandSourceStack, ?> addRemoveSubDim(LiteralArgumentBuilder<CommandSourceStack> name, boolean add) {
		return name.then(
			zone().then(
				argument("dim", DimensionArgument.dimension()).executes(ctx -> {
					ServerLevel dim = DimensionArgument.getDimension(ctx, "dim");
					var dimKey = dim.dimension();
					RealZone zone = getZone(ctx);
					if (add) {
						if (zone.getDim() == dimKey || zone.hasRemoteZone(dimKey)) {
							ctx.getSource().sendSuccess(() -> Component.literal(
									"Dimension already covered!"
							), false);
						} else {
							zone.addRemoteDimension(dim);
							ctx.getSource().sendSuccess(() -> Component.literal(
									"Added dimension " + dimKey.identifier() + " to " + zone.getName()
							), true);
						}
					} else {
						if (zone.getDim() == dimKey) {
							ctx.getSource().sendSuccess(() -> Component.literal(
											"Cannot remove source dimension, delete the zone instead!"
									), false
							);
						} else if (zone.hasRemoteZone(dimKey)) {
							zone.removeRemoteDimension(dim);
							ctx.getSource().sendSuccess(() -> Component.literal(
									"Removed dimension " + dimKey.identifier() + " from " + zone.getName()
							), true);
						} else {
							ctx.getSource().sendSuccess(() -> Component.literal(
									"Dimension not covered!"
							), false);
						}
					}
					return 1;
				})
			)
		);
	}

	static RealZone getZone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ZoneCommandUtils.getZone(ctx, NAME);
	}

	static RequiredArgumentBuilder<CommandSourceStack, ?> zone() {
		return ZoneCommandUtils.zone(NAME);
	}

	static ArgumentBuilder<CommandSourceStack, ?> createCreateCommandsFromRegistry(
			ArgumentBuilder<CommandSourceStack, ?> builder, CommandBuildContext registryAccess
	) {
		return getZoneCreator(builder, registryAccess, (zoneCreator, ctx) -> {
			Level world = ctx.getSource().getLevel();
			ZoneType zone = zoneCreator.create();
			String name = StringArgumentType.getString(ctx, NAME);
			if (getSettings(ctx).containsZone(name)) {
				throw ZONE_ALREADY_EXISTS.create();
			}
			RealZone container = new RealZone(
					name, world, zone, new HashMap<>(), 0, getSettings(ctx)::setDirty
			);
			getSettings(ctx).addZone(container);
			ctx.getSource().sendSuccess(
					() -> Component.literal("Added " + container.getName()), true
			);
			return 1;
		});
	}

	static ArgumentBuilder<CommandSourceStack, ?> getZoneCreator(
			ArgumentBuilder<CommandSourceStack, ?> builder, CommandBuildContext registryAccess, ZoneAdder zoneAdder
	) {
		ZoneRegistry.REGISTRY.listElements().forEach(entry -> {
			if(entry.value().commandCreator() != null) {
				builder.then(
						entry.value().commandCreator().createCommand(
								literal(Commands.getIDAsString(entry.key().identifier())), registryAccess, zoneAdder
						)
				);
			}
		});
		return builder;
	}

	@FunctionalInterface
	public interface ZoneAdder {
		int add(ZoneCreator zoneCreator, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
	}

	@FunctionalInterface
	public interface ZoneCreator {
		ZoneType create() throws CommandSyntaxException;
	}

	public static ZoneManager getSettings(CommandContext<CommandSourceStack> ctx) {
		return ZoneManager.getInstance(ctx.getSource().getServer());
	}

}
