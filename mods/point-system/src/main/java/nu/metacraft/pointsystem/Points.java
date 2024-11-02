package nu.metacraft.pointsystem;

import java.util.Map;
import java.util.UUID;

public record Points(Map<Integer, Integer> teamPoints, Map<UUID, Integer> playerPoints) {
}
