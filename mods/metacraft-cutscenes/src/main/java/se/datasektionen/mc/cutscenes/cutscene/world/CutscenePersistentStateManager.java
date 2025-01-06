package se.datasektionen.mc.cutscenes.cutscene.world;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Unit;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class CutscenePersistentStateManager extends PersistentStateManager {

	private final Supplier<NbtCompound> storage;
	private final DataFixer dataFixer;
	private final RegistryWrapper.WrapperLookup lookup;
	private final Map<String, PersistentState> loadedStates = Maps.newHashMap();

	public CutscenePersistentStateManager(
			Path directory, DataFixer dataFixer,
			RegistryWrapper.WrapperLookup registries,
			Supplier<NbtCompound> storage
	) {
		super(directory, dataFixer, registries);
		this.dataFixer = dataFixer;
		this.storage = storage;
		this.lookup = registries;
	}

	@Override
	public <T extends PersistentState> T getOrCreate(PersistentState.Type<T> type, String id) {
		var result = get(type, id);
		if (result == null) {
			set(id, type.constructor().get());
			return get(type, id);
		}
		return result;
	}

	@Override
	public <T extends PersistentState> T get(PersistentState.Type<T> type, String id) {
		if (!loadedStates.containsKey(id) && storage.get().contains(id)) {
			NbtCompound data = readNbt(id, type.type(), SharedConstants.getGameVersion().getSaveVersion().getId());
			var loaded = type.deserializer().apply(data.getCompound("data"), lookup);
			set(id, loaded);
		}
		return (T) loadedStates.get(id);
	}

	@Override
	public void set(String id, PersistentState state) {
		this.loadedStates.put(id, state);
	}

	@Override
	public NbtCompound readNbt(String id, DataFixTypes dataFixTypes, int currentSaveVersion) {
		var data = storage.get().getCompound(id);
		int version = NbtHelper.getDataVersion(data, 1343);
		return dataFixTypes.update(dataFixer, data, version, currentSaveVersion);
	}

	@Override
	public CompletableFuture<?> startSaving() {
		for (var entry : loadedStates.entrySet()) {
			if (entry.getValue().isDirty()) {
				storage.get().put(entry.getKey(), entry.getValue().toNbt(lookup));
			}
		}
		return CompletableFuture.completedFuture(Unit.INSTANCE);
	}

	public void saveAndReload() {
		save();
		loadedStates.clear();
	}
}
