# East vs West vs Rest

A server-side item collection event between three teams: `east`, `west` and `rest` ("south" in flavour text).
Players register items with a collector villager; the scores live in MySQL, where the website reads them.
Vanilla clients need only the server's resource pack (for the legendary hat's model).

- A player right-clicks a **collector** villager holding an item in the main hand. The item is registered and
  **kept**: nothing is taken from the inventory.
- A player can register each item **once**. Any number of players, on any team, can register the same item.
- Only the item's registry id counts (`minecraft:diamond_sword`). Names, enchantments, damage, trims, potion
  contents and every other component are ignored.
- A team's score is the number of **different** items its players have registered.

## Setting it up

1. Create a database (schema) and a user for it on the MySQL or MariaDB server. The tables are created by the
   mod at the first query.
2. Start the server once to get `config/east-vs-west.json5`, fill in the `database` block and set
   `enabled: true`. The password can come from an environment variable instead (`password_env`,
   `EVW_DB_PASSWORD` by default), which keeps it out of the file.
3. `/reload`. The module connects within a second; a wrong setting is logged, and fixed the same way.
4. `/evw admin collector spawn` where the collector should stand.

While `enabled` is false (the default) the module connects to nothing and does nothing.

## Teams

The teams are the event's own, stored in the database. Nobody is put in a vanilla scoreboard team.

A player on no team who right-clicks a collector (or runs `/evw join`) gets the **team selection dialog**
instead: one button per team. The choice is made once; after it only an admin moves a player
(`/evw admin team set`), and what the player has registered moves with them.

## Commands

| Command | Who | What |
| --- | --- | --- |
| `/evw` | everyone | The teams' scores, your team, your items and your hat |
| `/evw top` | everyone | The ten best players |
| `/evw join` | everyone | The team selection dialog, while on no team |
| `/evw admin team set <players> <east\|west\|rest>` | admins | Puts players on a team, online or not |
| `/evw admin team get <player>` | admins | A player's team and item count |
| `/evw admin collector spawn` | admins | A collector villager where you stand, facing you |
| `/evw admin collector tag\|untag <villager>` | admins | Makes an existing villager a collector, or ordinary again |
| `/evw admin open` / `close` | admins | Whether collectors take items (saved in the config) |
| `/evw admin refresh` | admins | Reads the standings from the database now |

Admins are game masters, or whoever has the `east-vs-west:admin` permission.

A collector is a villager with the scoreboard tag `evw_collector` (`/tag` works on it too). Its trade screen
never opens. A spawned one has no AI, makes no sound, can't be hurt and doesn't despawn; remove it with
`/kill`. Other villagers are untouched.

## Hats

Hats are item displays riding the wearer's head. They are not items: nothing is in the helmet slot, and they
can't be dropped, taken or duplicated. They appear and disappear by themselves, and are hidden on a player
who is invisible, vanished or spectating.

- **Team hat**: from `participation_threshold` registered items (10) on. One per team, named in the config:
  "Humble east", "West is best", "Sophisticated south". By default a wool cap in the team's colour; `item`,
  `item_model`, `scale` and `y_offset` change it to anything a resource pack has a model for.
- **Legendary hat**: the top `legendary.top_count` players (5) across all teams, by items registered, ties
  going to who got there first. The lid of an ender chest, with portal particles drifting in. It replaces the
  team hat, and follows the leaderboard by itself.

## Database

Two tables, with the prefix from `database.table_prefix` (`evw_`). All queries run on the module's own
threads through a small connection pool (HikariCP), never the server thread, and all are prepared statements.

```sql
CREATE TABLE IF NOT EXISTS evw_players (
  uuid CHAR(36) NOT NULL,
  username VARCHAR(16) NOT NULL,
  team ENUM('east', 'west', 'rest') NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (uuid),
  KEY idx_evw_players_team (team)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS evw_submissions (
  player_uuid CHAR(36) NOT NULL,
  item_id VARCHAR(128) NOT NULL,
  team ENUM('east', 'west', 'rest') NOT NULL,
  submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_uuid, item_id),
  KEY idx_evw_submissions_team_item (team, item_id),
  CONSTRAINT fk_evw_submissions_player FOREIGN KEY (player_uuid) REFERENCES evw_players (uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- `evw_players` has a row for every player on a team, and none for a player who hasn't picked one. `username`
  is brought up to date when the player joins the server or registers an item.
- `evw_submissions.team` is always its player's current team. A team's score:
  `SELECT team, COUNT(DISTINCT item_id) FROM evw_submissions GROUP BY team`.
- The primary key `(player_uuid, item_id)` is what stops a double registration, whatever two clicks or two
  servers do at the same moment.

If the database can't be reached, players are told to try again and nothing is lost or queued; the module
picks up again when it is back.

## Development

`./gradlew :mods:east-vs-west:test` runs the SQL against an in-memory H2 in MySQL mode.
