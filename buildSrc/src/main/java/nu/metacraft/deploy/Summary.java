package nu.metacraft.deploy;

import java.util.List;
import java.util.SortedSet;
import java.util.function.Function;

/** The job summary and release notes for one server's deploy. */
public final class Summary {
    /** The most files a skipped pack lists; the rest are counted. */
    static final int MAX_FILES = 20;

    private Summary() {
    }

    public static String markdown(Deployer.Report report) {
        StringBuilder md = new StringBuilder();
        md.append("### ").append(report.server());
        if (report.dryRun()) {
            md.append(" (dry run: nothing was uploaded)");
        }
        md.append("\n\n");
        if (report.firstDeploy()) {
            md.append("First deploy with separate jars: the `metacraft` bundle is deleted at the next restart.\n\n");
        }
        DatapackDeployer.Report packs = report.datapacks();
        warnings(md, packs);
        section(md, "uploaded", report.uploaded(), id -> report.manifest().entries().get(id).version());
        section(md, "removed at next restart", report.removed(), null);
        section(md, "unchanged", report.unchanged(), id -> report.manifest().entries().get(id).version());
        if (packs.any()) {
            datapacks(md, packs);
        }
        md.append(applies(report)).append('\n');
        return md.toString();
    }

    /** One line per pack the deploy left alone, for the job's annotations. */
    public static List<String> warningLines(Deployer.Report report) {
        return report.datapacks().skipped().stream()
                .map(s -> report.server() + ": datapack " + s.name() + " " + s.reason() + " (" + String.join(", ", s.files()) + ")")
                .toList();
    }

    /** When the changes take effect. */
    static String applies(Deployer.Report report) {
        DatapackDeployer.Report packs = report.datapacks();
        StringBuilder line = new StringBuilder();
        if (report.modsChanged()) {
            line.append("**Needs a restart**: mod jars changed. Changes apply at ").append(report.server())
                    .append("'s next restart.");
        } else if (!packs.worldgen().isEmpty()) {
            line.append("**Needs a restart**: ").append(codes(packs.worldgen()))
                    .append(" changed worldgen or dimensions, which `/reload` doesn't apply.");
        } else if (packs.datapacksChanged()) {
            line.append("Datapacks changed: apply with `/reload` or the next restart.");
        } else if (!packs.resourcePacksChanged()) {
            line.append("Nothing changed.");
        }
        if (packs.resourcePacksChanged()) {
            if (line.length() > 0) {
                line.append('\n');
            }
            line.append("Resource packs changed: apply with `/metacraft-resource-pack reload` or the next restart.");
        }
        return line.toString();
    }

    private static void warnings(StringBuilder md, DatapackDeployer.Report packs) {
        for (DatapackDeployer.Skipped skip : packs.skipped()) {
            md.append("> [!WARNING]\n> Datapack `").append(skip.name()).append("` ").append(skip.reason()).append(":\n");
            List<String> files = skip.files();
            for (String file : files.subList(0, Math.min(files.size(), MAX_FILES))) {
                md.append("> - `").append(file).append("`\n");
            }
            if (files.size() > MAX_FILES) {
                md.append("> - and ").append(files.size() - MAX_FILES).append(" more\n");
            }
            md.append(">\n> The deploy won't touch it until the folder holds what it deployed: delete `")
                    .append(packs.datapacksDir()).append('/').append(skip.name())
                    .append("` (the next deploy installs it again) or undo the change. See deploy/README.md.\n\n");
        }
    }

    private static void datapacks(StringBuilder md, DatapackDeployer.Report packs) {
        md.append("#### datapacks in `").append(packs.datapacksDir()).append("`\n\n");
        if (packs.note() != null) {
            md.append(packs.note()).append(".\n\n");
        }
        Function<String, String> version = name -> packs.manifest().packs().get(name).version();
        section(md, "installed", packs.installed(), version);
        section(md, "updated", packs.updated(), version);
        section(md, "removed", packs.removed(), null);
        section(md, "unchanged", packs.unchanged(), version);
        if (packs.resourcePacksChanged()) {
            md.append("**resource packs** (`").append(DatapackDeployer.RESOURCE_PACK_UPDATE_DIR).append("`)\n");
            for (String name : packs.resourcePacksSent()) {
                md.append("- `").append(name).append(".zip` uploaded\n");
            }
            for (String name : packs.resourcePacksRemoved()) {
                md.append("- `").append(name).append(".zip` removed\n");
            }
            md.append('\n');
        }
        if (!packs.newResourcePacks().isEmpty()) {
            md.append("New resource packs reach nobody until they are global: after the next restart or `/metacraft-resource-pack reload`, set `\"global\": true` on ")
                    .append(codes(packs.newResourcePacks(), ".zip"))
                    .append(" in `config/metacraft-resource-packs/config.json5`, then run `/metacraft-resource-pack reload`. See deploy/README.md.\n\n");
        }
    }

    private static String codes(SortedSet<String> names) {
        return codes(names, "");
    }

    private static String codes(SortedSet<String> names, String suffix) {
        return String.join(", ", names.stream().map(n -> "`" + n + suffix + "`").toList());
    }

    private static void section(StringBuilder md, String title, SortedSet<String> ids, Function<String, String> version) {
        md.append("**").append(title).append("** (").append(ids.size()).append(")\n");
        if (ids.isEmpty()) {
            md.append("- none\n");
        }
        for (String id : ids) {
            md.append("- `").append(id).append('`');
            if (version != null) {
                md.append(' ').append(version.apply(id));
            }
            md.append('\n');
        }
        md.append('\n');
    }
}
