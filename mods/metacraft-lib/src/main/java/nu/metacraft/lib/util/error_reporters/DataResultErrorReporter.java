package nu.metacraft.lib.util.error_reporters;

import com.mojang.serialization.DataResult;
import net.minecraft.util.ErrorReporter;

public class DataResultErrorReporter extends ErrorReporter.Impl {

	public DataResultErrorReporter() {

	}

	public DataResultErrorReporter(ErrorReporter.Context context) {
		super(context);
	}

	public static DataResultErrorReporter create(ErrorReporter.Context context) {
		return new DataResultErrorReporter(context);
	}

	private <T> DataResult<T> error(T object) {
		return DataResult.error(this::getErrorsAsString, object);
	}

	public <T> DataResult<T> wrap(T object) {
		if (isEmpty()) {
			return DataResult.success(object);
		} else {
			return error(object);
		}
	}

	public <T> DataResult<T> wrap(DataResult<T> result) {
		if (isEmpty()) {
			return result;
		} else {
			return result.flatMap(this::error);
		}
	}

}
