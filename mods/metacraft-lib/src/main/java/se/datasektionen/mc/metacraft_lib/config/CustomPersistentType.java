package se.datasektionen.mc.metacraft_lib.config;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorPersistentStateManager;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class CustomPersistentType extends PersistentState {

	protected Path filePath;
	protected String key;
	protected CustomType<CustomPersistentType> type;
	protected ServerWorld world;

	static <T extends CustomPersistentType> Optional<T> parse(
			ServerWorld world, String key, CustomType<T> type, Path root
	) {
		return type.parser.parse(world, key, type, root).map(t -> {
			t.prepare(world, type.parser.getPath(root, key), key, downCast(type));
			return t;
		});
	}

	protected static Path getDefaultRoot(ServerWorld world) {
		return ((AccessorPersistentStateManager) world.getPersistentStateManager()).getDirectory().toPath();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		return null;
	}

	protected static Path getPath(Path root, String name) {
		return root.resolve(name);
	}

	protected static <T extends CustomPersistentType> Optional<T> getLoadedConfig(
			ServerWorld world, CustomType<T> type, String key
	) {
		return Optional.ofNullable(world.getPersistentStateManager().get(type.createType(world, key), key));
	}

	protected static <T extends CustomPersistentType> Optional<T> getConfig(
			ServerWorld world, CustomType<T> type, String key
	) {
		var state = getLoadedConfig(world, type, key);
		if (state.isPresent()) return state;
		state = parse(world, key, type, type.parser().getRoot(world));
		state.ifPresent(s -> world.getPersistentStateManager().set(key, s));
		return state;
	}

	protected static CustomType<CustomPersistentType> downCast(CustomType<? extends CustomPersistentType> parser) {
		return (CustomType<CustomPersistentType>) parser;
	}

	protected static <T extends CustomPersistentType> T getOrCreateConfig(
			ServerWorld world, CustomType<T> type, String key
	) {
		return getConfig(world, type, key).orElseGet(
				() -> {
					var config = type.creator.get();
					config.markDirty();
					config.prepare(world, getPath(type.parser().getRoot(world), key), key, downCast(type), true);
					world.getPersistentStateManager().set(key, config);
					return config;
				}
		);
	}

	public final void prepare(ServerWorld world, Path path, String key, CustomType<CustomPersistentType> type) {
		prepare(world, path, key, type, false);
	}

	public void prepare(ServerWorld world, Path path, String key, CustomType<CustomPersistentType> type, boolean create) {
		this.filePath = path;
		this.key = key;
		this.type = type;
		this.world = world;
	}

	public void reload() {
		parse(world, key, type, filePath.getParent()).ifPresent(result -> {
			world.getPersistentStateManager().set(key, result);
		});
	}

	@Override
	public void save(File file, RegistryWrapper.WrapperLookup registryLookup) {
		save(registryLookup);
	}

	public abstract void save(RegistryWrapper.WrapperLookup lookup);

	public record CustomType<T extends CustomPersistentType>(
			Supplier<T> creator, Parser<T> parser
	) {
		public Type<T> createType(ServerWorld world, String key) {
			return new Type<>(
					() -> {
						var config = creator.get();
						config.prepare(world, getPath(parser.getRoot(world), key), key, downCast(this));
						return config;
					},
					(nbt, lookup) -> {
						var config = parser.fromNBT(nbt, lookup);
						config.prepare(world, getPath(parser.getRoot(world), key), key, downCast(this));
						return config;
					},
					null
			);
		}
	}

	public interface Parser<T extends CustomPersistentType> {
		Optional<T> parse(ServerWorld world, String key, CustomType<T> type, Path root);
		Path getPath(Path root, String key);
		default Path getRoot(ServerWorld world) {
			return getDefaultRoot(world);
		}
		String getFileExtension();

		default T fromNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
			throw new IllegalStateException("NBT fallback not supported");
		}
	}
}
