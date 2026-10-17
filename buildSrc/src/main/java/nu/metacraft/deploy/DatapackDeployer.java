package nu.metacraft.deploy;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Deploys a set's datapacks: each is the folder {@code <world>/datapacks/<name>/}, where staff can
 * open and edit it and {@code /reload} picks it up, and its {@code assets/} are a resource pack for
 * metacraft-resource-packs.
 * <p>
 * A folder is filled under {@code .<name>.deploying} with its pack.mcmeta last (Minecraft only takes a
 * directory with a pack.mcmeta for a pack), then renamed into place; the folder it replaces is first
 * renamed to {@code .<name>.replaced} and loses its pack.mcmeta, then is deleted. So a half-uploaded
 * pack is never a pack. As for jars, the server's manifest is rewritten before the first change so
 * that it names every pack the deploy touches (and the files a folder may hold if the deploy is
 * interrupted), and written in full last; the next deploy deletes the leftover dot-folders.
 * <p>
 * The deploy only touches folders its manifest names, and only if they still hold exactly what it put
 * there. A folder edited on the server is neither updated nor removed: the report names it and the
 * files that differ, until staff delete the folder (the next deploy installs it again) or undo the edit.
 * <p>
 * A resource pack is uploaded as {@code update/<name>.zip} under metacraft-resource-packs'
 * resource-packs folder, which the mod moves into place at its next start or
 * {@code /metacraft-resource-pack reload}; a removed one is deleted from both folders.
 */
public final class DatapackDeployer {
    public static final String REMOTE_MANIFEST = "mods/metacraft-deploy-datapacks.json";
    public static final String SERVER_PROPERTIES = "server.properties";
    public static final String DEFAULT_WORLD = "world";
    /** metacraft-resource-packs' folders (its ResourcePackConfig). */
    public static final String RESOURCE_PACK_DIR = "config/metacraft-resource-packs/resource-packs";
    public static final String RESOURCE_PACK_UPDATE_DIR = RESOURCE_PACK_DIR + "/update";
    static final String STAGING = ".deploying";
    static final String REPLACED = ".replaced";
    /** Packs whose changes only a restart applies: /reload doesn't rebuild the world's dimensions. */
    private static final Pattern WORLDGEN = Pattern.compile("(^|/)data/[^/]+/(worldgen|dimension|dimension_type)/");

    private enum Folder { NONE, INSTALL, UPDATE, REMOVE }

    /** A pack the deploy left alone: why, and the files that differ from what it deployed. */
    public record Skipped(String name, String reason, List<String> files) {}

    public record Report(String world, String note, SortedSet<String> installed, SortedSet<String> updated,
                         SortedSet<String> removed, SortedSet<String> unchanged, List<Skipped> skipped,
                         SortedSet<String> resourcePacksSent, SortedSet<String> resourcePacksRemoved,
                         SortedSet<String> newResourcePacks, SortedSet<String> worldgen, DatapackManifest manifest) {
        static Report none() {
            return new Report(null, null, new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), List.of(),
                    new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), DatapackManifest.empty());
        }

        /** Whether the set or the server has any datapacks, so the summary has something to say. */
        public boolean any() {
            return world != null;
        }

        public boolean datapacksChanged() {
            return !installed.isEmpty() || !updated.isEmpty() || !removed.isEmpty();
        }

        public boolean resourcePacksChanged() {
            return !resourcePacksSent.isEmpty() || !resourcePacksRemoved.isEmpty();
        }

        public String datapacksDir() {
            return world + "/datapacks";
        }
    }

    /** What a deploy will do, worked out before anything is written. */
    public static final class Plan {
        private final DatapackManifest old;
        private final boolean oldExists;
        private final DatapackManifest interim;
        private final Map<String, Folder> folders;
        private final Set<String> upload;
        private final Set<String> deleteResourcePack;
        private final Set<String> leftovers;
        private final Map<String, SortedMap<String, byte[]>> contents;
        private final Map<String, byte[]> resourcePacks;
        public final Report report;

        private Plan(DatapackManifest old, boolean oldExists, DatapackManifest interim,
                     Map<String, Folder> folders, Set<String> upload, Set<String> deleteResourcePack, Set<String> leftovers,
                     Map<String, SortedMap<String, byte[]>> contents, Map<String, byte[]> resourcePacks, Report report) {
            this.old = old;
            this.oldExists = oldExists;
            this.interim = interim;
            this.folders = folders;
            this.upload = upload;
            this.deleteResourcePack = deleteResourcePack;
            this.leftovers = leftovers;
            this.contents = contents;
            this.resourcePacks = resourcePacks;
            this.report = report;
        }

        public void execute(RemoteFiles remote) throws IOException {
            if (report.world() == null) {
                return;
            }
            String dir = report.datapacksDir();
            if (!oldExists || !interim.equals(old)) {
                remote.write(REMOTE_MANIFEST, interim.toJson().getBytes(StandardCharsets.UTF_8));
            }
            for (String leftover : leftovers) {
                remote.deleteTree(dir + "/" + leftover);
            }
            for (Map.Entry<String, Folder> e : folders.entrySet()) {
                String name = e.getKey();
                switch (e.getValue()) {
                    case INSTALL, UPDATE -> replace(remote, dir, name, contents.get(name), e.getValue() == Folder.UPDATE);
                    case REMOVE -> {
                        moveAside(remote, dir, name);
                        remote.deleteTree(dir + "/." + name + REPLACED);
                    }
                    case NONE -> {
                    }
                }
            }
            for (String name : upload) {
                remote.mkdirs(RESOURCE_PACK_UPDATE_DIR);
                remote.write(RESOURCE_PACK_UPDATE_DIR + "/" + name + ".zip", resourcePacks.get(name));
            }
            for (String name : deleteResourcePack) {
                remote.delete(RESOURCE_PACK_UPDATE_DIR + "/" + name + ".zip");
                remote.delete(RESOURCE_PACK_DIR + "/" + name + ".zip");
            }
            if (!oldExists || !report.manifest().equals(old)) {
                remote.write(REMOTE_MANIFEST, report.manifest().toJson().getBytes(StandardCharsets.UTF_8));
            }
        }

        DatapackManifest interim() {
            return interim;
        }
    }

    private DatapackDeployer() {
    }

    /** Reads the server and the set, and works out what to do; writes nothing. */
    public static Plan plan(String server, Path deployDir, RemoteFiles remote) throws IOException {
        DatapackManifest local = DatapackManifest.readSet(deployDir);
        Optional<byte[]> oldBytes = remote.read(REMOTE_MANIFEST);
        DatapackManifest onServer = oldBytes.isEmpty() ? DatapackManifest.empty() : parseServerManifest(server, oldBytes.get());
        if (local.packs().isEmpty() && onServer.packs().isEmpty()) {
            return new Plan(onServer, true, onServer, Map.of(), Set.of(), Set.of(), Set.of(), Map.of(), Map.of(), Report.none());
        }
        Map<String, SortedMap<String, byte[]>> contents = new HashMap<>();
        Map<String, byte[]> resourcePacks = new HashMap<>();
        checkLocal(deployDir, local, contents, resourcePacks);

        String world = levelName(server, remote);
        String note = null;
        DatapackManifest old = onServer;
        if (onServer.world() != null && !onServer.world().equals(world)) {
            note = "level-name is now `" + world + "` (it was `" + onServer.world() + "`): the datapacks go into `"
                    + world + "/datapacks`, and `" + onServer.world() + "/datapacks` is left as it is";
            old = DatapackManifest.empty();
        }
        String dir = world + "/datapacks";
        SortedMap<String, Boolean> present = remote.entries(dir);
        Set<String> resourcePackFiles = new HashSet<>();
        boolean anyResourcePack = local.packs().values().stream().anyMatch(p -> p.resourcePack() != null)
                || old.packs().values().stream().anyMatch(p -> p.resourcePack() != null);
        if (anyResourcePack) {
            for (String f : remote.list(RESOURCE_PACK_DIR)) {
                resourcePackFiles.add(RESOURCE_PACK_DIR + "/" + f);
            }
            for (String f : remote.list(RESOURCE_PACK_UPDATE_DIR)) {
                resourcePackFiles.add(RESOURCE_PACK_UPDATE_DIR + "/" + f);
            }
        }

        SortedSet<String> names = new TreeSet<>(local.packs().keySet());
        names.addAll(old.packs().keySet());
        SortedMap<String, Folder> folders = new TreeMap<>();
        Set<String> upload = new TreeSet<>();
        Set<String> deleteResourcePack = new TreeSet<>();
        SortedSet<String> installed = new TreeSet<>();
        SortedSet<String> updated = new TreeSet<>();
        SortedSet<String> removed = new TreeSet<>();
        SortedSet<String> unchanged = new TreeSet<>();
        SortedSet<String> newResourcePacks = new TreeSet<>();
        SortedSet<String> worldgen = new TreeSet<>();
        List<Skipped> skipped = new ArrayList<>();
        SortedMap<String, DatapackManifest.Pack> interim = new TreeMap<>(old.packs());
        SortedMap<String, DatapackManifest.Pack> after = new TreeMap<>();

        for (String name : names) {
            DatapackManifest.Pack want = local.packs().get(name);
            DatapackManifest.Pack had = old.packs().get(name);
            String folder = dir + "/" + name;
            boolean exists = present.containsKey(name);
            Folder action = Folder.NONE;
            Skipped skip = null;
            boolean changed;

            if (had == null) {
                // New to the deploy: only if nothing of that name is there already.
                List<String> foreign = new ArrayList<>();
                if (exists) {
                    foreign.add(folder);
                }
                if (want.resourcePack() != null) {
                    foreign.addAll(resourcePackFilesOf(name, resourcePackFiles));
                }
                if (!foreign.isEmpty()) {
                    skip = new Skipped(name, "not installed: already on the server, and not put there by the deploy", foreign);
                } else {
                    action = Folder.INSTALL;
                }
                changed = skip == null;
            } else {
                SortedMap<String, String> serverFiles = null;
                if (exists && present.get(name) && (want == null || had.pending() != null || !had.files().equals(want.files()))) {
                    serverFiles = readFolder(remote, folder);
                }
                boolean ours = !exists || (serverFiles == null ? present.get(name)
                        : serverFiles.equals(had.files()) || serverFiles.equals(had.pending())
                        || (want != null && serverFiles.equals(want.files())));
                if (!ours) {
                    List<String> diff = exists && !present.get(name) ? List.of(folder + " is a file, not a folder")
                            : differences(folder, had.files(), serverFiles);
                    skip = new Skipped(name, want == null ? "not removed: edited on the server" : "not updated: edited on the server", diff);
                    changed = false;
                } else if (want == null) {
                    action = exists ? Folder.REMOVE : Folder.NONE;
                    changed = true;
                } else if (!exists) {
                    action = Folder.INSTALL;
                    changed = true;
                } else if (serverFiles == null || serverFiles.equals(want.files())) {
                    changed = !had.files().equals(want.files());
                } else {
                    action = Folder.UPDATE;
                    changed = true;
                }
                if (skip == null && want != null && want.resourcePack() != null && had.resourcePack() == null) {
                    List<String> foreign = resourcePackFilesOf(name, resourcePackFiles);
                    if (!foreign.isEmpty()) {
                        skip = new Skipped(name, "not updated: its resource pack is already on the server, and not put there by the deploy", foreign);
                        action = Folder.NONE;
                        changed = false;
                    }
                }
            }

            if (skip != null) {
                skipped.add(skip);
                if (had != null) {
                    after.put(name, had);
                }
                continue;
            }
            folders.put(name, action);
            DatapackManifest.ResourcePack wantRp = want == null ? null : want.resourcePack();
            DatapackManifest.ResourcePack hadRp = had == null ? null : had.resourcePack();
            boolean sendRp = wantRp != null && (hadRp == null || !hadRp.sha256().equals(wantRp.sha256()));
            boolean dropRp = wantRp == null && hadRp != null;
            if (sendRp) {
                upload.add(name);
                if (hadRp == null) {
                    newResourcePacks.add(name);
                }
            }
            if (dropRp) {
                deleteResourcePack.add(name);
            }

            if (want == null) {
                removed.add(name);
            } else if (had == null || (action == Folder.INSTALL && !exists)) {
                installed.add(name);
            } else if (changed || sendRp || dropRp) {
                updated.add(name);
            } else {
                unchanged.add(name);
            }
            if (changed && (hasWorldgen(want) || hasWorldgen(had))) {
                worldgen.add(name);
            }

            if (want != null) {
                after.put(name, want);
                DatapackManifest.Pack during = had == null ? want : had;
                if (action == Folder.INSTALL || action == Folder.UPDATE) {
                    during = during.withPending(had == null ? null : want.files());
                }
                if (sendRp) {
                    during = during.withResourcePack(new DatapackManifest.ResourcePack(wantRp.file(), Deployer.UNKNOWN_SHA256));
                }
                interim.put(name, during);
            }
        }

        Set<String> leftovers = new TreeSet<>();
        for (String name : interim.keySet()) {
            for (String suffix : List.of(STAGING, REPLACED)) {
                if (present.containsKey("." + name + suffix)) {
                    leftovers.add("." + name + suffix);
                }
            }
        }
        DatapackManifest afterManifest = new DatapackManifest(world, after);
        Report report = new Report(world, note, installed, updated, removed, unchanged, List.copyOf(skipped),
                Collections.unmodifiableSortedSet(new TreeSet<>(upload)), Collections.unmodifiableSortedSet(new TreeSet<>(deleteResourcePack)),
                newResourcePacks, worldgen, afterManifest);
        return new Plan(onServer, oldBytes.isPresent(), new DatapackManifest(world, interim), folders, upload,
                deleteResourcePack, leftovers, contents, resourcePacks, report);
    }

    /** The world folder: {@code level-name} from server.properties, {@value #DEFAULT_WORLD} without one. */
    static String levelName(String server, RemoteFiles remote) throws IOException {
        Optional<byte[]> bytes = remote.read(SERVER_PROPERTIES);
        if (bytes.isEmpty()) {
            return DEFAULT_WORLD;
        }
        Properties properties = new Properties();
        properties.load(new StringReader(new String(bytes.get(), StandardCharsets.UTF_8)));
        String name = properties.getProperty("level-name", "").strip();
        if (name.isEmpty()) {
            return DEFAULT_WORLD;
        }
        try {
            return DatapackManifest.checkPath(name, "world");
        } catch (DeployException e) {
            throw new DeployException("level-name in " + server + "'s " + SERVER_PROPERTIES + ": " + e.getMessage()
                    + "; nothing was uploaded", e);
        }
    }

    /** Fills {@code dir/.<name>.deploying}, then swaps it in for {@code dir/<name>}. */
    private static void replace(RemoteFiles remote, String dir, String name, SortedMap<String, byte[]> files,
                                boolean update) throws IOException {
        String staging = dir + "/." + name + STAGING;
        remote.mkdirs(staging);
        Set<String> made = new HashSet<>();
        made.add(staging);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (e.getKey().equals("pack.mcmeta")) {
                continue;
            }
            String path = staging + "/" + e.getKey();
            String parent = path.substring(0, path.lastIndexOf('/'));
            if (made.add(parent)) {
                remote.mkdirs(parent);
            }
            remote.put(path, e.getValue());
        }
        if (update) {
            moveAside(remote, dir, name);
        }
        remote.put(staging + "/pack.mcmeta", files.get("pack.mcmeta"));
        remote.rename(staging, dir + "/" + name);
        if (update) {
            remote.deleteTree(dir + "/." + name + REPLACED);
        }
    }

    /** Renames {@code dir/<name>} to {@code .<name>.replaced}, which stops being a pack at once. */
    private static void moveAside(RemoteFiles remote, String dir, String name) throws IOException {
        String replaced = dir + "/." + name + REPLACED;
        remote.rename(dir + "/" + name, replaced);
        remote.delete(replaced + "/pack.mcmeta");
    }

    /** Every file under {@code folder}, by path, with its sha256. */
    static SortedMap<String, String> readFolder(RemoteFiles remote, String folder) throws IOException {
        SortedMap<String, String> files = new TreeMap<>();
        readFolder(remote, folder, "", files);
        return files;
    }

    private static void readFolder(RemoteFiles remote, String folder, String prefix, SortedMap<String, String> files)
            throws IOException {
        for (Map.Entry<String, Boolean> e : remote.entries(folder + (prefix.isEmpty() ? "" : "/" + prefix)).entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "/" + e.getKey();
            if (e.getValue()) {
                readFolder(remote, folder, path, files);
            } else {
                byte[] data = remote.read(folder + "/" + path)
                        .orElseThrow(() -> new IOException(folder + "/" + path + " vanished while being read"));
                files.put(path, Sha256.of(data));
            }
        }
    }

    /** What differs between the files the deploy put in {@code folder} and those there now. */
    static List<String> differences(String folder, SortedMap<String, String> deployed, SortedMap<String, String> now) {
        List<String> lines = new ArrayList<>();
        SortedSet<String> paths = new TreeSet<>(deployed.keySet());
        paths.addAll(now.keySet());
        for (String path : paths) {
            String was = deployed.get(path);
            String is = now.get(path);
            if (was == null) {
                lines.add("added " + folder + "/" + path);
            } else if (is == null) {
                lines.add("deleted " + folder + "/" + path);
            } else if (!was.equals(is)) {
                lines.add("changed " + folder + "/" + path);
            }
        }
        return lines;
    }

    private static List<String> resourcePackFilesOf(String name, Set<String> files) {
        List<String> found = new ArrayList<>();
        for (String path : List.of(RESOURCE_PACK_DIR + "/" + name + ".zip", RESOURCE_PACK_UPDATE_DIR + "/" + name + ".zip")) {
            if (files.contains(path)) {
                found.add(path);
            }
        }
        return found;
    }

    private static boolean hasWorldgen(DatapackManifest.Pack pack) {
        return pack != null && pack.files().keySet().stream().anyMatch(p -> WORLDGEN.matcher(p).find());
    }

    /** Checks the set's zips against datapacks.json, and reads them. */
    private static void checkLocal(Path deployDir, DatapackManifest local, Map<String, SortedMap<String, byte[]>> contents,
                                   Map<String, byte[]> resourcePacks) throws IOException {
        for (Map.Entry<String, DatapackManifest.Pack> e : local.packs().entrySet()) {
            DatapackManifest.Pack pack = e.getValue();
            Path zip = deployDir.resolve(pack.file());
            if (!Files.isRegularFile(zip)) {
                throw new DeployException(zip + " (" + e.getKey() + ") is missing; nothing was uploaded");
            }
            SortedMap<String, byte[]> files = Datapack.read(zip);
            SortedMap<String, String> hashes = new TreeMap<>();
            files.forEach((path, data) -> hashes.put(path, Sha256.of(data)));
            if (!hashes.equals(pack.files())) {
                throw new DeployException(zip + " (" + e.getKey() + ") does not match " + DatapackManifest.FILE_NAME
                        + "; nothing was uploaded");
            }
            contents.put(e.getKey(), files);
            if (pack.resourcePack() != null) {
                Path rp = deployDir.resolve(pack.resourcePack().file());
                if (!Files.isRegularFile(rp) || !Sha256.of(rp).equals(pack.resourcePack().sha256())) {
                    throw new DeployException(rp + " (" + e.getKey() + ") does not match " + DatapackManifest.FILE_NAME
                            + "; nothing was uploaded");
                }
                resourcePacks.put(e.getKey(), Files.readAllBytes(rp));
            }
        }
    }

    private static DatapackManifest parseServerManifest(String server, byte[] bytes) {
        try {
            return DatapackManifest.parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (DeployException e) {
            throw new DeployException(REMOTE_MANIFEST + " on " + server + " is unreadable (" + e.getMessage()
                    + "); fix or delete it (deleting it makes the deploy treat every datapack folder as not its own);"
                    + " nothing was uploaded", e);
        }
    }
}
