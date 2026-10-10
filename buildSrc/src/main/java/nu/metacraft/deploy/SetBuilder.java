package nu.metacraft.deploy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Fills {@code build/deploy/<server>/} with a set's jars and its manifest.json. */
public final class SetBuilder {
    @FunctionalInterface
    public interface Fetcher {
        InputStream open(URI url) throws IOException;
    }

    /** https (following redirects within https, as GitHub release downloads need) and file URLs. */
    public static final Fetcher URL_FETCHER = url -> url.toURL().openStream();

    private SetBuilder() {
    }

    /**
     * @param projectJars project name to its built jar, for every project in the set
     * @param repoModIds  mod ids of every project under mods/, for the depends check
     */
    public static Manifest build(DeployList list, SortedMap<String, Path> projectJars, Set<String> repoModIds,
                                 Path outDir, Fetcher fetcher) {
        String server = list.server();
        try {
            resetDir(outDir);
            SortedMap<String, Manifest.Entry> entries = new TreeMap<>();
            Map<String, ModInfo> infos = new TreeMap<>();
            for (Map.Entry<String, Path> e : projectJars.entrySet()) {
                Path jar = e.getValue();
                String file = jar.getFileName().toString();
                add(server, entries, infos, ModInfo.read(jar), file, Sha256.of(jar), e.getKey());
                Files.copy(jar, outDir.resolve(file));
            }
            for (DeployList.External external : list.externals()) {
                String file = fileNameOf(external.url());
                Path target = outDir.resolve(file);
                if (Files.exists(target)) {
                    throw new DeployException(external.url() + ": another jar in " + server + "'s set is already called " + file);
                }
                try (InputStream in = fetcher.open(external.url())) {
                    Files.copy(in, target);
                }
                String sha256 = Sha256.of(target);
                if (!sha256.equals(external.sha256())) {
                    Files.delete(target);
                    throw new DeployException("sha256 mismatch for " + external.url() + ": deploy/" + server
                            + ".txt pins " + external.sha256() + ", the download is " + sha256);
                }
                ModInfo info = ModInfo.read(target);
                if (!info.id().equals(external.modId())) {
                    throw new DeployException(external.url() + " is mod " + info.id() + ", but deploy/" + server
                            + ".txt calls it " + external.modId());
                }
                add(server, entries, infos, info, file, sha256, external.url().toString());
            }
            DependsCheck.check(server, infos, repoModIds);
            Manifest manifest = new Manifest(entries);
            manifest.write(outDir.resolve("manifest.json"));
            return manifest;
        } catch (IOException e) {
            throw new DeployException("building " + server + "'s set failed: " + e.getMessage(), e);
        }
    }

    private static void add(String server, SortedMap<String, Manifest.Entry> entries, Map<String, ModInfo> infos,
                            ModInfo info, String file, String sha256, String source) {
        if (info.id().equals(DeployList.BUNDLE_ID)) {
            throw new DeployException(source + ": a list may not contain metacraft (the old all-in-one bundle)");
        }
        if (entries.containsKey(info.id())) {
            throw new DeployException("mod id " + info.id() + " is twice in " + server + "'s set ("
                    + entries.get(info.id()).source() + " and " + source + ")");
        }
        entries.put(info.id(), new Manifest.Entry(file, sha256, info.version(), source));
        infos.put(info.id(), info);
    }

    static String fileNameOf(URI url) {
        String path = url.getPath() == null ? "" : url.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (!name.endsWith(".jar")) {
            throw new DeployException(url + " does not end in a .jar file name");
        }
        return name;
    }

    private static void resetDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    if (!p.equals(dir)) {
                        Files.delete(p);
                    }
                }
            }
        }
        Files.createDirectories(dir);
    }
}
