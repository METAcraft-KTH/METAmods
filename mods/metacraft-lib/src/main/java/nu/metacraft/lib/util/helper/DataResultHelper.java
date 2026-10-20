package nu.metacraft.lib.util.helper;

import com.mojang.serialization.DataResult;

public class DataResultHelper {

	public static <T> DataResult<T> withPartialIfError(
		DataResult<T> result, T partial
	) {
		if (result.hasResultOrPartial()) return result;
		return result.setPartial(partial);
	}

}
