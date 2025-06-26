package se.datasektionen.mc.metacraft_lib.util.error_reporters;

import net.minecraft.util.ErrorReporter;
import org.apache.logging.log4j.Logger;

public class LoggingErrorReporter extends ErrorReporter.Impl implements AutoCloseable {

	private final Logger logger;

	public LoggingErrorReporter(Logger logger) {
		this.logger = logger;
	}

	public LoggingErrorReporter(ErrorReporter.Context context, Logger logger) {
		super(context);
		this.logger = logger;
	}

	public static LoggingErrorReporter create(ErrorReporter.Context context, Logger logger) {
		return new LoggingErrorReporter(context, logger);
	}

	@Override
	public void close() {
		if (!this.isEmpty()) {
			this.logger.warn("[{}] Serialization errors:\n{}", this.logger.getName(), this.getErrorsAsLongString());
		}
	}
}
