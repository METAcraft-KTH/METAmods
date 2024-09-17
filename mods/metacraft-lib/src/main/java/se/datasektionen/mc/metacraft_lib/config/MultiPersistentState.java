package se.datasektionen.mc.metacraft_lib.config;

import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.PathUtil;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class MultiPersistentState<T extends CustomPersistentType> extends CustomPersistentType {

	protected final Map<String, T> states;
	private final List<Path> toRemove = new ArrayList<>();

	public MultiPersistentState(Map<String, T> states) {
		this.states = states;
	}

	@Override
	public void save(RegistryWrapper.WrapperLookup lookup) {
		var f = filePath.toFile();
		if (!f.exists()) {
			f.mkdirs();
		}
		for (var r : toRemove) {
			try {
				Files.delete(r);
			} catch (IOException e) {
				METAcraftLib.LOGGER.error(e);
			}
		}
		toRemove.clear();
		for (var t : states.values()) {
			if (t.isDirty()) {
				t.save(lookup);
			}
		}
	}

	protected void prepare(String subType, T type) {
		var parser = ((MultiPersistentState.MultiParser<CustomPersistentType,?>) this.type.parser());
		String key = this.key + "/" + subType;
		type.prepare(world, parser.subType.parser().getPath(world, key), key, parser.subType);
	}

	@Override
	public void prepare(ServerWorld world, Path path, String key, CustomType<CustomPersistentType> parser, boolean create) {
		super.prepare(world, path, key, parser, create);
		if (create) {
			for (var e : states.entrySet()) {
				prepare(e.getKey(), e.getValue());
				e.getValue().markDirty();
			}
		}
	}

	private static String fixFileName(String name) {
		if (PathUtil.isFileNameValid(name)) {
			return name;
		} else {
			return PathUtil.replaceInvalidChars(name);
		}
	}

	public void add(String subType, T type) {
		subType = fixFileName(subType);
		states.put(subType, type);
		prepare(subType, type);
		type.markDirty();
		markDirty();
	}

	public void remove(String subType) {
		subType = fixFileName(subType);
		var t = states.remove(subType);
		toRemove.add(t.filePath);
		markDirty();
	}

	public T get(String subType) {
		subType = fixFileName(subType);
		return states.get(subType);
	}

	public static class MultiParser<T extends CustomPersistentType, M extends MultiPersistentState<T>> implements Parser<M> {

		private final CustomType<T> subType;
		private final Function<Map<String, T>, M> creator;

		public MultiParser(CustomType<T> subType, Function<Map<String, T>, M> creator) {
			this.subType = subType;
			this.creator = creator;
		}

		private String getSubExtension() {
			var ext = subType.parser().getFileExtension();
			if (ext.isEmpty()) {
				return "";
			} else {
				return "." + ext;
			}
		}

		@Override
		public Optional<M> parse(ServerWorld world, String key, CustomType<M> type) {
			var file = getPath(world, key).toFile();
			if (file.isDirectory()) {
				var list = file.listFiles((f, name) -> name.endsWith(getSubExtension()));
				if (list == null) return Optional.empty();
				Map<String, T> states = new HashMap<>();
				for (var f : list) {
					String subKey = f.getName().substring(0, f.getName().length()-5);
					MultiPersistentState.parse(
							world,
							key + "/" + subKey,
							subType
					).ifPresent(p -> states.put(subKey, p));
				}
				return Optional.of(creator.apply(states));
			} else {
				return Optional.empty();
			}
		}

		@Override
		public Path getPath(ServerWorld world, String key) {
			return MultiPersistentState.getPath(world, key);
		}

		@Override
		public String getFileExtension() {
			return "";
		}
	}
}
