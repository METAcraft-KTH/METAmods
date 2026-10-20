package metacraft.kultur.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The two ways anything is written into {@code src/main/generated} — a JSON document and a PNG —
 * through the run's {@link CachedOutput}, which leaves a file whose bytes have not changed alone.
 */
final class Writes {
	private final CachedOutput out;
	private final List<CompletableFuture<?>> pending = new ArrayList<>();

	Writes(CachedOutput out) {
		this.out = out;
	}

	/** A JSON document, written stably — keys sorted, so the same tree is always the same bytes. */
	void json(Path path, JsonElement element) {
		pending.add(DataProvider.saveStable(out, element, path));
	}

	void png(Path path, byte[] data) {
		pending.add(CompletableFuture.runAsync(() -> {
			try {
				out.writeIfNeeded(path, data, Hashing.sha1().hashBytes(data));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}));
	}

	CompletableFuture<?> allOf() {
		return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
	}
}
