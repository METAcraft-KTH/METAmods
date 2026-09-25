package nu.metacraft.deploy;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The file operations a deploy needs on a server; paths are relative to the SFTP home. */
public interface RemoteFiles extends Closeable {
    /** The file's bytes, or empty if it does not exist. */
    Optional<byte[]> read(String path) throws IOException;

    /** Writes the file so it appears whole (to {@code path + ".part"}, then renamed). */
    void write(String path, byte[] data) throws IOException;

    default void upload(String path, Path local) throws IOException {
        write(path, Files.readAllBytes(local));
    }

    /** Deletes the file; false if it did not exist. */
    boolean delete(String path) throws IOException;

    /** The plain file names directly under {@code dir}, or empty if {@code dir} does not exist. */
    List<String> list(String dir) throws IOException;
}
