package nu.metacraft.deploy;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;

/** The file operations a deploy needs on a server; paths are relative to the SFTP home. */
public interface RemoteFiles extends Closeable {
    /** The file's bytes, or empty if it does not exist. */
    Optional<byte[]> read(String path) throws IOException;

    /** Writes the file so it appears whole (to {@code path + ".part"}, then renamed). */
    void write(String path, byte[] data) throws IOException;

    /** Writes the file in place, for a folder that is renamed into place once it is complete. */
    default void put(String path, byte[] data) throws IOException {
        write(path, data);
    }

    default void upload(String path, Path local) throws IOException {
        write(path, Files.readAllBytes(local));
    }

    /** Deletes the file; false if it did not exist. */
    boolean delete(String path) throws IOException;

    /** The plain file names directly under {@code dir}, or empty if {@code dir} does not exist. */
    List<String> list(String dir) throws IOException;

    /** Everything directly under {@code dir}, name to whether it is a directory; empty if {@code dir} does not exist. */
    SortedMap<String, Boolean> entries(String dir) throws IOException;

    /** Creates {@code dir} and its missing parents. */
    void mkdirs(String dir) throws IOException;

    /** Renames a file or directory; fails if {@code to} exists. */
    void rename(String from, String to) throws IOException;

    /** Deletes an empty directory. */
    void rmdir(String dir) throws IOException;

    /** Deletes the directory {@code dir} and everything in it. */
    default void deleteTree(String dir) throws IOException {
        for (var e : entries(dir).entrySet()) {
            String path = dir + "/" + e.getKey();
            if (e.getValue()) {
                deleteTree(path);
            } else {
                delete(path);
            }
        }
        rmdir(dir);
    }
}
