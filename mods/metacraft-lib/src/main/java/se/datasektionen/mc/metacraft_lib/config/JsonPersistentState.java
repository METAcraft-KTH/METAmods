package se.datasektionen.mc.metacraft_lib.config;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorPersistentStateManager;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

public class JsonPersistentState extends PersistentState {

	protected Path filePath;
	protected Codec<JsonPersistentState> codec;
	protected String key;

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		return null;
	}

	protected static Path getPath(ServerWorld world, String key) {
		return ((AccessorPersistentStateManager) world.getPersistentStateManager()).getDirectory().toPath().resolve(key + ".json");
	}

	protected static <T extends JsonPersistentState> Optional<T> parse(
			ServerWorld world, String key, Codec<T> codec
	) {
		var path = getPath(world, key);
		return JsonHelper.load(path, codec, world.getRegistryManager()).map(
				c -> {
					c.prepare(world, path, key, downCast(codec));
					return c;
				}
		);
	}

	protected static <T extends JsonPersistentState> Optional<T> getLoadedConfig(
			ServerWorld world, JsonType<T> type, String key
	) {
		return Optional.ofNullable(world.getPersistentStateManager().get(type.createType(world, key), key));
	}

	protected static <T extends JsonPersistentState> Optional<T> getConfig(
			ServerWorld world, JsonType<T> type, String key
	) {
		var state = getLoadedConfig(world, type, key);
		if (state.isPresent()) return state;
		state = parse(world, key, type.codec());
		state.ifPresent(s -> world.getPersistentStateManager().set(key, s));
		return state;
	}

	protected static Codec<JsonPersistentState> downCast(Codec<? extends JsonPersistentState> codec) {
		return (Codec<JsonPersistentState>) codec;
	}

	protected static <T extends JsonPersistentState> T getOrCreateConfig(
			ServerWorld world, JsonType<T> type, String key
	) {
		return getConfig(world, type, key).orElseGet(
				() -> {
					var config = type.creator.get();
					config.markDirty();
					config.prepare(world, getPath(world, key), key, downCast(type.codec));
					world.getPersistentStateManager().set(key, config);
					return config;
				}
		);
	}

	public void prepare(ServerWorld world, Path path, String key, Codec<JsonPersistentState> codec) {
		this.filePath = path;
		this.key = key;
		this.codec = codec;
	}

	protected void reload(ServerWorld world) {
		parse(world, key, codec).ifPresent(result -> {
			world.getPersistentStateManager().set(key, result);
		});
	}

	@Override
	public void save(File file, RegistryWrapper.WrapperLookup registryLookup) {
		save(registryLookup);
	}

	public void save(RegistryWrapper.WrapperLookup lookup) {
		if (isDirty()) {
			JsonHelper.save(filePath, codec, this, lookup);
		}
	}

	public record JsonType<T extends JsonPersistentState>(
			Supplier<T> creator, Codec<T> codec
	) {
		public Type<T> createType(ServerWorld world, String key) {
			return new Type<>(
					() -> {
						var config = creator.get();
						config.prepare(world, getPath(world, key), key, downCast(codec));
						return config;
					},
					(nbt, lookup) -> {
						var config = codec.parse(lookup.getOps(NbtOps.INSTANCE), nbt).resultOrPartial(
								METAcraftLib.LOGGER::error
						).orElseGet(creator);
						config.prepare(world, getPath(world, key), key, downCast(codec));
						return config;
					},
					null
			);
		}
	}
}
