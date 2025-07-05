package nu.metacraft.loot_containers;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.Codec;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import nu.metacraft.loot_containers.containers.*;
import nu.metacraft.loot_containers.containers.events.LootContainerEvent;
import nu.metacraft.loot_containers.containers.events.LootContainerEventRegistry;
import nu.metacraft.loot_containers.containers.events.LootContainerEventType;
import nu.metacraft.loot_containers.util.EntityOrBlockEntity;

import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {
	private static final SimpleCommandExceptionType NOT_BLOCK_ENTITY = new SimpleCommandExceptionType(
			Text.literal("Not a block entity!")
	);


	private static final DynamicCommandExceptionType INVALID_TYPE = new DynamicCommandExceptionType(
			name -> Text.literal(name + " is not a valid type!")
	);

	private static final DynamicCommandExceptionType INVALID_EVENT_TYPE = new DynamicCommandExceptionType(
			name -> Text.literal(name + " is not a valid event type!")
	);

	private static final SimpleCommandExceptionType NBT_ERROR = new SimpleCommandExceptionType(
			Text.literal("Invalid NBT")
	);

	private static final SuggestionProvider<ServerCommandSource> CONTAINER_GROUP_SUGGESTIONS = (ctx, builder) -> {
		return CommandSource.suggestMatching(
				LootContainerData.getInstance(ctx.getSource().getServer()).getGroups(), builder
		);
	};

	private static final SuggestionProvider<ServerCommandSource> CONTAINER_TYPE_SUGGESTIONS = (ctx, builder) -> {
		return CommandSource.suggestMatching(
				LootContainerRegistry.REGISTRY.getIds().stream().map(
						id -> id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()
				), builder
		);
	};

	private static final SuggestionProvider<ServerCommandSource> CONTAINER_EVENT_SUGGESTIONS = (ctx, builder) -> {
		return CommandSource.suggestMatching(
				LootContainerEventRegistry.REGISTRY.getIds().stream().map(
						id -> id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()
				), builder
		);
	};

	@SafeVarargs
	private static ArgumentBuilder<ServerCommandSource, ?> blockAndEntity(
			ArgumentBuilder<ServerCommandSource, ?> parent,
			String block, String entity,
			ArgumentBuilder<ServerCommandSource, ?>... children
	) {
		var blockPath = argument(block, BlockPosArgumentType.blockPos());
		var entityPath = argument(entity, EntityArgumentType.entity());
		for (var child : children) {
			blockPath.then(child);
			entityPath.then(child);
		}
		return parent.then(
			literal("block").then(blockPath)
		).then(
			literal("entity").then(entityPath)
		);
	}

	private static ArgumentBuilder<ServerCommandSource, ?> blockAndEntity(
			ArgumentBuilder<ServerCommandSource, ?> parent,
			String block, String entity,
			Command<ServerCommandSource> command
	) {
		return parent.then(
				literal("block").then(argument(block, BlockPosArgumentType.blockPos()).executes(command))
		).then(
				literal("entity").then(argument(entity, EntityArgumentType.entity()).executes(command))
		);
	}

	private static Command<ServerCommandSource> forEntityAndBlockEntity(
			String blockName, String entityName,
			CommandWithParam<CommandContext<ServerCommandSource>, EntityOrBlockEntity> action
	) {
		return ctx -> {
			try {
				BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, blockName);
				var blockEntity = ctx.getSource().getWorld().getBlockEntity(pos);
				if (blockEntity == null) {
					throw NOT_BLOCK_ENTITY.create();
				}
				return action.run(ctx, new EntityOrBlockEntity(blockEntity));
			} catch (IllegalArgumentException err) {
				try {
					Entity entity = EntityArgumentType.getEntity(ctx, entityName);
					return action.run(ctx, new EntityOrBlockEntity(entity));
				} catch (IllegalArgumentException err2) {
					throw new IllegalArgumentException(
							err.getMessage() + " and " + err2.getMessage()
					);
				}
			}
		};
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("container").requires(
						Permissions.require("metacraft.container", 2)
				).then(
					literal("container").then(
						literal("add").then(
							blockAndEntity(
								containerGroup("group"), "block", "entity", containerType("type").executes(
									forEntityAndBlockEntity("block", "entity", (ctx, entity) -> {
										var type = getContainerType(ctx, "type");
										String group = StringArgumentType.getString(ctx, "group");
										if (LootAccess.getInventory(entity).isPresent()) {
											LootContainerData.getInstance(ctx.getSource().getServer()).putLootContainer(
													group, ctx.getSource().getWorld().getRegistryKey(), entity, type.createDefault()
											);
											ctx.getSource().sendFeedback(() -> Text.literal("Added container for " + entity.map(
													Entity::getNameForScoreboard, BlockEntity::getPos
											)), true);
											return 1;
										} else {
											ctx.getSource().sendFeedback(
													() -> Text.literal("That is not a block entity with an inventory."),
													false
											);
											return 0;
										}
									})
								), argument("parameters", NbtCompoundArgumentType.nbtCompound()).executes(
									forEntityAndBlockEntity("block", "entity", (ctx, entity) -> {
										var container = parseCodec(ctx, "parameters", LootContainer.REGISTRY_CODEC);
										String group = StringArgumentType.getString(ctx, "group");
										if (LootAccess.getInventory(entity).isPresent()) {
											LootContainerData.getInstance(ctx.getSource().getServer()).putLootContainer(
													group, ctx.getSource().getWorld().getRegistryKey(), entity, container
											);
											ctx.getSource().sendFeedback(() -> Text.literal("Added container for " + entity.map(
													Entity::getNameForScoreboard, BlockEntity::getPos
											)), true);
											return 1;
										} else {
											ctx.getSource().sendFeedback(
													() -> Text.literal("That is not a block entity with an inventory."),
													false
											);
											return 0;
										}
									})
								)
							)
						)
					).then(
						literal("get").then(
							blockAndEntity(
								containerGroup("group"), "block", "entity",
								forEntityAndBlockEntity("block", "entity", (ctx, entity) -> {
									String group = StringArgumentType.getString(ctx, "group");
									var container = LootContainerData.getInstance(ctx.getSource().getServer()).getLootContainer(
											group, ctx.getSource().getWorld().getRegistryKey(), entity
									);
									ctx.getSource().sendFeedback(container::toText, false);
									return 1;
								})
							)
						)
					).then(
						literal("list").then(
							containerGroup("group").executes(ctx -> {
								String group = StringArgumentType.getString(ctx, "group");
								ctx.getSource().sendFeedback(() -> Text.literal(
										LootContainerData.getInstance(ctx.getSource().getServer()).getAllLootContainers(group).map(
												container -> container.getPos().toString()
										).collect(Collectors.joining("\n"))
								), false);
								return 1;
							})
						)
					)
				).then(
					literal("event").then(
						literal("add").then(
							containerGroup("group").then(
								containerEventType("event").executes(ctx -> {
									var type = getContainerEventType(ctx, "event");
									String group = StringArgumentType.getString(ctx, "group");
									LootContainerData.getInstance(ctx.getSource().getServer()).addEvent(
											group, type.createDefault()
									);
									ctx.getSource().sendFeedback(() -> Text.literal(
											"Added new event to " + group
									), true);
									return 1;
								})
							).then(
								argument("parameters", NbtCompoundArgumentType.nbtCompound()).executes(ctx -> {
									String group = StringArgumentType.getString(ctx, "group");
									var event = parseCodec(ctx, "parameters", LootContainerEvent.REGISTRY_CODEC);
									LootContainerData.getInstance(ctx.getSource().getServer()).addEvent(
											group, event
									);
									ctx.getSource().sendFeedback(() -> Text.literal(
											"Added new event to " + group
									), true);
									return 1;
								})
							)
						)
					).then(
						literal("remove").then(
							containerGroup("group").then(
								argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
									String group = StringArgumentType.getString(ctx, "group");
									int index = IntegerArgumentType.getInteger(ctx, "index");
									var data = LootContainerData.getInstance(ctx.getSource().getServer());
									if (data.getEvents(group).size() > index) {
										var event = data.removeEvent(group, index);
										ctx.getSource().sendFeedback(
												() -> Text.literal(
														"Removed " + event
												), true
										);
										return 1;
									} else {
										ctx.getSource().sendError(Text.literal("Index too large"));
										return 0;
									}
								})
							)
						)
					).then(
						literal("get").then(
							containerGroup("group").then(
								argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
									String group = StringArgumentType.getString(ctx, "group");
									int index = IntegerArgumentType.getInteger(ctx, "index");
									var data = LootContainerData.getInstance(ctx.getSource().getServer());
									if (data.getEvents(group).size() > index) {
										var event = data.getEvent(group, index);
										ctx.getSource().sendFeedback(
												event::toText, true
										);
										return 1;
									} else {
										ctx.getSource().sendError(Text.literal("Index too large"));
										return 0;
									}
								})
							)
						)
					).then(
						literal("list").then(
							containerGroup("group").executes(ctx -> {
								String group = StringArgumentType.getString(ctx, "group");
								StringBuilder builder = new StringBuilder();
								int index = 0;
								builder.append("Events: \n");
								for (var event : LootContainerData.getInstance(ctx.getSource().getServer()).getEvents(group)) {
									builder.append(index).append(": ").append(event).append("\n");
									index++;
								}
								ctx.getSource().sendFeedback(() -> Text.literal(builder.toString()), false);
								return 1;
							})
						)
					)
				)
			);
		});
	}


	private static ArgumentBuilder<ServerCommandSource, ?> containerGroup(String name) {
		return argument(name, StringArgumentType.string()).suggests(CONTAINER_GROUP_SUGGESTIONS);
	}

	private static ArgumentBuilder<ServerCommandSource, ?> containerType(String name) {
		return argument(name, IdentifierArgumentType.identifier()).suggests(CONTAINER_TYPE_SUGGESTIONS);
	}

	private static ArgumentBuilder<ServerCommandSource, ?> containerEventType(String name) {
		return argument(name, IdentifierArgumentType.identifier()).suggests(CONTAINER_EVENT_SUGGESTIONS);
	}

	private static LootContainerType<?> getContainerType(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		var id = IdentifierArgumentType.getIdentifier(ctx, name);
		var type = LootContainerRegistry.REGISTRY.get(id);
		if (type != null) {
			return type;
		} else {
			throw INVALID_TYPE.create(id);
		}
	}

	private static LootContainerEventType<?> getContainerEventType(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		var id = IdentifierArgumentType.getIdentifier(ctx, name);
		var type = LootContainerEventRegistry.REGISTRY.get(id);
		if (type != null) {
			return type;
		} else {
			throw INVALID_EVENT_TYPE.create(id);
		}
	}

	private static <T> T parseCodec(CommandContext<ServerCommandSource> ctx, String name, Codec<T> codec) throws CommandSyntaxException {
		return codec.parse(NbtOps.INSTANCE, NbtCompoundArgumentType.getNbtCompound(ctx, name)).resultOrPartial(
				error -> ctx.getSource().sendError(Text.literal(error))
		).orElseThrow(NBT_ERROR::create);
	}

	public interface CommandWithParam<T, E> {
		int run(T a, E b) throws CommandSyntaxException;
	}
}
