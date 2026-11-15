# Museum Collection Competition

A server-side item collection event between three teams: `east`, `west` and `south`.
Players register items with a collector villager; the scores live in MySQL, where the website reads them.
Players need no client mod.

- A player right-clicks a **collector** villager holding an item in the main hand. The item is registered and
  **kept**: nothing is taken from the inventory.
- A player can register each item **once**. Any number of players, on any team, can register the same item.
- Only the item's registry id counts (`minecraft:diamond_sword`). Names, enchantments, damage, trims, potion
  contents and every other component are ignored.
- A team's score is the number of **different** items its players have registered.

## Setting it up

1. Create a database (schema) and a user for it on the MySQL or MariaDB server. The tables are created by the
   mod at the first query.
2. Start the server once to get `config/museum-collection.json5`, fill in the `database` block and set
   `enabled: true`. The password can come from an environment variable instead (`password_env`,
   `MUSEUM_DB_PASSWORD` by default), which keeps it out of the file.
3. `/reload`. The module connects within a second; a wrong setting is logged, and fixed the same way.
4. `/museum admin collector spawn` where the collector should stand.

While `enabled` is false (the default) the module connects to nothing and does nothing.

## Teams

The teams are the event's own, stored in the database. Nobody is put in a vanilla scoreboard team.

A player on no team who right-clicks a collector (or runs `/museum join`) gets the **team selection dialog**
instead: one button per team. The choice is made once; after it only an admin moves a player
(`/museum admin team set`), and what the player has registered moves with them.

## Commands

| Command | Who | What |
| --- | --- | --- |
| `/museum` | everyone | The teams' scores, your team and how many items you have |
| `/museum top` | everyone | The ten best players |
| `/museum join` | everyone | The team selection dialog, while on no team |
| `/museum admin team set <players> <east\|west\|south>` | admins | Puts players on a team, online or not |
| `/museum admin team get <player>` | admins | A player's team and item count |
| `/museum admin collector spawn` | admins | A collector villager where you stand, facing you |
| `/museum admin collector tag\|untag <villager>` | admins | Makes an existing villager a collector, or ordinary again |
| `/museum admin open` / `close` | admins | Whether collectors take items (saved in the config) |
| `/museum admin refresh` | admins | Reads the standings from the database now |

Admins are game masters, or whoever has the `museum-collection:admin` permission.

A collector is a villager with the scoreboard tag `museum_collector` (`/tag` works on it too). Its trade screen
never opens. A spawned one has no AI, makes no sound, can't be hurt and doesn't despawn; remove it with
`/kill`. Other villagers are untouched.

## Participation reward

When a player registers their 10th item (`participation_threshold`), the server runs
`/itemskins grant <player> chest_lid_hat` as the console (`participation_reward_command`, `{player}` standing for
the name; `""` turns it off). It runs once, at exactly the threshold, and also when the player logged out before
the database answered: `/itemskins grant` takes offline players. The command shows in the server log. The skin
`chest_lid_hat` must be loaded in metacraft-skins.

Every other reward is handed out by hand.

## Database

Two tables, with the prefix from `database.table_prefix` (`museum_`). All queries run on the module's own
threads through a small connection pool (HikariCP), never the server thread, and all are prepared statements.

```sql
CREATE TABLE IF NOT EXISTS museum_players (
  uuid CHAR(36) NOT NULL,
  username VARCHAR(16) NOT NULL,
  team ENUM('east', 'west', 'south') NOT NULL,
  skin VARCHAR(64) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (uuid),
  KEY idx_museum_players_team (team)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS museum_submissions (
  player_uuid CHAR(36) NOT NULL,
  item_id VARCHAR(128) NOT NULL,
  team ENUM('east', 'west', 'south') NOT NULL,
  submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_uuid, item_id),
  KEY idx_museum_submissions_team_item (team, item_id),
  CONSTRAINT fk_museum_submissions_player FOREIGN KEY (player_uuid) REFERENCES museum_players (uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- `museum_players` has a row for every player on a team, and none for a player who hasn't picked one. `username`
  is brought up to date when the player joins the server or registers an item, and so is `skin`.
- `museum_players.skin` is the hash of the player's skin on `textures.minecraft.net`, read from the profile
  Mojang signed at login (no API request). The website draws heads from it: it fetches
  `https://textures.minecraft.net/texture/<skin>` once per skin, and a changed skin is a new hash. It is null
  for a player with no skin texture (offline mode), and a lookup that doesn't know the skin (an admin command on
  an offline player) keeps the stored one.
- `museum_submissions.team` is always its player's current team. A team's score:
  `SELECT team, COUNT(DISTINCT item_id) FROM museum_submissions GROUP BY team`.
- The primary key `(player_uuid, item_id)` is what stops a double registration, whatever two clicks or two
  servers do at the same moment.

If the database can't be reached, players are told to try again and nothing is lost or queued; the module
picks up again when it is back.

## Development

`./gradlew :mods:museum-collection:test` runs the SQL against an in-memory H2 in MySQL mode.
