package nu.metacraft.lib.commands;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.DSL;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.datafix.fixes.References;
import nu.metacraft.lib.mixin.NbtPathAccessor;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Note, heavily based on {@link DataCommands}.
 */
public class DataFixerCommands {

	private static final DynamicCommandExceptionType NOT_TYPE_REFERENCE = new DynamicCommandExceptionType(
			path -> () -> path + " is not a registered type reference"
	);

	private static final DynamicCommandExceptionType NO_VERSION_FOUND = new DynamicCommandExceptionType(
			path -> () -> path + " does not contain a valid data version"
	);

	private static final DynamicCommandExceptionType ERROR_EXPECTED_OBJECT = new DynamicCommandExceptionType(
			object -> Component.translatableEscape("commands.data.modify.expected_object", object)
	);

	private static final SimpleCommandExceptionType ERROR_MERGE_UNCHANGED = new SimpleCommandExceptionType(Component.translatable("commands.data.merge.failed"));

	public static final Map<String, DSL.TypeReference> TYPE_REFERENCES = new HashMap<>();

	static {
		for (var field : References.class.getFields()) {
			if (field.getType() == DSL.TypeReference.class) {
				try {
					var value = (DSL.TypeReference) field.get(null);
					if (value != null) {
						TYPE_REFERENCES.put(value.typeName(), value);
					}
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private static ArgumentBuilder<CommandSourceStack, ?> typeReference(String name) {
		return argument(name, StringArgumentType.string()).suggests(
				(ctx, builder ) -> SharedSuggestionProvider.suggest(
						TYPE_REFERENCES.keySet(), builder
				)
		);
	}

	private static DSL.TypeReference getTypeReference(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		var key = StringArgumentType.getString(ctx, name);
		if (TYPE_REFERENCES.containsKey(key)) {
			return TYPE_REFERENCES.get(key);
		} else {
			throw NOT_TYPE_REFERENCE.create(key);
		}
	}

	private static int update(
			CommandContext<CommandSourceStack> ctx,
			DataCommands.DataProvider targetProvider, DataCommands.DataProvider sourceProvider,
			DataManipulator targetManipulator,
			NbtPathArgument.NbtPath targetPath, NbtPathArgument.NbtPath sourcePath,
			IntList sourceDataVersions, DSL.TypeReference typeReference
	) throws CommandSyntaxException {
		var targetAccess = targetProvider.access(ctx);
		var sourceTags = sourcePath.get(sourceProvider.access(ctx).getData());
		MutableInt index = new MutableInt(0);
		var fixedSourceTags = sourceTags.stream().map(
				tag -> ctx.getSource().getServer().getFixerUpper().update(
						typeReference, new Dynamic<>(NbtOps.INSTANCE, tag),
						sourceDataVersions.getInt(
								index.intValue() < sourceDataVersions.size() ? index.getAndIncrement() : sourceDataVersions.size()-1
						), SharedConstants.getCurrentVersion().dataVersion().version()
				).getValue()
		).toList();
		var data = targetAccess.getData();
		int result = targetManipulator.modify(ctx, data, targetPath, fixedSourceTags);
		if (result == 0) {
			throw ERROR_MERGE_UNCHANGED.create();
		} else {
			targetAccess.setData(data);
			ctx.getSource().sendSuccess(targetAccess::getModifiedSuccess, true);
			return result;
		}
	}

	private static int update(
			CommandContext<CommandSourceStack> ctx,
			DataCommands.DataProvider targetProvider, DataCommands.DataProvider sourceProvider,
			DataManipulator targetManipulator,
			NbtPathArgument.NbtPath targetPath, NbtPathArgument.NbtPath sourcePath,
			NbtPathArgument.NbtPath versionPath, DSL.TypeReference typeReference
	) throws CommandSyntaxException {
		var versionAccess = sourceProvider.access(ctx);
		var versionTagList = IntArrayList.toList(
				versionPath.get(versionAccess.getData()).stream().filter(
						num -> num instanceof NumericTag
				).mapToInt(num -> ((NumericTag) num).intValue())
		);
		if (versionTagList.isEmpty()) {
			throw NO_VERSION_FOUND.create(versionPath);
		}

		return update(
				ctx, targetProvider, sourceProvider, targetManipulator,
				targetPath, sourcePath, versionTagList, typeReference
		);
	}

	private static int setFromListPerEntry(NbtPathArgument.NbtPath path, Tag main, List<Tag> tagsToApply) throws CommandSyntaxException {
		var accessor = (NbtPathAccessor) path;
		var nodes = accessor.getNodes();
		for (var tag : tagsToApply) {
			if (NbtPathArgument.NbtPath.isTooDeep(tag, accessor.callEstimatePathDepth())) {
				throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
			}
		}
		List<Tag> list = accessor.callGetOrCreateParents(main);
		if (list.isEmpty()) {
			return 0;
		} else {
			NbtPathArgument.Node node = nodes[nodes.length - 1];
			MutableInt index = new MutableInt(0);
			return NbtPathAccessor.callApply(
					list, tag2x -> node.setTag(tag2x, () -> {
						if (index.intValue() > tagsToApply.size()) {
							return new CompoundTag();
						}
						var result = tagsToApply.get(index.intValue());
						index.increment();
						return result;
					})
			);
		}
	}

	private static NbtPathArgument.NbtPath getDefaultVersionPath(NbtPathArgument.NbtPath sourcePath) throws CommandSyntaxException {
		return NbtPathArgument.NbtPath.of(sourcePath.asString() + ".DataVersion");
	}

	private static ArgumentBuilder<CommandSourceStack, ?> decorateArgs(
			ArgumentBuilder<CommandSourceStack, ?> root,
			DataManipulatorDecorator decorator
	) {
		return root.then(
				Commands.literal("insert")
						.then(
								Commands.argument("index", IntegerArgumentType.integer())
										.then(
												decorator.create(
														(commandContext, compoundTag, nbtPath, list) -> nbtPath.insert(IntegerArgumentType.getInteger(commandContext, "index"), compoundTag, list)
												)
										)
						)
		)
		.then(
				Commands.literal("prepend").then(decorator.create((commandContext, compoundTag, nbtPath, list) -> nbtPath.insert(0, compoundTag, list)))
		)
		.then(
				Commands.literal("append").then(decorator.create((commandContext, compoundTag, nbtPath, list) -> nbtPath.insert(-1, compoundTag, list)))
		)
		.then(
				Commands.literal("set")
						.then(decorator.create((commandContext, compoundTag, nbtPath, list) -> nbtPath.set(compoundTag, Iterables.getLast(list))))
		)
		.then(Commands.literal("merge").then(decorator.create((commandContext, compoundTag, nbtPath, list) -> {
			CompoundTag compoundTag2 = new CompoundTag();

			for (Tag tag : list) {
				if (NbtPathArgument.NbtPath.isTooDeep(tag, 0)) {
					throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
				}

				if (!(tag instanceof CompoundTag compoundTag3)) {
					throw ERROR_EXPECTED_OBJECT.create(tag);
				}

				compoundTag2.merge(compoundTag3);
			}

			Collection<Tag> collection = nbtPath.getOrCreate(compoundTag, CompoundTag::new);
			int i = 0;

			for (Tag tag2 : collection) {
				if (!(tag2 instanceof CompoundTag compoundTag4)) {
					throw ERROR_EXPECTED_OBJECT.create(tag2);
				}

				CompoundTag compoundTag5 = compoundTag4.copy();
				compoundTag4.merge(compoundTag2);
				i += compoundTag5.equals(compoundTag4) ? 0 : 1;
			}

			return i;
		})));
	}

	private static ArgumentBuilder<CommandSourceStack, ?> createUpdateCommand() {
		final String typeRefName = "type_reference";
		final String pathName = "path";
		final String sourcePathName = "source_path";
		final String targetPathName = "target_path";
		final String versionPathName = "version_path";
		final String fromVersionName = "from_version";
		var typeRef = typeReference(typeRefName);
		for (var target : DataCommands.TARGET_PROVIDERS) {
			target.wrap(
					typeRef,
					u -> {
						u.then(
							argument(pathName, NbtPathArgument.nbtPath()).then(
									argument("from_version", IntegerArgumentType.integer()).executes(ctx -> {
										var path = NbtPathArgument.getPath(ctx, pathName);
										return update(
												ctx, target, target,
												(c, t, p, tags) -> setFromListPerEntry(p, t, tags),
												path, path, IntList.of(IntegerArgumentType.getInteger(ctx, fromVersionName)),
												getTypeReference(ctx, typeRefName)
										);
									})
							).executes(ctx -> {
								var path = NbtPathArgument.getPath(ctx, pathName);
								return update(
										ctx, target, target,
										(c, t, p, tags) -> setFromListPerEntry(p, t, tags),
										path, path, getDefaultVersionPath(path),
										getTypeReference(ctx, typeRefName)
								);
							}).then(
									literal("load-version").then(
											argument(versionPathName, NbtPathArgument.nbtPath()).executes(ctx -> {
												var path = NbtPathArgument.getPath(ctx, pathName);
												var versionPath = NbtPathArgument.getPath(ctx, versionPathName);
												return update(
														ctx, target, target,
														(c, t, p, tags) -> setFromListPerEntry(p, t, tags),
														path, path, versionPath,
														getTypeReference(ctx, typeRefName)
												);
											})
									)
							)
						);

						for (var source : DataCommands.SOURCE_PROVIDERS) {
							u.then(
								argument(sourcePathName, NbtPathArgument.nbtPath()).then(
									source.wrap(
										literal("from"),
										v -> decorateArgs(
											v, manipulator -> argument(targetPathName, NbtPathArgument.nbtPath()).then(
												argument("from_version", IntegerArgumentType.integer()).executes(ctx -> {
													var targetPath = NbtPathArgument.getPath(ctx, targetPathName);
													var sourceProvider = NbtPathArgument.getPath(ctx, sourcePathName);
													return update(
															ctx, target, source,
															manipulator,
															targetPath, sourceProvider, IntList.of(IntegerArgumentType.getInteger(ctx, "from_version")),
															getTypeReference(ctx, typeRefName)
													);
												})
											).executes(ctx -> {
												var path = NbtPathArgument.getPath(ctx, targetPathName);
												var sourcePath = NbtPathArgument.getPath(ctx, sourcePathName);
												return update(
														ctx, target, source,
														manipulator,
														path, sourcePath, getDefaultVersionPath(sourcePath),
														getTypeReference(ctx, typeRefName)
												);
											}).then(
												literal("load-version").then(
													argument(versionPathName, NbtPathArgument.nbtPath()).executes(ctx -> {
														var path = NbtPathArgument.getPath(ctx, targetPathName);
														var sourcePath = NbtPathArgument.getPath(ctx, sourcePathName);
														var versionPath = NbtPathArgument.getPath(ctx, versionPathName);
														return update(
															ctx, target, source,
															manipulator,
															path, sourcePath, versionPath,
															getTypeReference(ctx, typeRefName)
														);
													})
												)
											)
										)
									)
								)
							);
						}

						return u;
					}
			);
		}
		return literal("update").then(typeRef);
	}

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
				literal("datafixer").requires(p -> p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).then(
						literal("version").executes(ctx -> {
							var dataVersion = SharedConstants.getCurrentVersion().dataVersion().version();
							ctx.getSource().sendSuccess(() -> Component.literal("The current data version is " + dataVersion), false);
							return dataVersion;
						})
				).then(
						createUpdateCommand()
				)
		);
	}

	@FunctionalInterface
	public interface DataManipulator {
		int modify(CommandContext<CommandSourceStack> commandContext, CompoundTag compoundTag, NbtPathArgument.NbtPath nbtPath, List<Tag> sourceTags) throws CommandSyntaxException;
	}

	@FunctionalInterface
	interface DataManipulatorDecorator {
		ArgumentBuilder<CommandSourceStack, ?> create(DataManipulator dataManipulator);
	}
}
