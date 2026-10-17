package nu.metacraft.deploy;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Splits a pinned datapack zip for the deploy. Everything but {@code assets/} becomes the folder
 * {@code <world>/datapacks/<name>/} on the server ({@code datapack-<name>-<hash>.zip} in the set,
 * unpacked by {@link DatapackDeployer}), overlays and all: Minecraft applies them itself. A pack's
 * {@code assets/} (textures and models for its items) can't work from a datapack, so they become a
 * resource pack, {@code resourcepack-<name>-<hash>.zip}, which metacraft-resource-packs sends to
 * players; a set without that mod refuses such a pack.
 * <p>
 * Both zips' bytes depend only on the pinned zip's contents, so an unchanged pack is never sent again.
 */
public final class Datapack {
    /** The mod that serves resource packs to players ({@code mods/metacraft-resource-packs}). */
    public static final String RESOURCE_PACKS_MOD_ID = "metacraft-resource-packs";

    /** Every entry's time: the earliest a zip can hold, as Gradle's reproducible archives use. */
    private static final LocalDateTime TIME = LocalDateTime.of(1980, 2, 1, 0, 0);

    /**
     * The resource pack's formats: from 65 (1.21.9, the first that takes {@code min_format} without
     * {@code supported_formats}) up, so the pack.mcmeta is valid on any version this server runs.
     * Checked against 26.3's {@code PackMetadataSection}: a client applies a server's resource pack
     * whatever formats it claims, but drops every server pack if one pack.mcmeta doesn't parse.
     */
    static final int RESOURCE_MIN_FORMAT = 65;
    static final int RESOURCE_MAX_FORMAT = Integer.MAX_VALUE;

    private Datapack() {
    }

    /** The first 12 hex digits of the zip's sha256: the pack's version, which a zip doesn't have. */
    public static String shortHash(String zipSha256) {
        return zipSha256.substring(0, 12);
    }

    public static String folderZipName(String name, String zipSha256) {
        return "datapack-" + name + "-" + shortHash(zipSha256) + ".zip";
    }

    public static String resourcePackZipName(String name, String zipSha256) {
        return "resourcepack-" + name + "-" + shortHash(zipSha256) + ".zip";
    }

    /**
     * Writes the folder's zip (and the resource pack's, if the pack has {@code assets/}) into
     * {@code outDir} and returns the pack's manifest entry.
     *
     * @param resourcePacks whether the set has metacraft-resource-packs, which a pack with assets needs
     */
    public static DatapackManifest.Pack build(Path zip, String name, String zipSha256, URI source, String server,
                                              boolean resourcePacks, Path outDir) throws IOException {
        SortedMap<String, byte[]> all = new TreeMap<>();
        try (ZipFile in = new ZipFile(zip.toFile())) {
            for (ZipEntry entry : in.stream().toList()) {
                if (!entry.isDirectory()) {
                    String path;
                    try {
                        path = DatapackManifest.checkPath(entry.getName(), "file in a datapack");
                    } catch (DeployException e) {
                        throw new DeployException(source + ": " + e.getMessage());
                    }
                    try (InputStream data = in.getInputStream(entry)) {
                        all.put(path, data.readAllBytes());
                    }
                }
            }
        }
        byte[] mcmeta = all.get("pack.mcmeta");
        if (mcmeta == null) {
            throw new DeployException(source + " has no pack.mcmeta at its root; it is not a datapack zip");
        }
        Map<?, ?> meta = parseMcmeta(source, mcmeta);
        for (String dir : overlayDirs(source, meta)) {
            if (all.keySet().stream().anyMatch(p -> p.startsWith(dir + "/assets/"))) {
                throw new DeployException(source + ": its overlay " + dir + " has assets/, which the deploy can't put in"
                        + " a resource pack; use a pack with its assets only at the root");
            }
        }

        SortedMap<String, byte[]> folder = new TreeMap<>();
        SortedMap<String, byte[]> assets = new TreeMap<>();
        all.forEach((path, data) -> (path.startsWith("assets/") ? assets : folder).put(path, data));
        if (VanillaTweaks.is(source)) {
            folder.putIfAbsent("credits.txt", VanillaTweaks.CREDITS.getBytes(StandardCharsets.UTF_8));
        }

        DatapackManifest.ResourcePack resourcePack = null;
        if (!assets.isEmpty()) {
            if (!resourcePacks) {
                throw new DeployException(source + " has textures (assets/), which reach players through "
                        + RESOURCE_PACKS_MOD_ID + ": add " + RESOURCE_PACKS_MOD_ID + " to deploy/" + server
                        + ".txt, or use a pack without assets/");
            }
            assets.put("pack.mcmeta", resourcePackMcmeta(name, meta));
            if (all.containsKey("pack.png")) {
                assets.put("pack.png", all.get("pack.png"));
            }
            if (VanillaTweaks.is(source)) {
                assets.put("credits.txt", VanillaTweaks.CREDITS.getBytes(StandardCharsets.UTF_8));
            }
            String rpFile = resourcePackZipName(name, zipSha256);
            write(assets, outDir.resolve(rpFile));
            resourcePack = new DatapackManifest.ResourcePack(rpFile, Sha256.of(outDir.resolve(rpFile)));
        }

        String file = folderZipName(name, zipSha256);
        write(folder, outDir.resolve(file));
        SortedMap<String, String> files = new TreeMap<>();
        folder.forEach((path, data) -> files.put(path, Sha256.of(data)));
        return new DatapackManifest.Pack(zipSha256, shortHash(zipSha256), source.toString(), file, files, resourcePack, null);
    }

    /** The files of a set's folder zip, by path. */
    public static SortedMap<String, byte[]> read(Path folderZip) throws IOException {
        SortedMap<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(folderZip))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                if (!entry.isDirectory()) {
                    files.put(DatapackManifest.checkPath(entry.getName(), "file in a datapack"), in.readAllBytes());
                }
            }
        }
        return files;
    }

    private static Map<?, ?> parseMcmeta(URI source, byte[] mcmeta) {
        Object json;
        try {
            json = new JsonSlurper().parse(mcmeta, "UTF-8");
        } catch (RuntimeException e) {
            throw new DeployException(source + ": pack.mcmeta is not valid JSON: " + e.getMessage(), e);
        }
        if (!(json instanceof Map<?, ?> root) || !(root.get("pack") instanceof Map<?, ?>)) {
            throw new DeployException(source + ": pack.mcmeta has no \"pack\"");
        }
        return root;
    }

    private static List<String> overlayDirs(URI source, Map<?, ?> meta) {
        List<String> dirs = new ArrayList<>();
        if (meta.get("overlays") instanceof Map<?, ?> overlays && overlays.get("entries") instanceof List<?> entries) {
            for (Object o : entries) {
                if (!(o instanceof Map<?, ?> entry) || !(entry.get("directory") instanceof String dir)) {
                    throw new DeployException(source + ": an overlay in pack.mcmeta has no \"directory\"");
                }
                dirs.add(dir);
            }
        }
        return dirs;
    }

    /** The datapack's description, with the resource pack formats ({@link #RESOURCE_MIN_FORMAT}). */
    private static byte[] resourcePackMcmeta(String name, Map<?, ?> meta) {
        Object description = ((Map<?, ?>) meta.get("pack")).get("description");
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("description", description != null ? description : name);
        pack.put("min_format", RESOURCE_MIN_FORMAT);
        pack.put("max_format", RESOURCE_MAX_FORMAT);
        return JsonOutput.prettyPrint(JsonOutput.toJson(Map.of("pack", pack))).getBytes(StandardCharsets.UTF_8);
    }

    /** Sorted entries, all at {@link #TIME}. */
    static void write(SortedMap<String, byte[]> files, Path zip) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTimeLocal(TIME);
                out.putNextEntry(entry);
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        try (OutputStream file = Files.newOutputStream(zip)) {
            bytes.writeTo(file);
        }
    }
}
