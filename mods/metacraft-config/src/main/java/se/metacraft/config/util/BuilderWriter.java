package se.metacraft.config.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;

public class BuilderWriter extends Writer {

	private final StringBuilder builder = new StringBuilder();

	@Override
	public void write(char @NotNull [] chars, int i, int i1) throws IOException {
		builder.append(chars, i, i1);
	}

	@Override
	public void flush() throws IOException {

	}

	@Override
	public void close() throws IOException {

	}

	public String result() {
		return builder.toString();
	}
}
