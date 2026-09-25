package nu.metacraft.deploy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A server's files in memory; {@link #writes} records every write in order. Set
 * {@link #failOnWrite} to n to make the n-th write from then on fail, as a connection that drops would.
 */
final class InMemoryRemoteFiles implements RemoteFiles {
    final SortedMap<String, byte[]> files = new TreeMap<>();
    final List<String> writes = new ArrayList<>();
    int failOnWrite;

    @Override
    public Optional<byte[]> read(String path) {
        return Optional.ofNullable(files.get(path)).map(byte[]::clone);
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        if (failOnWrite > 0 && --failOnWrite == 0) {
            throw new IOException("connection lost writing " + path);
        }
        files.put(path, data.clone());
        writes.add(path);
    }

    @Override
    public boolean delete(String path) {
        return files.remove(path) != null;
    }

    @Override
    public List<String> list(String dir) {
        List<String> names = new ArrayList<>();
        for (String path : files.keySet()) {
            String rest = path.startsWith(dir + "/") ? path.substring(dir.length() + 1) : null;
            if (rest != null && !rest.contains("/")) {
                names.add(rest);
            }
        }
        return names;
    }

    @Override
    public void close() {
    }

    void put(String path, String text) {
        files.put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    String text(String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    SortedSet<String> jarsIn(String dir) {
        SortedSet<String> jars = new TreeSet<>();
        for (String path : files.keySet()) {
            String rest = path.startsWith(dir + "/") ? path.substring(dir.length() + 1) : null;
            if (rest != null && !rest.contains("/") && rest.endsWith(".jar")) {
                jars.add(rest);
            }
        }
        return jars;
    }
}
