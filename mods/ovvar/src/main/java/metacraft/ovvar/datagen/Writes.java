package metacraft.ovvar.datagen;

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
 * The two ways anything of ours is ever written into {@code src/main/generated} — a JSON document
 * and a PNG — and the futures the run has to wait on before it may say it is finished. One of
 * these belongs to one {@link DataProvider#run} call: it holds that call's {@link CachedOutput},
 * which is the cache deciding whether a file whose bytes have not changed is touched at all, so a
 * provider makes a fresh one each run rather than clearing a field it shares between runs.
 *
 * <p>There is nothing clever here, and that is the point: both providers in this package wrote
 * these six lines out separately, which is six lines for a hash algorithm and a wrapping of
 * {@link IOException} to drift apart in — and a PNG written two ways is a PNG whose cache entry
 * two runs disagree about.
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

	/** A texture. Encoded now (on the calling thread, so a {@link Tex} may be reused after) and hashed for the cache. */
	void png(Path path, Tex tex) {
		byte[] data = tex.png();
		pending.add(CompletableFuture.runAsync(() -> {
			try {
				out.writeIfNeeded(path, data, Hashing.sha1().hashBytes(data));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}));
	}

	/** Everything asked for above, as the one future {@link DataProvider#run} returns. */
	CompletableFuture<?> allOf() {
		return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
	}
}
