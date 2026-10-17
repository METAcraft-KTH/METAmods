package nu.metacraft.deploy;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/** Old (the server's) against new (the set being deployed), by mod id. */
public record ManifestDiff(SortedSet<String> upload, SortedSet<String> remove, SortedSet<String> unchanged) {

    public static ManifestDiff of(Manifest old, Manifest now) {
        SortedSet<String> upload = new TreeSet<>();
        SortedSet<String> remove = new TreeSet<>();
        SortedSet<String> unchanged = new TreeSet<>();
        now.entries().forEach((id, entry) -> {
            Manifest.Entry before = old.entries().get(id);
            if (before != null && before.sha256().equals(entry.sha256()) && before.file().equals(entry.file())) {
                unchanged.add(id);
            } else {
                upload.add(id);
            }
        });
        for (String id : old.entries().keySet()) {
            if (!now.entries().containsKey(id)) {
                remove.add(id);
            }
        }
        return new ManifestDiff(Collections.unmodifiableSortedSet(upload), Collections.unmodifiableSortedSet(remove),
                Collections.unmodifiableSortedSet(unchanged));
    }
}
