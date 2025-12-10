package nu.metacraft.core.util;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.List;
import java.util.function.Function;

public class SynchedDataHelper {


	public static <T> void replace(List<SynchedEntityData.DataValue<?>> data, int current, EntityDataAccessor<? extends T> from, EntityDataAccessor<T> to) {
		replace(data, current, from, to, t -> t);
	}

	public static <T, U> void replace(List<SynchedEntityData.DataValue<?>> data, int current, EntityDataAccessor<T> from, EntityDataAccessor<U> to, Function<T, U> converter) {
		var existing = data.get(current);
		if (existing.id() == from.id() && existing.serializer() == from.serializer()) {
			data.remove(current);
			data.add(current, SynchedEntityData.DataValue.create(to, converter.apply((T) existing.value())));
		}
	}

}
