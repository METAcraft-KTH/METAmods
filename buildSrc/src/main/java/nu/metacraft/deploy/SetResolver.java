package nu.metacraft.deploy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** A list's projects plus everything they depend on, so lib, core and zones never need listing. */
public final class SetResolver {
    private SetResolver() {
    }

    public static SortedSet<String> resolve(String server, List<String> listed, Map<String, Set<String>> projectDeps) {
        Deque<String> todo = new ArrayDeque<>();
        for (String name : listed) {
            if (!projectDeps.containsKey(name)) {
                throw new DeployException("unknown project " + name + " in deploy/" + server
                        + ".txt: not a project under mods/ in settings.gradle");
            }
            todo.add(name);
        }
        SortedSet<String> set = new TreeSet<>();
        while (!todo.isEmpty()) {
            String name = todo.poll();
            if (!set.add(name)) {
                continue;
            }
            for (String dep : projectDeps.getOrDefault(name, Set.of())) {
                if (!projectDeps.containsKey(dep)) {
                    throw new DeployException("project " + name + " depends on " + dep + ", which is not a project under mods/");
                }
                todo.add(dep);
            }
        }
        return set;
    }
}
