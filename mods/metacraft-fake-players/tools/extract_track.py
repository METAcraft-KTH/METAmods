import sqlite3, sys
db, out = sys.argv[1], sys.argv[2]
c = sqlite3.connect(db)
LEN = 900
rows = c.execute("select t, player_id, x, y, z from samples where map_id=1 order by t").fetchall()
tmin = rows[0][0]; tmax = rows[-1][0]
# per-5s-bucket: sliding window search over 60s steps, score = players with >20 block span
import collections, bisect
byp = collections.defaultdict(list)
for t,p,x,y,z in rows: byp[p].append((t,x,y,z))
def score(start):
    s=0
    for p,l in byp.items():
        i=bisect.bisect_left(l,(start,)); j=bisect.bisect_left(l,(start+LEN,))
        w=l[i:j]
        if len(w)>60:
            xs=[r[1] for r in w]; zs=[r[3] for r in w]
            if max(xs)-min(xs)+max(zs)-min(zs)>20: s+=len(w)
    return s
best=max(range(tmin, tmax-LEN, 300), key=score)
print("start", best, "score", score(best), file=sys.stderr)
# build segments: split at gaps > 30s
lines=[]; n=0
for p,l in sorted(byp.items()):
    w=[r for r in l if best<=r[0]<best+LEN]
    seg=[]
    for r in w:
        if seg and r[0]-seg[-1][0]>30:
            if len(seg)>=12: lines.append(seg)
            seg=[]
        seg.append(r)
    if len(seg)>=12: lines.append(seg)
with open(out,"w") as f:
    f.write("# Anonymised 15 min player movement. seg <id> <startSec>; then <dtSec> <x> <y> <z> deltas.\n")
    for i,seg in enumerate(lines):
        f.write(f"S {i} {seg[0][0]-best}\n")
        px=py=pz=pt=None
        for t,x,y,z in seg:
            if px is None: f.write(f"{x} {y} {z} 0\n")
            else: f.write(f"{x-px} {y-py} {z-pz} {t-pt}\n")
            px,py,pz,pt=x,y,z,t
print(len(lines),"segments", sum(len(s) for s in lines),"samples", file=sys.stderr)
