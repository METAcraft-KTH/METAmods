# METAcraft-Fake-Players

Replays recorded player movement with [Carpet](https://modrinth.com/mod/carpet) fake players, for performance
testing (e.g. with spark). The mod only issues `/player`, `/tp` and `/execute` commands, so Carpet is required.

## Commands (admin, or any permission level in a dev environment)

| Command                                         | What it does                                                          |
|-------------------------------------------------|-----------------------------------------------------------------------|
| `/fakeplayers list`                             | Lists the available scenarios.                                        |
| `/fakeplayers start <scenario> [offsetX offsetZ]` | Starts a replay (a numbered run) that loops until stopped.            |
| `/fakeplayers status`                           | Shows every running replay.                                           |
| `/fakeplayers stop [run]`                       | Stops one run, or all of them.                                        |

Several runs can go at once, also of the same scenario. Bots are named `bot_fake_<run>_<session>`. The offset shifts
every position, use it when the world is not a copy of the one the scenario was recorded on.
Replays follow the wall clock, so a lagging server makes the bots skip ahead instead of slowing them down.
While a replay runs, the server's `pause-when-empty-seconds` is turned off (an idle server would stop ticking).

## Scenarios

Bundled in the jar (`data/metacraft-fake-players/scenarios/`):

- `normal`: 44 sessions spread over the whole world, overworld and nether. Ordinary play.
- `light`: 19 sessions, a quiet evening.
- `gathering`: 38 sessions that all gather in one place. A worst case for chunk and entity load.

Add your own by putting `<name>.csv` files in `config/metacraft-fake-players/scenarios/` on the server. They are read
on `/fakeplayers start`, so no restart is needed, and a file there replaces a bundled scenario of the same name.
Names may contain `a-z`, `0-9`, `_` and `-`.

### CSV format

```
session,t,x,y,z,dim
0,3,-547,15,-255,minecraft:the_nether
0,8,-551,15,-256,minecraft:the_nether
1,0,120,64,-30,minecraft:overworld
```

- `session`: one fake player. It joins at its first row and leaves after its last.
- `t`: seconds from the start of the scenario. About 5 seconds between samples works well.
- `x,y,z`: whole blocks.
- `dim`: optional, defaults to `minecraft:overworld`.
- Blank lines and lines starting with `#` are ignored.

`tools/extract_scenario.py` cuts a scenario out of a player tracks database. Run it with `--list` first to see which
time windows have many players spread out, then with `--start <unix seconds>`.
