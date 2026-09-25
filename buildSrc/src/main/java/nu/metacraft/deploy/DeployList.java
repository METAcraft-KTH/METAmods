package nu.metacraft.deploy;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** A parsed {@code deploy/<server>.txt}: project names under {@code mods/} and external jars. */
public record DeployList(String server, List<String> projects, List<External> externals) {

    /** The mod id of the old all-in-one jar. It is never on a list; the first deploy removes it. */
    public static final String BUNDLE_ID = "metacraft";

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> SCHEMES = Set.of("https", "http", "file");

    /** An {@code external <mod-id> <url> sha256:<hex>} line; {@code sha256} is lower-case hex. */
    public record External(String modId, URI url, String sha256) {}

    public static DeployList read(String server, Path file) {
        if (!Files.isRegularFile(file)) {
            throw new DeployException("no list for server " + server + ": " + file + " does not exist");
        }
        try {
            return parse(server, Files.readAllLines(file));
        } catch (IOException e) {
            throw new DeployException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    public static DeployList parse(String server, List<String> lines) {
        List<String> projects = new ArrayList<>();
        List<External> externals = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String where = "deploy/" + server + ".txt line " + (i + 1);
            String line = stripComment(lines.get(i)).strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] words = line.split("\\s+");
            String id;
            if (words[0].equals("external")) {
                if (words.length != 4) {
                    throw new DeployException(where + ": expected 'external <mod-id> <url> sha256:<hex>', got '" + line + "'");
                }
                id = checkId(where, words[1]);
                URI url = parseUrl(where, words[2]);
                if (!words[3].startsWith("sha256:")) {
                    throw new DeployException(where + ": write the hash as sha256:<hex>, got '" + words[3] + "'");
                }
                String hex = words[3].substring("sha256:".length()).toLowerCase(Locale.ROOT);
                if (!SHA256.matcher(hex).matches()) {
                    throw new DeployException(where + ": a sha256 is 64 hex digits, got '" + hex + "'");
                }
                externals.add(new External(id, url, hex));
            } else {
                if (words.length != 1) {
                    throw new DeployException(where + ": expected one project name per line, got '" + line + "'");
                }
                id = checkId(where, words[0]);
                projects.add(id);
            }
            if (!seen.add(id)) {
                throw new DeployException(where + ": " + id + " is listed twice");
            }
        }
        return new DeployList(server, List.copyOf(projects), List.copyOf(externals));
    }

    /** Drops a comment: a {@code #} at the start of the line or after whitespace. A URL's {@code #fragment} stays. */
    static String stripComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String checkId(String where, String id) {
        if (id.equals(BUNDLE_ID)) {
            throw new DeployException(where + ": a list may not contain metacraft; that is the old all-in-one bundle, deleted on the first deploy");
        }
        if (!NAME.matcher(id).matches()) {
            throw new DeployException(where + ": '" + id + "' is not a project name or mod id");
        }
        return id;
    }

    private static URI parseUrl(String where, String text) {
        URI url;
        try {
            url = new URI(text);
        } catch (URISyntaxException e) {
            throw new DeployException(where + ": '" + text + "' is not a URL");
        }
        if (url.getScheme() == null || !SCHEMES.contains(url.getScheme().toLowerCase(Locale.ROOT))) {
            throw new DeployException(where + ": '" + text + "' is not an https URL");
        }
        return url;
    }
}
