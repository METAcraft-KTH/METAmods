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
 * Directories are those holding a file, and those {@link #mkdirs} made. Every change is in
 * {@link #ops} ({@code "put <path>"}, {@code "rename <from> <to>"}, ...); set {@link #failOnOp} to make
 * the first change starting with it fail.
 */
final class InMemoryRemoteFiles implements RemoteFiles {
    final SortedMap<String, byte[]> files = new TreeMap<>();
    final SortedSet<String> dirs = new TreeSet<>();
    final List<String> writes = new ArrayList<>();
    final List<String> ops = new ArrayList<>();
    int failOnWrite;
    String failOnOp;

    private void op(String op) throws IOException {
        if (failOnOp != null && op.startsWith(failOnOp)) {
            failOnOp = null;
            throw new IOException("connection lost: " + op);
        }
        ops.add(op);
    }

    @Override
    public Optional<byte[]> read(String path) {
        return Optional.ofNullable(files.get(path)).map(byte[]::clone);
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        if (failOnWrite > 0 && --failOnWrite == 0) {
            throw new IOException("connection lost writing " + path);
        }
        op("write " + path);
        files.put(path, data.clone());
        writes.add(path);
    }

    @Override
    public void put(String path, byte[] data) throws IOException {
        if (!isDir(parent(path))) {
            throw new IOException("no directory for " + path);
        }
        op("put " + path);
        files.put(path, data.clone());
    }

    @Override
    public boolean delete(String path) throws IOException {
        if (!files.containsKey(path)) {
            return false;
        }
        op("delete " + path);
        files.remove(path);
        return true;
    }

    @Override
    public List<String> list(String dir) {
        return new ArrayList<>(entries(dir).keySet());
    }

    @Override
    public SortedMap<String, Boolean> entries(String dir) {
        SortedMap<String, Boolean> names = new TreeMap<>();
        for (String path : files.keySet()) {
            String rest = path.startsWith(dir + "/") ? path.substring(dir.length() + 1) : null;
            if (rest != null) {
                int slash = rest.indexOf('/');
                names.put(slash < 0 ? rest : rest.substring(0, slash), slash >= 0);
            }
        }
        for (String d : dirs) {
            String rest = d.startsWith(dir + "/") ? d.substring(dir.length() + 1) : null;
            if (rest != null) {
                int slash = rest.indexOf('/');
                names.put(slash < 0 ? rest : rest.substring(0, slash), true);
            }
        }
        return names;
    }

    @Override
    public void mkdirs(String dir) throws IOException {
        if (!isDir(dir)) {
            op("mkdirs " + dir);
        }
        for (String d = dir; !d.isEmpty(); d = parent(d)) {
            dirs.add(d);
        }
    }

    @Override
    public void rename(String from, String to) throws IOException {
        if (files.containsKey(to) || isDir(to)) {
            throw new IOException(to + " exists");
        }
        if (!files.containsKey(from) && !isDir(from)) {
            throw new IOException(from + " does not exist");
        }
        op("rename " + from + " " + to);
        if (files.containsKey(from)) {
            files.put(to, files.remove(from));
            return;
        }
        for (String path : new ArrayList<>(files.keySet())) {
            if (path.startsWith(from + "/")) {
                files.put(to + path.substring(from.length()), files.remove(path));
            }
        }
        for (String d : new ArrayList<>(dirs)) {
            if (d.equals(from) || d.startsWith(from + "/")) {
                dirs.remove(d);
                dirs.add(to + d.substring(from.length()));
            }
        }
        for (String d = parent(to); !d.isEmpty(); d = parent(d)) {
            dirs.add(d);
        }
    }

    @Override
    public void rmdir(String dir) throws IOException {
        if (!entries(dir).isEmpty()) {
            throw new IOException(dir + " is not empty");
        }
        op("rmdir " + dir);
        dirs.remove(dir);
    }

    @Override
    public void close() {
    }

    boolean isDir(String dir) {
        return dirs.contains(dir) || files.keySet().stream().anyMatch(p -> p.startsWith(dir + "/"));
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    void put(String path, String text) {
        files.put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    String text(String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    /** The files under {@code dir}, by path relative to it. */
    SortedMap<String, String> textsIn(String dir) {
        SortedMap<String, String> texts = new TreeMap<>();
        files.forEach((path, data) -> {
            if (path.startsWith(dir + "/")) {
                texts.put(path.substring(dir.length() + 1), new String(data, StandardCharsets.UTF_8));
            }
        });
        return texts;
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
