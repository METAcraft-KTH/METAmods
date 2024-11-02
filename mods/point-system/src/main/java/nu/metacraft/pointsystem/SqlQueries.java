package nu.metacraft.pointsystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class SqlQueries {
    public final String addPoints;
    public final String getPlayerPoints;
    public final String getPlayerPointsByMinigame;
    public final String getTeamPoints;
    public final String getTeamPointsByMinigame;
    public final String getMinigameIds;

    public SqlQueries(Path folder) throws IOException {
        this.addPoints = read(folder, "addPoints");
        this.getPlayerPoints = read(folder, "getPlayerPoints");
        this.getPlayerPointsByMinigame = read(folder, "getPlayerPointsByMinigame");
        this.getTeamPoints = read(folder, "getTeamPoints");
        this.getTeamPointsByMinigame = read(folder, "getTeamPointsByMinigame");
        this.getMinigameIds = read(folder, "getMinigameIds");
    }

    private String read(Path folder, String name) throws IOException {
        Path path = folder.resolve(name + ".sql");
        if (Files.notExists(path)) {
            try (InputStream stream = SqlQueries.class.getClassLoader().getResourceAsStream("sql/" + name + ".sql")) {
                if (stream == null) {
                    throw new RuntimeException("Sql query '" + name + "' not found.");
                }
                Files.copy(stream, path);
            }
        }
        return Files.readString(path);
    }
}
