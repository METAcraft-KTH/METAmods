package nu.metacraft.deploy;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Vector;

/** {@link RemoteFiles} over SFTP with password login (JSch, the maintained mwiede fork). */
public final class SftpRemoteFiles implements RemoteFiles {
    private static final int TIMEOUT_MS = 30_000;
    private final Session session;
    private final ChannelSftp sftp;

    private SftpRemoteFiles(Session session, ChannelSftp sftp) {
        this.session = session;
        this.sftp = sftp;
    }

    public static SftpRemoteFiles connect(String host, int port, String user, String password) throws IOException {
        try {
            Session session = new JSch().getSession(user, host, port);
            session.setPassword(password);
            // Host keys are not pinned, as with the SFTP action this replaces (a listed follow-up).
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password");
            session.connect(TIMEOUT_MS);
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(TIMEOUT_MS);
            return new SftpRemoteFiles(session, sftp);
        } catch (JSchException e) {
            throw new IOException("cannot open SFTP to " + user + "@" + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<byte[]> read(String path) throws IOException {
        try (InputStream in = sftp.get(path)) {
            return Optional.of(in.readAllBytes());
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return Optional.empty();
            }
            throw failure("read", path, e);
        }
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        String part = path + ".part";
        try {
            sftp.put(new ByteArrayInputStream(data), part, ChannelSftp.OVERWRITE);
            delete(path);
            sftp.rename(part, path);
        } catch (SftpException e) {
            throw failure("write", path, e);
        }
    }

    @Override
    public boolean delete(String path) throws IOException {
        try {
            sftp.rm(path);
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw failure("delete", path, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> list(String dir) throws IOException {
        try {
            Vector<ChannelSftp.LsEntry> entries = sftp.ls(dir);
            List<String> names = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (!".".equals(name) && !"..".equals(name)) {
                    names.add(name);
                }
            }
            return names;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return List.of();
            }
            throw failure("list", dir, e);
        }
    }

    @Override
    public void close() {
        sftp.disconnect();
        session.disconnect();
    }

    private static IOException failure(String operation, String path, SftpException e) {
        return new IOException("SFTP " + operation + " " + path + " failed: " + e.getMessage(), e);
    }
}
