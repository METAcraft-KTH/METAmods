package nu.metacraft.lib.util.helper;

import com.mojang.serialization.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.ReadView;
import org.jetbrains.annotations.NotNull;
import nu.metacraft.lib.mixin.AccessorNbtReadView;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class ViewHelper {

	public static int getSize(ReadView view) {
		var dynamic = getDynamic(view);
		return dynamic.asMapOpt().result().map(Stream::count).or(
				() -> dynamic.asStreamOpt().result().map(Stream::count)
		).orElse(0L).intValue();
	}

	public static Dynamic<?> getDynamic(ReadView view) {
		if (view instanceof NbtReadView v) {
			return new Dynamic<>(view.getRegistries().getOps(NbtOps.INSTANCE), ((AccessorNbtReadView) v).getNbt());
		}
		if (view instanceof ExtendedReadView v) {
			return v.getData();
		}
		return new Dynamic<>(view.getRegistries().getOps(JavaOps.INSTANCE));
	}

	public static <T> T getContents(ReadView view, DynamicOps<T> ops) {
		var dynamic = getDynamic(view);
		var targetOps = ops;
		if (dynamic.getOps() instanceof RegistryOps<?> r && !(targetOps instanceof RegistryOps<T>)) {
			targetOps = r.withDelegate(targetOps);
		}
		return dynamic.convert(targetOps).getValue();
	}

	public static NbtCompound getNBT(ReadView view) {
		return getContents(view, NbtOps.INSTANCE).asCompound().orElseGet(NbtCompound::new);
	}

	public static final ReadView.ListReadView EMPTY_LIST = new ReadView.ListReadView() {
		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Stream<ReadView> stream() {
			return Stream.empty();
		}

		@Override
		public @NotNull Iterator<ReadView> iterator() {
			return stream().iterator();
		}
	};

	private static final ReadView.TypedListReadView<?> EMPTY_TYPED_LIST = new ReadView.TypedListReadView<Object>() {
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
	public static <T> ReadView.TypedListReadView<T> emptyTypedList() {
		return (ReadView.TypedListReadView<T>) EMPTY_TYPED_LIST;
	}

	public static ReadView empty(RegistryWrapper.WrapperLookup lookup) {
		return new ReadView() {
			@Override
			public <T> Optional<T> read(String key, Codec<T> codec) {
				return Optional.empty();
			}

			@Override
			public <T> Optional<T> read(MapCodec<T> mapCodec) {
				return Optional.empty();
			}

			@Override
			public Optional<ReadView> getOptionalReadView(String key) {
				return Optional.empty();
			}

			@Override
			public ReadView getReadView(String key) {
				return this;
			}

			@Override
			public Optional<ListReadView> getOptionalListReadView(String key) {
				return Optional.empty();
			}

			@Override
			public ListReadView getListReadView(String key) {
				return EMPTY_LIST;
			}

			@Override
			public <T> Optional<TypedListReadView<T>> getOptionalTypedListView(String key, Codec<T> typeCodec) {
				return Optional.empty();
			}

			@Override
			public <T> TypedListReadView<T> getTypedListView(String key, Codec<T> typeCodec) {
				return emptyTypedList();
			}

			@Override
			public boolean getBoolean(String key, boolean fallback) {
				return fallback;
			}

			@Override
			public byte getByte(String key, byte fallback) {
				return fallback;
			}

			@Override
			public int getShort(String key, short fallback) {
				return fallback;
			}

			@Override
			public Optional<Integer> getOptionalInt(String key) {
				return Optional.empty();
			}

			@Override
			public int getInt(String key, int fallback) {
				return fallback;
			}

			@Override
			public long getLong(String key, long fallback) {
				return fallback;
			}

			@Override
			public Optional<Long> getOptionalLong(String key) {
				return Optional.empty();
			}

			@Override
			public float getFloat(String key, float fallback) {
				return fallback;
			}

			@Override
			public double getDouble(String key, double fallback) {
				return fallback;
			}

			@Override
			public Optional<String> getOptionalString(String key) {
				return Optional.empty();
			}

			@Override
			public String getString(String key, String fallback) {
				return fallback;
			}

			@Override
			public Optional<int[]> getOptionalIntArray(String key) {
				return Optional.empty();
			}

			@Override
			public RegistryWrapper.WrapperLookup getRegistries() {
				return lookup;
			}
		};
	}

	public static ReadView filtered(ReadView view, Set<String> toRemove) {
		return new FilteredReadView(view, toRemove);
	}

	public interface ExtendedReadView {
		Dynamic<?> getData();
	}

	public static class FilteredReadView implements ReadView, ExtendedReadView {

		private final ReadView view;
		private final Set<String> toRemove;
		private final ReadView empty;

		public FilteredReadView(ReadView view, Set<String> toRemove) {
			this.view = view;
			this.toRemove = toRemove;
			this.empty = empty(view.getRegistries());
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
		public Optional<ReadView> getOptionalReadView(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getOptionalReadView(key);
		}

		@Override
		public ReadView getReadView(String key) {
			if (toRemove.contains(key)) return empty;
			return view.getReadView(key);
		}

		@Override
		public Optional<ListReadView> getOptionalListReadView(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getOptionalListReadView(key);
		}

		@Override
		public ListReadView getListReadView(String key) {
			if (toRemove.contains(key)) return EMPTY_LIST;
			return view.getListReadView(key);
		}

		@Override
		public <T> Optional<TypedListReadView<T>> getOptionalTypedListView(String key, Codec<T> typeCodec) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getOptionalTypedListView(key, typeCodec);
		}

		@Override
		public <T> TypedListReadView<T> getTypedListView(String key, Codec<T> typeCodec) {
			if (toRemove.contains(key)) return emptyTypedList();
			return view.getTypedListView(key, typeCodec);
		}

		@Override
		public boolean getBoolean(String key, boolean fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getBoolean(key, fallback);
		}

		@Override
		public byte getByte(String key, byte fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getByte(key, fallback);
		}

		@Override
		public int getShort(String key, short fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getShort(key, fallback);
		}

		@Override
		public Optional<Integer> getOptionalInt(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getOptionalInt(key);
		}

		@Override
		public int getInt(String key, int fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getInt(key, fallback);
		}

		@Override
		public long getLong(String key, long fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getLong(key, fallback);
		}

		@Override
		public Optional<Long> getOptionalLong(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getOptionalLong(key);
		}

		@Override
		public float getFloat(String key, float fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getFloat(key, fallback);
		}

		@Override
		public double getDouble(String key, double fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getDouble(key, fallback);
		}

		@Override
		public Optional<String> getOptionalString(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getOptionalString(key);
		}

		@Override
		public String getString(String key, String fallback) {
			if (toRemove.contains(key)) return fallback;
			return view.getString(key, fallback);
		}

		@Override
		public Optional<int[]> getOptionalIntArray(String key) {
			if (toRemove.contains(key)) return Optional.empty();
			return view.getOptionalIntArray(key);
		}

		@Override
		public RegistryWrapper.WrapperLookup getRegistries() {
			return view.getRegistries();
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
