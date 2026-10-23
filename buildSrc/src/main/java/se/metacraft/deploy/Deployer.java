package se.metacraft.deploy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deploys a set to a server: checks autodeploy can delete, uploads what differs from the
 * server's manifest, lists removals in remove.txt, and writes the manifest last so an
 * interrupted deploy is retried in full. Before its first change it rewrites the server's
 * manifest with an unknown sha256 for the mods it is about to upload or remove, so that after an
 * interrupted deploy (and maybe a restart that installed part of it) the next deploy sends or
 * removes those again.
 *
 * <p>It only manages its own mods: the mod ids in the server's manifest and the set (and the old
 * {@code metacraft} bundle, which the set replaces). Jars of any other mod, in {@code mods/} or
 * {@code mods/update}, are never uploaded over, listed for removal or deleted.
 */
public final class Deployer {
	/** The first FabricModsUpdate version that handles remove.txt. */
	public static final String MIN_AUTODEPLOY_VERSION = "1.1";
	public static final String UPDATE_DIR = "mods/update";
	public static final String MARKER = UPDATE_DIR + "/.autodeploy-version";
	public static final String REMOVE_FILE = UPDATE_DIR + "/remove.txt";
	public static final String REMOTE_MANIFEST = "mods/metacraft-deploy.json";
	/** The sha256 the interim manifest gives mods a deploy is changing: valid, but no jar's. */
	static final String UNKNOWN_SHA256 = "0".repeat(64);

	public record Report(String server, boolean firstDeploy, boolean dryRun, SortedSet<String> uploaded,
						 SortedSet<String> removed, SortedSet<String> unchanged, Manifest manifest) {}

	private Deployer() {
	}

	public static Report deploy(String server, Path deployDir, RemoteFiles remote, boolean dryRun) throws IOException {
		checkAutodeploy(server, remote);
		Manifest now = Manifest.read(deployDir.resolve("manifest.json"));
		checkLocalJars(deployDir, now);

		Optional<byte[]> oldBytes = remote.read(REMOTE_MANIFEST);
		// First means no manifest on the server at all. (After an interrupted first deploy the
		// interim manifest below is there, so the retry no longer counts as first.)
		boolean first = oldBytes.isEmpty();
		Manifest old = first ? Manifest.empty() : parseServerManifest(server, oldBytes.get());
		ManifestDiff diff = ManifestDiff.of(old, now);

		// mods/update is the source of truth for what's pending, not the (possibly never-written)
		// old manifest: an interrupted deploy can leave jars there that no manifest ever named.
		Map<String, String> fileToId = new HashMap<>();
		now.entries().forEach((id, entry) -> fileToId.put(entry.file(), id));
		SortedSet<String> jarsInUpdateDir = new TreeSet<>();
		for (String name : remote.list(UPDATE_DIR)) {
			if (name.endsWith(".jar")) {
				jarsInUpdateDir.add(name);
			}
		}

		SortedSet<String> upload = new TreeSet<>(diff.upload());
		SortedSet<String> otherJars = new TreeSet<>();
		for (String jar : jarsInUpdateDir) {
			String id = fileToId.get(jar);
			if (id != null) {
				// Already sitting there with the right bytes (or about to be re-checked by
				// checkLocalJars against manifest.json), but the server's manifest may never have
				// recorded it, so upload again to be sure.
				upload.add(id);
			} else {
				otherJars.add(jar);
			}
		}
		SortedSet<String> unchanged = new TreeSet<>(diff.unchanged());
		unchanged.removeAll(upload);

		// Removals still pending from deploys the server has not restarted for stay listed. The
		// bundle is listed on the first deploy, the switch from it to the set.
		Optional<byte[]> pendingFile = remote.read(REMOVE_FILE);
		SortedSet<String> pendingRemovals = pendingFile.map(Deployer::ids).orElseGet(TreeSet::new);
		SortedSet<String> remove = new TreeSet<>(diff.remove());
		remove.addAll(pendingRemovals);
		if (first) {
			remove.add(DeployList.BUNDLE_ID);
		}
		remove.removeAll(now.entries().keySet());

		// A jar in mods/update that isn't in this set is an earlier deploy's upload that no restart
		// installed yet, or one someone put there by hand. Ours (a mod id this deploy manages) is
		// deleted, so the restart doesn't install an old or removed version; any other mod's is left
		// for autodeploy to install, as whoever put it there meant.
		Set<String> managed = new HashSet<>(old.entries().keySet());
		managed.addAll(now.entries().keySet());
		managed.addAll(remove);
		SortedSet<String> staleJars = new TreeSet<>();
		for (String jar : otherJars) {
			Optional<byte[]> bytes = remote.read(UPDATE_DIR + "/" + jar);
			if (bytes.isPresent() && managed.contains(modIdOf(bytes.get(), jar))) {
				staleJars.add(jar);
			}
		}

		// The server's manifest records what was sent, and a restart installs whatever an
		// interrupted deploy got as far as sending (jar names don't change between builds). So
		// until this deploy is complete the server's manifest must not vouch for the bytes of any
		// mod it touches, yet must keep naming it: an entry whose sha256 matches no jar is uploaded
		// again by the next deploy if still listed, and removed if not. Ids new in this deploy get
		// such an entry too, so the next deploy still counts them as its own: it deletes their
		// upload from mods/update, or their jar if a restart installed it, if they are no longer
		// listed. (A new id whose jar name an old entry already has goes without; the manifest
		// can't name a file twice.)
		SortedMap<String, Manifest.Entry> interim = new TreeMap<>(old.entries());
		SortedSet<String> touched = new TreeSet<>(upload);
		touched.addAll(remove);
		touched.addAll(pendingRemovals);
		for (String id : touched) {
			interim.computeIfPresent(id, (i, e) -> new Manifest.Entry(e.file(), UNKNOWN_SHA256, e.version(), e.source()));
		}
		Set<String> interimFiles = new HashSet<>();
		interim.values().forEach(e -> interimFiles.add(e.file()));
		for (String id : upload) {
			Manifest.Entry e = now.entries().get(id);
			if (!interim.containsKey(id) && interimFiles.add(e.file())) {
				interim.put(id, new Manifest.Entry(e.file(), UNKNOWN_SHA256, e.version(), e.source()));
			}
		}

		if (!dryRun) {
			if (!interim.equals(old.entries())) {
				remote.write(REMOTE_MANIFEST, new Manifest(interim).toJson().getBytes(StandardCharsets.UTF_8));
			}
			for (String jar : staleJars) {
				remote.delete(UPDATE_DIR + "/" + jar);
			}
			for (String id : upload) {
				String file = now.entries().get(id).file();
				remote.upload(UPDATE_DIR + "/" + file, deployDir.resolve(file));
			}
			if (!remove.isEmpty()) {
				remote.write(REMOVE_FILE, (String.join("\n", remove) + "\n").getBytes(StandardCharsets.UTF_8));
			} else if (pendingFile.isPresent()) {
				remote.delete(REMOVE_FILE);   // its removals were all re-added
			}
			remote.write(REMOTE_MANIFEST, now.toJson().getBytes(StandardCharsets.UTF_8));
		}
		return new Report(server, first, dryRun, Collections.unmodifiableSortedSet(upload),
				Collections.unmodifiableSortedSet(remove), Collections.unmodifiableSortedSet(unchanged), now);
	}

	private static void checkAutodeploy(String server, RemoteFiles remote) throws IOException {
		Optional<String> version = remote.read(MARKER).map(b -> new String(b, StandardCharsets.UTF_8).strip());
		if (version.isEmpty() || compareVersions(version.get(), MIN_AUTODEPLOY_VERSION) < 0) {
			throw new DeployException("update autodeploy.jar on " + server + " first (FabricModsUpdate v"
					+ MIN_AUTODEPLOY_VERSION + "); nothing was uploaded ("
					+ version.map(v -> "it runs " + v).orElse("no " + MARKER) + ")");
		}
	}

	private static void checkLocalJars(Path deployDir, Manifest now) throws IOException {
		for (Map.Entry<String, Manifest.Entry> e : now.entries().entrySet()) {
			Path jar = deployDir.resolve(e.getValue().file());
			if (!Files.isRegularFile(jar) || !Sha256.of(jar).equals(e.getValue().sha256())) {
				throw new DeployException(jar + " (" + e.getKey() + ") does not match manifest.json; nothing was uploaded");
			}
		}
	}

	private static Manifest parseServerManifest(String server, byte[] bytes) {
		try {
			return Manifest.parse(new String(bytes, StandardCharsets.UTF_8));
		} catch (DeployException e) {
			throw new DeployException(REMOTE_MANIFEST + " on " + server + " is unreadable (" + e.getMessage()
					+ "); delete it to redeploy everything; nothing was uploaded", e);
		}
	}

	/** The mod id of a jar on the server, or null if it isn't a Fabric mod we can read. */
	private static String modIdOf(byte[] jar, String name) {
		try {
			return ModInfo.read(jar, name).id();
		} catch (DeployException notAMod) {
			return null;
		}
	}

	private static SortedSet<String> ids(byte[] removeTxt) {
		SortedSet<String> ids = new TreeSet<>();
		for (String line : new String(removeTxt, StandardCharsets.UTF_8).split("\n")) {
			String id = line.strip();
			if (!id.isEmpty() && !id.startsWith("#")) {
				ids.add(id);
			}
		}
		return ids;
	}

	static int compareVersions(String a, String b) {
		String[] x = a.split("\\.");
		String[] y = b.split("\\.");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? leadingInt(x[i]) : 0;
			int q = i < y.length ? leadingInt(y[i]) : 0;
			if (p != q) {
				return Integer.compare(p, q);
			}
		}
		return 0;
	}

	private static int leadingInt(String part) {
		int n = 0;
		for (int i = 0; i < part.length() && i < 9 && Character.isDigit(part.charAt(i)); i++) {
			n = n * 10 + (part.charAt(i) - '0');
		}
		return n;
	}
}
