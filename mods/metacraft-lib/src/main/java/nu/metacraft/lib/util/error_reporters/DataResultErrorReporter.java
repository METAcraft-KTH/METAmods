package nu.metacraft.lib.util.error_reporters;

import com.mojang.serialization.DataResult;
import net.minecraft.util.ProblemReporter;

public class DataResultErrorReporter extends ProblemReporter.Collector {

	public DataResultErrorReporter() {

	}

	public DataResultErrorReporter(ProblemReporter.PathElement context) {
		super(context);
	}

	public static DataResultErrorReporter create(ProblemReporter.PathElement context) {
		return new DataResultErrorReporter(context);
	}

	private <T> DataResult<T> error(T object) {
		return DataResult.error(this::getReport, object);
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
