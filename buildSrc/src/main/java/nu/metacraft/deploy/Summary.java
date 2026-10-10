package nu.metacraft.deploy;

import java.util.SortedSet;

/** The job summary and release notes for one server's deploy. */
public final class Summary {
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
        section(md, "uploaded", report.uploaded(), report.manifest());
        section(md, "removed at next restart", report.removed(), null);
        section(md, "unchanged", report.unchanged(), report.manifest());
        md.append("Changes apply at ").append(report.server()).append("'s next restart.\n");
        return md.toString();
    }

    private static void section(StringBuilder md, String title, SortedSet<String> ids, Manifest manifest) {
        md.append("**").append(title).append("** (").append(ids.size()).append(")\n");
        if (ids.isEmpty()) {
            md.append("- none\n");
        }
        for (String id : ids) {
            md.append("- `").append(id).append('`');
            if (manifest != null) {
                md.append(' ').append(manifest.entries().get(id).version());
            }
            md.append('\n');
        }
        md.append('\n');
    }
}
