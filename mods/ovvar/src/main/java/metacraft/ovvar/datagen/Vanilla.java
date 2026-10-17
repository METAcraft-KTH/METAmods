package metacraft.ovvar.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Vanilla assets and data as sources: at datagen time the game jar is on the classpath, so its
 * textures, item definitions and recipes are plain class resources. Listing a directory (banner
 * masks, the dye recipes) opens the jar the resources come from.
 */
final class Vanilla {
	private Vanilla() {}

	static InputStream open(String path) {
		InputStream in = Vanilla.class.getResourceAsStream("/" + path);
		if (in == null) throw new IllegalStateException("vanilla resource missing from classpath: " + path);
		return in;
	}

	static boolean exists(String path) {
		return Vanilla.class.getResource("/" + path) != null;
	}

	static byte[] bytes(String path) {
		try (InputStream in = open(path)) {
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static Tex texture(String path) {
		return Tex.read(open("assets/minecraft/textures/" + path + ".png"));
	}

	static JsonElement json(String path) {
		try (var reader = new InputStreamReader(open(path), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * The file names in a resource directory on the classpath: the mod's own {@code art/ovvar/...}
	 * in a build directory in dev, or an entry in a jar. Only datagen looks at a directory as a
	 * directory — everything else knows the name of the file it wants.
	 */
	static List<String> dir(String path) {
		URL url = Vanilla.class.getResource("/" + path);
		if (url == null) throw new IllegalStateException("no such resource directory on the classpath: " + path);
		List<String> out = new ArrayList<>();
		try {
			if ("file".equals(url.getProtocol())) {
				try (var files = java.nio.file.Files.list(java.nio.file.Path.of(url.toURI()))) {
					files.forEach(f -> out.add(f.getFileName().toString()));
				}
			} else {
				out.addAll(list(path.endsWith("/") ? path : path + "/", path));
			}
		} catch (IOException | java.net.URISyntaxException e) {
			throw new IllegalStateException("cannot list " + path, e);
		}
		out.sort(null);
		return out;
	}

	/** Names (relative to {@code prefix}) of every entry under it in the jar that holds {@code probe}. */
	static List<String> list(String prefix, String probe) {
		URL url = Vanilla.class.getResource("/" + probe);
		if (url == null || !"jar".equals(url.getProtocol())) {
			throw new IllegalStateException("vanilla resource " + probe + " is not in a jar on the classpath: " + url);
		}
		String spec = url.getPath(); // file:/path/to.jar!/assets/...
		String jarPath = spec.substring(spec.indexOf(':') + 1, spec.indexOf('!'));
		List<String> out = new ArrayList<>();
		try (ZipFile zip = new ZipFile(java.net.URLDecoder.decode(jarPath, StandardCharsets.UTF_8))) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry e = entries.nextElement();
				if (!e.isDirectory() && e.getName().startsWith(prefix)) {
					out.add(e.getName().substring(prefix.length()));
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		out.sort(null);
		return out;
	}
}
