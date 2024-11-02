SELECT
    points.player_uuid, team_id, SUM(points) AS total_points
FROM points
JOIN player_teams
    ON player_teams.player_uuid = points.player_uuid
WHERE
    points.minigame_id = ?
GROUP BY
    points.player_uuid, team_id
ORDER BY
    team_id, total_points DESC