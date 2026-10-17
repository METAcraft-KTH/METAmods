package nu.metacraft.deploy;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Datapacks from Vanilla Tweaks, which has no fixed download URL: its site zips the packs you pick
 * into a file that expires. {@code vanillatweaks:<version>/<category>/<pack>}, named as their picker
 * names them, with {@code %20} for a space and {@code %2F} for the slash in {@code decorative/cosmetic}
 * (for example {@code vanillatweaks:26.3/gameplay%20changes/anti%20enderman%20grief}), asks for that
 * one pack and gives the pack's own zip, which is the same bytes on every download. Their terms
 * forbid sharing the packs unchanged, so they are fetched at build time and never kept in this
 * repository.
 */
public final class VanillaTweaks {
    public static final String SCHEME = "vanillatweaks";

    /** What their terms ask a project using the packs to carry, as credits.txt. */
    public static final String CREDITS = "Credits:\nVanilla Tweaks: https://vanillatweaks.net/\n";

    private static final URI SITE = URI.create("https://vanillatweaks.net/");
    private static final URI ZIP_DATAPACKS = SITE.resolve("assets/server/zipdatapacks.php");

    private VanillaTweaks() {
    }

    public static boolean is(URI url) {
        return SCHEME.equalsIgnoreCase(url.getScheme());
    }

    /** The pack's zip. */
    public static InputStream open(URI url) throws IOException {
        String[] names = parse(url);
        String version = names[0];
        String category = names[1];
        String pack = names[2];
        String form = "version=" + URLEncoder.encode(version, StandardCharsets.UTF_8)
                + "&packs=" + URLEncoder.encode(JsonOutput.toJson(Map.of(category, List.of(pack))), StandardCharsets.UTF_8);
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        try {
            HttpResponse<String> picked = http.send(HttpRequest.newBuilder(ZIP_DATAPACKS)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            Object json = picked.statusCode() == 200 ? parseJson(picked.body()) : null;
            if (!(json instanceof Map<?, ?> map) || !"success".equals(map.get("status")) || !(map.get("link") instanceof String link)) {
                throw new DeployException(url + ": Vanilla Tweaks has no pack '" + pack + "' in '" + category
                        + "' for " + version + " (HTTP " + picked.statusCode() + ": " + picked.body().strip() + ")");
            }
            HttpResponse<InputStream> bundle = http.send(HttpRequest.newBuilder(SITE.resolve(link)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (bundle.statusCode() != 200) {
                bundle.body().close();
                throw new DeployException(url + ": downloading " + link + " from Vanilla Tweaks gave HTTP " + bundle.statusCode());
            }
            return onlyZipIn(url, bundle.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted fetching " + url, e);
        }
    }

    /** {@code vanillatweaks:<version>/<category>/<pack>} as its three names, decoded. */
    static String[] parse(URI url) {
        String[] parts = url.getRawSchemeSpecificPart().split("/", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new DeployException(url + ": write a Vanilla Tweaks pack as vanillatweaks:<version>/<category>/<pack>");
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = URLDecoder.decode(parts[i], StandardCharsets.UTF_8);
        }
        return parts;
    }

    private static Object parseJson(String body) {
        try {
            return new JsonSlurper().parseText(body);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Their download wraps the picked packs, each a zip of its own, in one more zip. */
    private static InputStream onlyZipIn(URI url, InputStream bundle) throws IOException {
        byte[] found = null;
        try (ZipInputStream zip = new ZipInputStream(bundle)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.isDirectory() || !entry.getName().endsWith(".zip")) {
                    continue;
                }
                if (found != null) {
                    throw new DeployException(url + ": Vanilla Tweaks sent more than one pack");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                zip.transferTo(bytes);
                found = bytes.toByteArray();
            }
        }
        if (found == null) {
            throw new DeployException(url + ": Vanilla Tweaks' download has no pack in it");
        }
        return new ByteArrayInputStream(found);
    }
}
