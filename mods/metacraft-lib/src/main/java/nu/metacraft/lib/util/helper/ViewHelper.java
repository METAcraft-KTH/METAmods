package nu.metacraft.lib.util.helper;

import com.mojang.serialization.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.NotNull;
import nu.metacraft.lib.mixin.TagValueInputAccessor;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class ViewHelper {

	public static int getSize(ValueInput view) {
		var dynamic = getDynamic(view);
		return dynamic.asMapOpt().result().map(Stream::count).or(
				() -> dynamic.asStreamOpt().result().map(Stream::count)
		).orElse(0L).intValue();
	}

	public static Dynamic<?> getDynamic(ValueInput view) {
		if (view instanceof TagValueInput v) {
			return new Dynamic<>(view.lookup().createSerializationContext(NbtOps.INSTANCE), ((TagValueInputAccessor) v).getInput());
		}
		if (view instanceof ExtendedReadView v) {
			return v.getData();
		}
		return new Dynamic<>(view.lookup().createSerializationContext(JavaOps.INSTANCE));
	}

	public static <T> T getContents(ValueInput view, DynamicOps<T> ops) {
		var dynamic = getDynamic(view);
		var targetOps = ops;
		if (dynamic.getOps() instanceof RegistryOps<?> r && !(targetOps instanceof RegistryOps<T>)) {
			targetOps = r.withParent(targetOps);
		}
		return dynamic.convert(targetOps).getValue();
	}

	public static CompoundTag getNBT(ValueInput view) {
		return getContents(view, NbtOps.INSTANCE).asCompound().orElseGet(CompoundTag::new);
	}

	public static final ValueInput.ValueInputList EMPTY_LIST = new ValueInput.ValueInputList() {
		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Stream<ValueInput> stream() {
			return Stream.empty();
		}

		@Override
		public @NotNull Iterator<ValueInput> iterator() {
			return stream().iterator();
		}
	};

	private static final ValueInput.TypedInputList<?> EMPTY_TYPED_LIST = new ValueInput.TypedInputList<Object>() {
		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Stream<Object> stream() {
			return Stream.empty();
		}

		@Override
		public @NotNull Iterator<Object> iterator() {
			return stream().iterator();
		}
	};

	@SuppressWarnings("unchecked")
	public static <T> ValueInput.TypedInputList<T> emptyTypedList() {
		return (ValueInput.TypedInputList<T>) EMPTY_TYPED_LIST;
	}

	public static ValueInput empty(HolderLookup.Provider lookup) {
		return new ValueInput() {
			@Override
			public <T> Optional<T> read(String key, Codec<T> codec) {
				return Optional.empty();
			}

			@Override
			public <T> Optional<T> read(MapCodec<T> mapCodec) {
				return Optional.empty();
			}

			@Override
			public Optional<ValueInput> child(String key) {
				return Optional.empty();
			}

			@Override
			public ValueInput childOrEmpty(String key) {
				return this;
			}

			@Override
			public Optional<ValueInputList> childrenList(String key) {
				return Optional.empty();
			}

			@Override
			public ValueInputList childrenListOrEmpty(String key) {
				return EMPTY_LIST;
			}

			@Override
			public <T> Optional<TypedInputList<T>> list(String key, Codec<T> typeCodec) {
				return Optional.empty();
			}

			@Override
			public <T> TypedInputList<T> listOrEmpty(String key, Codec<T> typeCodec) {
				return emptyTypedList();
			}

			@Override
			public boolean getBooleanOr(String key, boolean fallback) {
				return fallback;
			}

			@Override
			public byte getByteOr(String key, byte fallback) {
				return fallback;
			}

			@Override
			public int getShortOr(String key, short fallback) {
				return fallback;
			}

			@Override
			public Optional<Integer> getInt(String key) {
				return Optional.empty();
			}

			@Override
			public int getIntOr(String key, int fallback) {
				return fallback;
			}

			@Override
			public long getLongOr(String key, long fallback) {
				return fallback;
			}

			@Override
			public Optional<Long> getLong(String key) {
				return Optional.empty();
			}

			@Override
			public float getFloatOr(String key, float fallback) {
				return fallback;
			}

			@Override
			public double getDoubleOr(String key, double fallback) {
				return fallback;
			}

			@Override
			public Optional<String> getString(String key) {
				return Optional.empty();
			}

			@Override
			public String getStringOr(String key, String fallback) {
				return fallback;
			}

			@Override
			public Optional<int[]> getIntArray(String key) {
				return Optional.empty();
			}

			@Override
			public HolderLookup.Provider lookup() {
				return lookup;
			}
		};
	}

	public static ValueInput filtered(ValueInput view, Set<String> toRemove) {
		return new FilteredReadView(view, toRemove);
	}

	public interface ExtendedReadView {
		Dynamic<?> getData();
	}

	public static class FilteredReadView implements ValueInput, ExtendedReadView {

		private final ValueInput view;
		private final Set<String> toRemove;
		private final ValueInput empty;

		public FilteredReadView(ValueInput view, Set<String> toRemove) {
			this.view = view;
			this.toRemove = toRemove;
			this.empty = empty(view.lookup());
		}

		@Override
		public <T> Optional<T> read(String key, Codec<T> codec) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.read(key, codec);
		}

		@Override
		public <T> Optional<T> read(MapCodec<T> mapCodec) {
			return view.read(mapCodec);
		}

		@Override
		public Optional<ValueInput> child(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.child(key);
		}

		@Override
		public ValueInput childOrEmpty(String key) {
			if (toRemove.contains(key)) return empty;
			return view.childOrEmpty(key);
		}

		@Override
		public Optional<ValueInputList> childrenList(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.childrenList(key);
		}

		@Override
		public ValueInputList childrenListOrEmpty(String key) {
			if (toRemove.contains(key)) return EMPTY_LIST;
			return view.childrenListOrEmpty(key);
		}

		@Override
		public <T> Optional<TypedInputList<T>> list(String key, Codec<T> typeCodec) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.list(key, typeCodec);
		}

		@Override
		public <T> TypedInputList<T> listOrEmpty(String key, Codec<T> typeCodec) {
			if (toRemove.contains(key)) return emptyTypedList();
			return view.listOrEmpty(key, typeCodec);
		}

		@Override
		public boolean getBooleanOr(String key, boolean fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getBooleanOr(key, fallback);
		}

		@Override
		public byte getByteOr(String key, byte fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getByteOr(key, fallback);
		}

		@Override
		public int getShortOr(String key, short fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getShortOr(key, fallback);
		}

		@Override
		public Optional<Integer> getInt(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getInt(key);
		}

		@Override
		public int getIntOr(String key, int fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getIntOr(key, fallback);
		}

		@Override
		public long getLongOr(String key, long fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getLongOr(key, fallback);
		}

		@Override
		public Optional<Long> getLong(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getLong(key);
		}

		@Override
		public float getFloatOr(String key, float fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getFloatOr(key, fallback);
		}

		@Override
		public double getDoubleOr(String key, double fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getDoubleOr(key, fallback);
		}

		@Override
		public Optional<String> getString(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getString(key);
		}

		@Override
		public String getStringOr(String key, String fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getStringOr(key, fallback);
		}

		@Override
		public Optional<int[]> getIntArray(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getIntArray(key);
		}

		@Override
		public HolderLookup.Provider lookup() {
			return view.lookup();
		}

		@Override
		public Dynamic<?> getData() {
			var dynamic = getDynamic(view);
			for (var removal : toRemove) {
				dynamic = dynamic.remove(removal);
			}
			return dynamic;
		}
	}

}
