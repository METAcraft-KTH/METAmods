package nu.metacraft.cutscenes.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DimensionDataStorage.class)
public interface DimensionDataStorageAccessor {

	@Invoker
	<T extends SavedData> CompoundTag callEncodeUnchecked(SavedDataType<T> type, SavedData state, RegistryOps<Tag> ops);

}
