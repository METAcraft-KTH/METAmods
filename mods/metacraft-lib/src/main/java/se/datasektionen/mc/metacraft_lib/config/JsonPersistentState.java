package se.datasektionen.mc.metacraft_lib.config;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;

import java.nio.file.Path;
import java.util.Optional;

public class JsonPersistentState extends CustomPersistentType {

	protected Codec<CustomPersistentType> codec;

	@Override
	public void prepare(ServerWorld world, Path path, String key, CustomType<CustomPersistentType> parser, boolean create) {
		super.prepare(world, path, key, parser, create);
		if (parser.parser() instanceof JsonParse<CustomPersistentType> p) {
			this.codec = p.codec;
		}
	}

	@Override
	public void save(RegistryWrapper.WrapperLookup lookup) {
		if (isDirty()) {
			JsonHelper.save(filePath, codec, this, lookup);
		}
	}

	public static class JsonParse<T extends CustomPersistentType> implements Parser<T> {

		private final Codec<T> codec;

		public JsonParse(Codec<T> codec) {
			this.codec = codec;
		}

		@Override
		public Optional<T> parse(ServerWorld world, String key, CustomType<T> type, Path root) {
			return JsonHelper.load(getPath(root, key), codec, world.getRegistryManager());
		}

		@Override
		public Path getPath(Path root, String key) {
			return JsonPersistentState.getPath(root, key + "." + getFileExtension());
		}

		@Override
		public T fromNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
			return codec.parse(lookup.getOps(NbtOps.INSTANCE), nbt).resultOrPartial(
					METAcraftLib.LOGGER::error
			).orElseThrow();
		}

		@Override
		public String getFileExtension() {
			return "json";
		}
	}
}
