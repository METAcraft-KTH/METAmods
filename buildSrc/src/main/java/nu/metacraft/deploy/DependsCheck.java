package nu.metacraft.deploy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Every {@code depends} entry naming a mod built in this repo must be in the same set. */
public final class DependsCheck {
    private DependsCheck() {
    }

    public static void check(String server, Map<String, ModInfo> set, Set<String> repoModIds) {
        List<String> problems = new ArrayList<>();
        for (ModInfo mod : new TreeMap<>(set).values()) {
            for (String dep : mod.depends()) {
                if (repoModIds.contains(dep) && !set.containsKey(dep)) {
                    problems.add(mod.id() + " needs " + dep + ", which is not on " + server + "'s list");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new DeployException(String.join("\n", problems));
        }
    }
}
