// Shared by every rift the item shader draws (metacraft-shaders' item.fsh): noise, and where on its sprite a fragment is.
// Needs texCoord0 and riftUv declared before it is included.
float riftHash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float riftNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(riftHash(i), riftHash(i + vec2(1.0, 0.0)), u.x),
               mix(riftHash(i + vec2(0.0, 1.0)), riftHash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float riftFbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * riftNoise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

mat2 riftRot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

// The sprite's box in the atlas, lo and hi in texture coordinates. riftUv numbers the quad's corners
// from wherever its draw starts in a shared buffer, so it can come out turned by a quarter; texCoord0
// cannot. The derivatives of both give the map from one to the other, and from it the box, whichever
// corner came first. False when the quad is seen edge-on and the map can't be had.
bool riftSpriteBox(out vec2 lo, out vec2 hi) {
    mat2 dUv = mat2(dFdx(riftUv), dFdy(riftUv));
    mat2 dTex = mat2(dFdx(texCoord0), dFdy(texCoord0));
    lo = texCoord0;
    hi = texCoord0;
    if (abs(determinant(dUv)) < 1e-12) return false;
    mat2 m = dTex * inverse(dUv);
    vec2 o = texCoord0 - m * riftUv;
    vec2 c1 = o + m[0], c2 = o + m[1], c3 = o + m[0] + m[1];
    lo = min(min(o, c1), min(c2, c3));
    hi = max(max(o, c1), max(c2, c3));
    return true;
}

// Where on the sprite this fragment is, 0..1 along the sprite's own axes.
vec2 riftLocal() {
    vec2 lo, hi;
    if (!riftSpriteBox(lo, hi)) return riftUv;
    return (texCoord0 - lo) / max(hi - lo, vec2(1e-9));
}
