package se.datasektionen.mc.cutscenes.cutscene.world;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Unit;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.mixin.AccessorPersistentStateManager;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class CutscenePersistentStateManager extends PersistentStateManager {

	private final Supplier<NbtCompound> storage;
	private final DataFixer dataFixer;
	private final RegistryWrapper.WrapperLookup lookup;
	private final Map<PersistentStateType<?>, PersistentState> loadedStates = Maps.newHashMap();

	protected final PersistentState.Context context;

	public CutscenePersistentStateManager(
			PersistentState.Context context,
			Path directory, DataFixer dataFixer,
			RegistryWrapper.WrapperLookup registries,
			Supplier<NbtCompound> storage
	) {
		super(context, directory, dataFixer, registries);
		this.context = context;
		this.dataFixer = dataFixer;
		this.storage = storage;
		this.lookup = registries;
	}

	@Override
	public <T extends PersistentState> T getOrCreate(PersistentStateType<T> type) {
		var result = get(type);
		if (result == null) {
			set(type, type.constructor().apply(context));
			return get(type);
		}
		return result;
	}

	@Override
	public <T extends PersistentState> T get(PersistentStateType<T> type) {
		if (!loadedStates.containsKey(type) && storage.get().contains(type.id())) {
			NbtCompound data = readNbt(type.id(), type.dataFixType(), SharedConstants.getGameVersion().dataVersion().id());
			var loaded = type.codec().apply(context).parse(
					lookup.getOps(NbtOps.INSTANCE), data.get("data")
			).resultOrPartial(
					(string) -> Cutscenes.LOGGER.error("Failed to parse saved data for '{}': {}", type, string)
			).orElse(null);
			set(type, loaded);
		}
		return (T) loadedStates.get(type);
	}

	@Override
	public <T extends PersistentState> void set(PersistentStateType<T> type, T state) {
		this.loadedStates.put(type, state);
	}

	@Override
	public NbtCompound readNbt(String id, DataFixTypes dataFixTypes, int currentSaveVersion) {
		var data = storage.get().getCompoundOrEmpty(id);
		int version = NbtHelper.getDataVersion(data, 1343);
		return dataFixTypes.update(dataFixer, data, version, currentSaveVersion);
	}

	@Override
	public CompletableFuture<?> startSaving() {
		var ops = lookup.getOps(NbtOps.INSTANCE);
		for (var entry : loadedStates.entrySet()) {
			if (entry.getValue().isDirty()) {
				storage.get().put(
						entry.getKey().id(),
						((AccessorPersistentStateManager) this).callEncode(entry.getKey(), entry.getValue(), ops)
				);
			}
		}
		return CompletableFuture.completedFuture(Unit.INSTANCE);
	}

	public void saveAndReload() {
		save();
		loadedStates.clear();
	}
}
