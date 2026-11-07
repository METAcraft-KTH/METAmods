#!/usr/bin/env python3
"""Cut a fake player scenario CSV out of a player tracks database (tracks.db, `samples` table).

  extract_scenario.py tracks.db out.csv --start <unix seconds> [--length 900]

Writes `session,t,x,y,z,dim`: one session per continuous stretch of one player (a gap of more than 30 seconds
starts a new session). Player identities are dropped, sessions are numbered from 0.
Use `--list` to print the busiest windows instead, to pick a --start.
"""
import argparse, bisect, collections, math, sqlite3, statistics

DIMS = {1: "minecraft:overworld", 2: "minecraft:the_nether", 3: "minecraft:the_end"}
GAP = 30  # seconds
MIN_SAMPLES = 12


def load(db):
    c = sqlite3.connect(db)
    rows = c.execute(
        "select t, map_id, player_id, x, y, z from samples where map_id in (1, 2, 3) order by t").fetchall()
    by_player = collections.defaultdict(list)
    for t, m, p, x, y, z in rows:
        by_player[p].append((t, m, x, y, z))
    return by_player, rows[0][0], rows[-1][0]


def window(l, start, length):
    return l[bisect.bisect_left(l, (start,)):bisect.bisect_left(l, (start + length,))]


def list_windows(by_player, tmin, tmax, length):
    for start in range(tmin, tmax - length, 300):
        means = []
        for l in by_player.values():
            w = window(l, start, length)
            if len(w) >= 60:
                means.append((statistics.mean(r[2] for r in w) // 256, statistics.mean(r[4] for r in w) // 256, w[0][1]))
        if len(means) >= 6:
            print(f"--start {start}  players {len(means):3d}  distinct 256-block areas {len(set(means)):3d}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("db")
    ap.add_argument("out", nargs="?")
    ap.add_argument("--start", type=int)
    ap.add_argument("--length", type=int, default=900)
    ap.add_argument("--list", action="store_true")
    a = ap.parse_args()
    by_player, tmin, tmax = load(a.db)
    if a.list:
        return list_windows(by_player, tmin, tmax, a.length)
    sessions = []
    for _, l in sorted(by_player.items()):
        seg = []
        for r in window(l, a.start, a.length):
            if seg and r[0] - seg[-1][0] > GAP:
                if len(seg) >= MIN_SAMPLES:
                    sessions.append(seg)
                seg = []
            seg.append(r)
        if len(seg) >= MIN_SAMPLES:
            sessions.append(seg)
    with open(a.out, "w") as f:
        f.write("session,t,x,y,z,dim\n")
        for i, seg in enumerate(sessions):
            for t, m, x, y, z in seg:
                f.write(f"{i},{t - a.start},{x},{y},{z},{DIMS[m]}\n")
    print(f"{len(sessions)} sessions, {sum(len(s) for s in sessions)} samples")


if __name__ == "__main__":
    main()
