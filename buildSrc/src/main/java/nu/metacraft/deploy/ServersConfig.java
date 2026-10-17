package nu.metacraft.deploy;

import groovy.json.JsonSlurper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** {@code deploy/servers.json}: which servers a branch deploys to, and how to reach each. */
public record ServersConfig(Map<String, List<String>> branches, Map<String, Server> servers) {

    public record Server(String host, int port, String user) {}

    public static ServersConfig read(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DeployException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    public static ServersConfig parse(String json) {
        Object parsed;
        try {
            parsed = new JsonSlurper().parseText(json);
        } catch (RuntimeException e) {
            throw new DeployException("deploy/servers.json is not valid JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("branches") instanceof Map<?, ?> branchMap)
                || !(root.get("servers") instanceof Map<?, ?> serverMap)) {
            throw new DeployException("deploy/servers.json needs \"branches\" and \"servers\" objects");
        }
        Map<String, Server> servers = new TreeMap<>();
        for (Map.Entry<?, ?> e : serverMap.entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> m) || !(m.get("host") instanceof String host)
                    || !(m.get("port") instanceof Number port) || !(m.get("user") instanceof String user)) {
                throw new DeployException("deploy/servers.json: server " + name + " needs \"host\", \"port\" and \"user\"");
            }
            servers.put(name, new Server(host, port.intValue(), user));
        }
        Map<String, List<String>> branches = new TreeMap<>();
        for (Map.Entry<?, ?> e : branchMap.entrySet()) {
            String branch = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof List<?> list)) {
                throw new DeployException("deploy/servers.json: branch " + branch + " must list server names");
            }
            List<String> names = new ArrayList<>();
            for (Object o : list) {
                String name = String.valueOf(o);
                if (!servers.containsKey(name)) {
                    throw new DeployException("deploy/servers.json: branch " + branch + " deploys to " + name
                            + ", which is not under \"servers\"");
                }
                names.add(name);
            }
            branches.put(branch, List.copyOf(names));
        }
        return new ServersConfig(Collections.unmodifiableMap(branches), Collections.unmodifiableMap(servers));
    }

    public Server server(String name) {
        Server server = servers.get(name);
        if (server == null) {
            throw new DeployException("unknown server " + name + ": not in deploy/servers.json");
        }
        return server;
    }
}
