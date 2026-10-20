// Nether rifts (nether-rifts): glass cracking over a vanilla nether portal, drawn as pixel art on a grid fixed
// to the world, 16 pixels a block like block textures, so the panels a rift is built from join without a seam
// and nothing swims as the camera moves. Clients see the rift's blocks as a nether portal and draw its swirl
// themselves; each panel is two faces just clear of the portal's slab, one either side. The face on the
// camera's side is the glass's surface: clear but for its cracks, so the portal shows. It cracks like struck
// glass: from an impact or two, fixed to the world, spokes run out and rings join them like a spider web.
// Stage 1 to 3 the web spreads (hairlines, then spokes and rings, then all the way to the frame); at 2 the
// innermost shards fall out and at 3 most of the middle, and through the holes the nether shows: the panel's
// sprite is a 360-degree nether panorama, looked up by view direction like a skybox, so it reads as a far
// place, seen through a haze of the portal's swirl. The face beyond, seen through the portal, is the glass's
// body: it reddens the portal from stage 2, each shard a shade of its own. The edge is a few sparks along the
// outline. Needs rift_common.glsl, Globals, Sampler0, riftWorld (world position less CameraBlockPos) and
// riftView (relative to the camera).

// Exact integer hashing, so the grid is the same anywhere in the world.
uint netherRiftMix(uint h) {
    h ^= h >> 16;
    h *= 0x7feb352du;
    h ^= h >> 15;
    h *= 0x846ca68bu;
    h ^= h >> 16;
    return h;
}

uint netherRiftHashU(ivec3 p) {
    uvec3 u = uvec3(p);
    return netherRiftMix(u.x ^ netherRiftMix(u.y ^ netherRiftMix(u.z + 0x9e3779b9u)));
}

float netherRiftHash(ivec3 p) {
    return float(netherRiftHashU(p) >> 8) * (1.0 / 16777216.0);
}

// 0..1 from a key already hashed once, mixed with a salt.
float netherRiftUnit(uint key, uint salt) {
    return float(netherRiftMix(key ^ salt) >> 8) * (1.0 / 16777216.0);
}

// Value noise on a lattice of 2^shift pixels; off moves it, in lattice cells.
float netherRiftNoise(ivec2 px, int shift, vec2 off, int seed) {
    int size = 1 << shift;
    vec2 local = (vec2(px & ivec2(size - 1)) + 0.5) / float(size) + off;
    vec2 fl = floor(local);
    ivec2 i = (px >> shift) + ivec2(fl);
    vec2 f = local - fl;
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = netherRiftHash(ivec3(i, seed)), b = netherRiftHash(ivec3(i + ivec2(1, 0), seed));
    float c = netherRiftHash(ivec3(i + ivec2(0, 1), seed)), d = netherRiftHash(ivec3(i + ivec2(1, 1), seed));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// This fragment's cell on the world's pixel grid, in the panel's plane: x along the panel (world x or z,
// whichever it spans; the plane's normal comes from the derivatives), y up.
ivec2 netherRiftPixel() {
    vec3 n = cross(dFdx(riftWorld), dFdy(riftWorld));
    bool alongX = abs(n.z) >= abs(n.x);
    vec2 inPlane = vec2(alongX ? riftWorld.x : riftWorld.z, riftWorld.y);
    ivec2 base = ivec2(alongX ? CameraBlockPos.x : CameraBlockPos.z, CameraBlockPos.y);
    return ivec2(floor(inPlane * 16.0)) + base * 16;
}

// The nether portal's purples, dark to pale, and the red they turn to (the cracks and their highlights).
const vec3 NETHER_RIFT_PURPLE[6] = vec3[](
    vec3(20.0, 6.0, 34.0) / 255.0, vec3(48.0, 14.0, 78.0) / 255.0, vec3(92.0, 28.0, 140.0) / 255.0,
    vec3(140.0, 54.0, 196.0) / 255.0, vec3(196.0, 110.0, 236.0) / 255.0, vec3(244.0, 200.0, 255.0) / 255.0
);
const vec3 NETHER_RIFT_RED[6] = vec3[](
    vec3(30.0, 6.0, 14.0) / 255.0, vec3(80.0, 12.0, 30.0) / 255.0, vec3(140.0, 22.0, 44.0) / 255.0,
    vec3(196.0, 46.0, 60.0) / 255.0, vec3(236.0, 110.0, 90.0) / 255.0, vec3(255.0, 210.0, 170.0) / 255.0
);

// The portal's haze over the holes, 0 dark to 1 bright: soft wavy bands, two sets across each other, in a
// plane warped by slow waves so they drift and turn as the portal's own swirl does, on the world's pixel grid.
// Every wave fits a whole number of times in 4 blocks, so it repeats without a seam (and floats stay small), and
// in 8 s, which divides the 1200 s where GameTime wraps. Made here: the portal's sprite isn't in this atlas.
float netherRiftSwirl(ivec2 px, float t) {
    vec2 p = (vec2(px & 63) + 0.5) * (6.2831853 / 64.0);
    float w = 6.2831853 * t / 8.0;
    vec2 q = p + 0.9 * sin(p.yx + vec2(w, -w)) + 0.35 * sin(vec2(2.0 * p.y - p.x - 2.0 * w, 2.0 * p.x + p.y + w));
    float v = 0.5 + 0.325 * sin(3.0 * q.x + 2.0 * q.y + w) + 0.175 * sin(2.0 * q.x - 3.0 * q.y - 2.0 * w);
    return clamp(v + 0.06 * (netherRiftHash(ivec3(px, 29)) - 0.5), 0.0, 1.0);
}

// Impacts. Every panel has one near its middle, which holds still while the panel pulses (it scales about its
// middle), so even the smallest rift is struck where it can be seen. Big rifts get more from the world: one
// candidate per cell of 5 by 5 blocks on the plane, every other row staggered half a cell, jittered within the
// middle of its cell; none crowding the panel's own. Panels of one rift share those; where two panels' own
// impacts disagree, a crack runs along the seam between them.
const int NETHER_RIFT_IMPACT_CELL = 80;

int netherRiftFloorDiv(int a, int b) {
    return a >= 0 ? a / b : -((-a - 1) / b) - 1;
}

// A world candidate's impact, in pixels from o (geometry is done near o, so floats keep their precision
// however far out the rift is).
vec2 netherRiftImpact(ivec2 cell, int plane, ivec2 o) {
    vec2 j = vec2(netherRiftHash(ivec3(cell, plane * 4 + 61)), netherRiftHash(ivec3(cell, plane * 4 + 62)));
    ivec2 corner = cell * NETHER_RIFT_IMPACT_CELL + ivec2((cell.y & 1) * (NETHER_RIFT_IMPACT_CELL / 2), 0) - o;
    return vec2(corner) + (0.2 + 0.6 * j) * float(NETHER_RIFT_IMPACT_CELL);
}

// What is known of the strike before looking at any pixel: which plane the panel lies in (its block along the
// normal, and which way it faces: rifts in other planes crack differently), the panel's middle o in world
// pixels (exact: panels are whole blocks, so it falls on a multiple of 8) and half its size, where this
// fragment is from o, and the panel's own impact (from o). Got from how the world and riftUv (0..1 over the
// panel's quad) move together across the screen; seen edge-on, the panel has no impact of its own.
struct NetherRiftStrike {
    int plane;
    ivec2 o;
    vec2 span;
    vec2 at;
    bool hasMine;
    vec2 mine;
    uint mineKey;
};

NetherRiftStrike netherRiftStrike(ivec2 px) {
    NetherRiftStrike k;
    vec3 n = cross(dFdx(riftWorld), dFdy(riftWorld));
    bool alongX = abs(n.z) >= abs(n.x);
    int depth = alongX ? int(floor(riftWorld.z)) + CameraBlockPos.z : int(floor(riftWorld.x)) + CameraBlockPos.x;
    k.plane = depth * 2 + (alongX ? 0 : 1);
    vec2 at = vec2(alongX ? riftWorld.x : riftWorld.z, riftWorld.y) * 16.0;
    ivec2 base = ivec2(alongX ? CameraBlockPos.x : CameraBlockPos.z, CameraBlockPos.y) * 16;
    mat2 dUv = mat2(dFdx(riftUv), dFdy(riftUv));
    k.hasMine = abs(determinant(dUv)) > 1e-12;
    k.o = px;
    k.span = vec2(0.0);
    k.at = vec2(0.5);
    k.mine = vec2(0.0);
    k.mineKey = 0u;
    if (k.hasMine) {
        mat2 m = mat2(dFdx(at), dFdy(at)) * inverse(dUv);
        vec2 mid = floor((at + m * (vec2(0.5) - riftUv)) / 8.0 + 0.5) * 8.0;
        k.o = ivec2(mid) + base;
        k.at = at - mid;
        k.span = 0.5 * (abs(m[0]) + abs(m[1]));
        k.mineKey = netherRiftHashU(ivec3(k.o >> 3, k.plane * 4 + 64));
        k.mine = (vec2(netherRiftUnit(k.mineKey, 0x3c6ef372u), netherRiftUnit(k.mineKey, 0xa54ff53au)) - 0.5)
                * vec2(12.0, 20.0);
    }
    return k;
}

// A distance measured across a line of this normal, counted along the nearer grid axis instead, so a crack
// drawn where it is under half a pixel comes out one pixel wide at any angle.
float netherRiftAxis(vec2 n) {
    return max(abs(n.x), abs(n.y));
}

// A crack's sideways wander at distance d along it: straight runs of 5 pixels with a kink between, up to
// about a pixel either way, none at the impact.
float netherRiftZig(uint key, float d) {
    float seg = floor(d / 5.0);
    int i = int(seg);
    float a = netherRiftUnit(key, uint(i) * 0x27d4eb2du), b = netherRiftUnit(key, uint(i + 1) * 0x27d4eb2du);
    return (mix(a, b, d / 5.0 - seg) - 0.5) * 2.0 * clamp(d / 8.0, 0.0, 1.0) * (0.8 + d * 0.006);
}

// Spoke s of n around an impact (s may run one past either end): its key and its angle, jittered within its
// share of the circle.
uint netherRiftSpokeKey(uint imp, int s, int n) {
    int w = s < 0 ? s + n : (s >= n ? s - n : s);
    return netherRiftMix(imp ^ (uint(w) + 1u) * 0x9e3779b9u);
}

float netherRiftSpokeAngle(uint key, int s, int n) {
    return (float(s) + 0.5 + 0.6 * (netherRiftUnit(key, 0x51ed270bu) - 0.5)) * 6.2831853 / float(n);
}

// How far out a spoke runs, in pixels: at stage 1 only some do, a little way; at 2 all, further; at 3 for ever.
float netherRiftReach(uint key, int st) {
    float h = netherRiftUnit(key, 0xc2b2ae35u), h2 = netherRiftUnit(key, 0x165667b1u);
    if (st <= 1) return h < 0.5 ? 6.0 + 20.0 * h2 : 0.0;
    if (st == 2) return 20.0 + 50.0 * h2;
    return 1e4;
}

// The glass at pixel q (at: where its middle is from the panel's middle). edge: how far (in pixels, see
// netherRiftAxis) from the nearest crack round its shard; normal: from that crack towards q; shard: a hash of
// the shard; whole: cracks close it all round (only those catch the light their own way or fall out); fallen:
// it fell out; pin: how far inside the hole the impact punched (> 0 inside).
void netherRiftShard(NetherRiftStrike k, ivec2 q, vec2 at, int st, out float edge, out vec2 normal, out uint shard,
        out bool whole, out bool fallen, out float pin) {
    vec2 p = vec2(q - k.o) + 0.5;
    // the nearest impact (i1, its key imp; own if it is the panel's) and the next (i2), among the world's in
    // the 3 by 3 cells around q (the jitter keeps the nearest among them) and the panel's own
    vec2 i1 = p + vec2(1e4), i2 = p + vec2(2e4);
    uint imp = 0u;
    bool own = false;
    float b1 = 1e12, b2 = 1e12;
    int cy = netherRiftFloorDiv(q.y, NETHER_RIFT_IMPACT_CELL);
    for (int j = -1; j <= 1; j++) {
        int y = cy + j;
        int cx = netherRiftFloorDiv(q.x - (y & 1) * (NETHER_RIFT_IMPACT_CELL / 2), NETHER_RIFT_IMPACT_CELL);
        for (int i = -1; i <= 1; i++) {
            ivec2 c = ivec2(cx + i, y);
            vec2 spot = netherRiftImpact(c, k.plane, k.o);
            if (k.hasMine && distance(spot, k.mine) < 40.0) continue;
            float dd = dot(p - spot, p - spot);
            if (dd < b1) {
                b2 = b1;
                i2 = i1;
                b1 = dd;
                i1 = spot;
                imp = netherRiftHashU(ivec3(c, k.plane * 4 + 63));
            } else if (dd < b2) {
                b2 = dd;
                i2 = spot;
            }
        }
    }
    float dm = dot(p - k.mine, p - k.mine);
    if (k.hasMine && dm < b1) {
        i2 = i1;
        i1 = k.mine;
        imp = k.mineKey;
        own = true;
    } else if (k.hasMine && dm < b2) {
        i2 = k.mine;
    }
    vec2 r = p - i1;
    float d = max(length(r), 0.01);
    int n = 8 + int(imp % 5u);
    float a = atan(r.y, r.x + 1e-6);
    if (a < 0.0) a += 6.2831853;

    // the sector: between the spoke at or below this angle and the next, the spoke's wander counted
    int below = min(int(a / (6.2831853 / float(n))), n - 1);
    uint keyK = netherRiftSpokeKey(imp, below, n);
    float angK = netherRiftSpokeAngle(keyK, below, n);
    vec2 uK = vec2(cos(angK), sin(angK));
    int s = dot(r, vec2(-uK.y, uK.x)) - netherRiftZig(keyK, dot(r, uK)) < 0.0 ? below - 1 : below;
    uint keyA = netherRiftSpokeKey(imp, s, n), keyB = netherRiftSpokeKey(imp, s + 1, n);
    float angA = netherRiftSpokeAngle(keyA, s, n), angB = netherRiftSpokeAngle(keyB, s + 1, n);
    float reachA = netherRiftReach(keyA, st), reachB = netherRiftReach(keyB, st);
    vec2 uA = vec2(cos(angA), sin(angA)), nA = vec2(-uA.y, uA.x);
    vec2 uB = vec2(cos(angB), sin(angB)), nB = vec2(-uB.y, uB.x);
    float alongA = dot(r, uA), alongB = dot(r, uB);
    // signed across each spoke, > 0 on the sector's side
    float sideA = (dot(r, nA) - netherRiftZig(keyA, alongA)) / netherRiftAxis(nA);
    float sideB = (netherRiftZig(keyB, alongB) - dot(r, nB)) / netherRiftAxis(nB);

    edge = 1e4;
    normal = vec2(0.0, 1.0);
    if (alongA < reachA && abs(sideA) < edge) {
        edge = abs(sideA);
        normal = sideA >= 0.0 ? nA : -nA;
    }
    if (alongB < reachB && abs(sideB) < edge) {
        edge = abs(sideB);
        normal = sideB >= 0.0 ? -nB : nB;
    }

    // the rings across this sector, spoke to spoke, further apart going out (they need both spokes to reach)
    uint sector = netherRiftMix(keyA ^ 0x632be5abu);
    int jMax = st <= 1 ? -1 : (st == 2 ? 2 : 9);
    int passed = 0;
    float ring = 0.0, beyond = 1e4;
    bool outward = false;
    for (int j = 0; j <= jMax; j++) {
        float base = 5.0 + 5.5 * float(j) + 1.1 * float(j * j);
        float ra = base * (1.0 + 0.16 * (netherRiftUnit(keyA, uint(j) * 0x2545f491u) - 0.5));
        float rb = base * (1.0 + 0.16 * (netherRiftUnit(keyB, uint(j) * 0x2545f491u) - 0.5));
        if (ra > reachA - 1.0 || rb > reachB - 1.0) break;
        if (netherRiftUnit(sector, uint(j) * 0x9e3779b9u) > 0.6) continue;
        vec2 ca = uA * ra + nA * netherRiftZig(keyA, ra), cb = uB * rb + nB * netherRiftZig(keyB, rb);
        float len = length(cb - ca);
        vec2 dir = (cb - ca) / len, m = vec2(dir.y, -dir.x);
        float t = dot(r - ca, dir);
        float seg = floor(t / 4.0);
        uint key = sector ^ uint(j) * 0x85ebca6bu;
        float wander = mix(netherRiftUnit(key, uint(int(seg)) * 0x27d4eb2du),
                netherRiftUnit(key, uint(int(seg) + 1) * 0x27d4eb2du), t / 4.0 - seg) - 0.5;
        float side = (dot(r - ca, m) - wander * 1.4 * clamp(min(t, len - t) / 2.0, 0.0, 1.0)) / netherRiftAxis(m);
        if (side > 0.0) {
            passed = j + 1;
            ring = base;
            if (side < edge) {
                edge = side;
                normal = m;
            }
        } else if (!outward) {
            outward = true;
            beyond = base;
            if (-side < edge) {
                edge = -side;
                normal = -m;
            }
        }
    }

    // where this impact's web meets the next one's, the nearer impact wins along a crack between them
    vec2 nb = normalize(i2 - i1);
    float border = (dot(p - i2, p - i2) - d * d) / (2.0 * length(i2 - i1)) / netherRiftAxis(nb);
    if ((st >= 3 || (st == 2 && d < 36.0)) && border < edge) {
        edge = border;
        normal = -nb;
    }

    // along the panel's sides, where its own impact owns the glass: on a seam the next panel's web may not
    // match (on the outline the edge strip covers it)
    vec2 inside = k.span - abs(at);
    if (own && (st >= 3 || (st == 2 && d < 36.0)) && min(inside.x, inside.y) < 1.5) {
        float seam = abs(min(inside.x, inside.y) - 0.5);
        if (seam < edge) {
            edge = seam;
            normal = inside.x < inside.y ? vec2(at.x < 0.0 ? 1.0 : -1.0, 0.0) : vec2(0.0, at.y < 0.0 ? 1.0 : -1.0);
        }
    }

    whole = st >= 3 || outward;
    shard = netherRiftMix(sector ^ uint(passed + 1) * 0x85ebca6bu);
    // At stage 3 each sector has broken out from the impact to a way of its own (less in a small panel): the
    // shards whose outer ring lies within it are out, so the hole is one jagged star and what glass is left
    // still hangs on the glass round it. At 2 some of the innermost shards are out.
    float h = netherRiftUnit(sector, 0x1b873593u);
    float broken = min(8.0 + 34.0 * h, k.hasMine ? (0.45 + 0.7 * h) * min(k.span.x, k.span.y) : 1e4);
    fallen = whole && (st >= 3 ? beyond < broken + 8.0
            : (st == 2 && ring == 0.0 && netherRiftUnit(shard, 0x1b873593u) < 0.5));
    pin = (st <= 1 ? 1.6 : (st == 2 ? 2.6 : 0.0)) - d;
}

// The panorama behind the rift, by view direction, nearest texel. The sprite holds six cube faces 3 by 2,
// laid out by tools/gen_panorama.py (nether-rifts): row 0 faces 0 1 2, row 1 faces 3 4 5; face 0 looks south
// (+Z, image right -X, up +Y), 1 west (-X, right -Z), 2 north (-Z, right +X), 3 east (+X, right +Z), 4 up
// (+Y, right -X, up -Z), 5 down (-Y, right -X, up +Z). shift moves the texel column (each hole a texel off the
// next, as if the shards refract).
vec3 netherRiftPanorama(vec3 d, int shift) {
    vec2 lo, hi;
    if (!riftSpriteBox(lo, hi)) return texture(Sampler0, texCoord0).rgb;
    vec2 atlas = vec2(textureSize(Sampler0, 0));
    ivec2 boxMin = ivec2(floor(lo * atlas + 0.5));
    int face = 0;
    vec2 s;
    vec3 a = abs(d);
    if (a.y >= a.x && a.y >= a.z) {
        face = d.y > 0.0 ? 4 : 5;
        s = d.y > 0.0 ? vec2(-d.x, -d.z) / a.y : vec2(-d.x, d.z) / a.y;
    } else if (a.x >= a.z) {
        face = d.x < 0.0 ? 1 : 3;
        s = vec2(d.z, d.y) / a.x * vec2(d.x < 0.0 ? -1.0 : 1.0, 1.0);
    } else {
        face = d.z > 0.0 ? 0 : 2;
        s = vec2(d.z > 0.0 ? -d.x : d.x, d.y) / a.z;
    }
    int size = int(floor((hi.x - lo.x) * atlas.x + 0.5)) / 3;
    ivec2 texel = ivec2(floor(vec2(s.x + 1.0, 1.0 - s.y) * 0.5 * float(size))) + ivec2(shift, 0);
    texel = clamp(texel, ivec2(0), ivec2(size - 1));
    return texelFetch(Sampler0, boxMin + ivec2(face % 3, face / 3) * size + texel, 0).rgb;
}

// Whether this fragment's face is beyond the rift's middle plane from the camera, and so seen through the
// portal: the faces sit about 0.135 of a block either side of it, and riftView runs from the camera.
bool netherRiftBeyond() {
    vec3 n = cross(dFdx(riftWorld), dFdy(riftWorld));
    bool alongX = abs(n.z) >= abs(n.x);
    float off = fract(alongX ? riftWorld.z : riftWorld.x) - 0.5;
    float eye = off - (alongX ? riftView.z : riftView.x);
    return off * eye < 0.0;
}

vec4 netherRiftBody(float stage) {
    float t = GameTime * 1200.0;
    int st = int(stage + 0.5);
    float red = st >= 3 ? 0.65 : (st == 2 ? 0.2 : 0.0);
    bool beyond = netherRiftBeyond();
    ivec2 px = netherRiftPixel();

    NetherRiftStrike k = netherRiftStrike(px);
    float edge, pin;
    vec2 normal;
    uint shard;
    bool whole, fallen;
    netherRiftShard(k, px, k.at, st, edge, normal, shard, whole, fallen, pin);

    if (beyond) {
        // The glass's body, seen through the portal (which lets about a quarter through): none at stage 1, a
        // faint red at 2, red-orange at 3; each whole shard a shade lighter or darker than the next.
        if (st <= 1 || fallen || pin > 0.0) return vec4(0.0);
        vec3 tint = st >= 3 ? vec3(1.0, 0.34, 0.08) : vec3(0.9, 0.08, 0.14);
        float alpha = st >= 3 ? 1.0 : 0.6;
        if (whole) {
            tint *= 0.6 + 0.7 * netherRiftUnit(shard, 0x68e31da4u);
        }
        return vec4(tint, alpha);
    }

    // A fallen shard leaves a jagged lip of glass, one to three pixels, with a thin glowing rim, along each crack
    // it shared with glass still there; where the shard across fell too, nothing is left between.
    float hole = pin;
    if (fallen) {
        float lip = 1.0 + floor(1.6 * netherRiftNoise(px, 1, vec2(0.0), 71) + 0.9 * netherRiftHash(ivec3(px, 73)));
        hole = 9.0;
        if (edge < lip) {
            ivec2 q = px + ivec2(floor(-normal / netherRiftAxis(normal) * (edge + 1.0) + 0.5));
            float e2, pin2;
            vec2 n2;
            uint shard2;
            bool whole2, fallen2;
            netherRiftShard(k, q, k.at + vec2(q - px), st, e2, n2, shard2, whole2, fallen2, pin2);
            if (pin2 <= 0.0 && !fallen2) hole = edge - lip;
        }
    }

    if (hole > 0.0) {
        // the nether, far away, through the hole (each hole a texel off the next), seen through the portal's
        // haze (a fifth of it on average, thicker in its bright bands and thin between, so the fortress reads
        // even from afar; mixed here, as the hole must stay opaque); darker under its lip
        int slip = int(shard % 3u) - 1;
        vec3 view = netherRiftPanorama(normalize(riftView), slip);
        float swirl = netherRiftSwirl(px, t);
        vec3 haze = mix(vec3(59.0, 10.0, 107.0) / 255.0, vec3(196.0, 108.0, 255.0) / 255.0, swirl);
        view = mix(view, haze, 0.2 * (0.55 + 0.9 * swirl));
        return vec4(view * mix(0.7, 1.0, clamp(hole / 3.0, 0.0, 1.0)), 1.0);
    }
    if (hole > -1.0) {
        // the hole's rim, a pixel of pale violet glow at every stage, each pixel shimmering softly on its own
        // beat (every 2 s, which divides the 1200 s where GameTime wraps)
        float glow = 0.5 + 0.5 * sin(6.2831853 * (t / 2.0 + netherRiftHash(ivec3(px, 23))));
        return vec4(mix(vec3(217.0, 179.0, 255.0) / 255.0, vec3(240.0, 220.0, 255.0) / 255.0, glow), 1.0);
    }

    // The glass's surface: clear, so the portal shows, but for its cracks (a dark pixel line, its far wall
    // catching the light from the upper left: violet, rose from stage 2, a touch brighter at 3) and a glint on
    // some shards. Anything translucent here would hide the portal behind it (items draw before translucent
    // blocks and write depth), so it is opaque or nothing.
    vec3 pale = st >= 3 ? vec3(0.98, 0.44, 0.6) : (st == 2 ? vec3(0.93, 0.36, 0.5) : NETHER_RIFT_PURPLE[4]);
    if (edge < 0.5) {
        return vec4(mix(NETHER_RIFT_PURPLE[0], NETHER_RIFT_RED[0], red) * 0.6, 1.0);
    }
    bool lit = dot(normal, vec2(-0.6, 0.8)) < -0.25;
    if (edge < 1.5 && lit) {
        return vec4(pale, 1.0);
    }
    if (whole && netherRiftUnit(shard, 0x2c1b3c6du) < 0.3 && ((px.x - px.y + int(shard & 31u)) & 31) == 0) {
        return vec4(pale, 1.0);
    }
    return vec4(0.0);
}

// A seam of sparks along the outline, two pixels across the middle of the strip: a few at stage 1, most of
// it at 3, flickering. Opaque or nothing, as everything drawn over the portal must be.
vec4 netherRiftEdge(float stage) {
    if (netherRiftBeyond()) return vec4(0.0);
    float t = GameTime * 1200.0;
    int st = int(stage + 0.5);
    float red = st >= 3 ? 0.65 : (st == 2 ? 0.2 : 0.0);
    int across = clamp(int(floor(riftLocal().y * 4.0)), 0, 3);
    if (across == 0 || across == 3) return vec4(0.0);
    ivec2 px = netherRiftPixel();
    float spark = netherRiftHash(ivec3(px, int(floor(t * 10.0)) * 8 + 21));
    if (spark > 0.12 + 0.18 * (stage - 1.0)) return vec4(0.0);
    vec3 hot = mix(vec3(1.0, 0.47, 0.94), vec3(1.0, 0.35, 0.27), red);
    return vec4(spark < 0.04 ? vec3(1.0, 0.92, 1.0) : hot, 1.0);
}
