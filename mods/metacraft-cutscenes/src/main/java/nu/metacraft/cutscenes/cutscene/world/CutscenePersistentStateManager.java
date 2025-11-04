package nu.metacraft.cutscenes.cutscene.world;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Unit;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.mixin.AccessorPersistentStateManager;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class CutscenePersistentStateManager extends DimensionDataStorage {

	private final Supplier<CompoundTag> storage;
	private final DataFixer dataFixer;
	private final HolderLookup.Provider lookup;
	private final Map<SavedDataType<?>, SavedData> loadedStates = Maps.newHashMap();

	protected final SavedData.Context context;

	public CutscenePersistentStateManager(
			SavedData.Context context,
			Path directory, DataFixer dataFixer,
			HolderLookup.Provider registries,
			Supplier<CompoundTag> storage
	) {
		super(context, directory, dataFixer, registries);
		this.context = context;
		this.dataFixer = dataFixer;
		this.storage = storage;
		this.lookup = registries;
	}

	@Override
	public <T extends SavedData> T computeIfAbsent(SavedDataType<T> type) {
		var result = get(type);
		if (result == null) {
			set(type, type.constructor().apply(context));
			return get(type);
		}
		return result;
	}

	@Override
	public <T extends SavedData> T get(SavedDataType<T> type) {
		if (!loadedStates.containsKey(type) && storage.get().contains(type.id())) {
			CompoundTag data = readTagFromDisk(type.id(), type.dataFixType(), SharedConstants.getCurrentVersion().dataVersion().version());
			var loaded = type.codec().apply(context).parse(
					lookup.createSerializationContext(NbtOps.INSTANCE), data.get("data")
			).resultOrPartial(
					(string) -> Cutscenes.LOGGER.error("Failed to parse saved data for '{}': {}", type, string)
			).orElse(null);
			set(type, loaded);
		}
		return (T) loadedStates.get(type);
	}

	@Override
	public <T extends SavedData> void set(SavedDataType<T> type, T state) {
		this.loadedStates.put(type, state);
	}

	@Override
	public CompoundTag readTagFromDisk(String id, DataFixTypes dataFixTypes, int currentSaveVersion) {
		var data = storage.get().getCompoundOrEmpty(id);
		int version = NbtUtils.getDataVersion(data, 1343);
		return dataFixTypes.update(dataFixer, data, version, currentSaveVersion);
	}

	@Override
	public CompletableFuture<?> scheduleSave() {
		var ops = lookup.createSerializationContext(NbtOps.INSTANCE);
		for (var entry : loadedStates.entrySet()) {
			if (entry.getValue().isDirty()) {
				storage.get().put(
						entry.getKey().id(),
						((AccessorPersistentStateManager) this).callEncodeUnchecked(entry.getKey(), entry.getValue(), ops)
				);
			}
		}
		return CompletableFuture.completedFuture(Unit.INSTANCE);
	}

	public void saveAndReload() {
		saveAndJoin();
		loadedStates.clear();
	}
}
