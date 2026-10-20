// Pirkko: a player head whose skin has a magenta marker texel at (0,0) is drawn
// as a ray-traced figure inside the head cube instead of as a textured cube.
// Texel (1,0) carries the body colour; texel (3,0) = white selects the rainbow colour and
// texel (4,0) = white marks a wall-mounted figure (the server picks that skin for wall heads),
// which stands with its feet on the wall and the head's front pointing away from it.
// Everything is solved in view space, where the camera sits at the origin.
//
// Included by metacraft-shaders' entity.fsh, which calls pirkko_shade first thing in main().
// Requires before inclusion: Sampler0, texCoord0, entityPos and entityNormal (the vertex position
// and normal, in the space ModelViewMat takes to view space), GameTime (minecraft:globals.glsl),
// ModelViewMat (minecraft:dynamictransforms.glsl) and, unless NO_CARDINAL_LIGHTING without
// PER_FACE_LIGHTING, minecraft:light.glsl.
const int PIRKKO_BOXES = 6;
// The figure is modelled 6.5 px tall; scale it so it spans the full height of the hat-layer cube
// (8.25 px above the block floor). The hat layer is drawn 8.5 px wide, so it hosts whatever pokes
// past the 8 px head cube, and both layers trace the same figure.
const float PIRKKO_SCALE = 8.25 / 6.5;
const vec3 PIRKKO_MIN[PIRKKO_BOXES] = vec3[](
    vec3(-1.0, 3.5, -1.0), vec3(-1.5, 2.5, -1.5), vec3(-2.0, 1.0, -2.0),
    vec3(-1.5, 0.0, -1.5), vec3(-0.5, 2.0, -3.0), vec3(-0.5, 2.0, 2.0));
const vec3 PIRKKO_MAX[PIRKKO_BOXES] = vec3[](
    vec3(1.0, 6.5, 1.0), vec3(1.5, 4.5, 1.5), vec3(2.0, 3.5, 2.0),
    vec3(1.5, 1.0, 1.5), vec3(0.5, 3.0, -2.0), vec3(0.5, 3.0, 3.0));

// A texture only counts as a Pirkko when it is 64x65 (Mojang skins are always 64x32 or
// 64x64, so a player skin can never match) and carries two key texels.
bool pirkkoIsMarker(out vec3 bodyColor, out bool wall) {
    if (textureSize(Sampler0, 0) != ivec2(64, 65)) return false;
    vec4 keyA = texelFetch(Sampler0, ivec2(0, 0), 0);
    vec4 keyB = texelFetch(Sampler0, ivec2(2, 0), 0);
    if (keyA != vec4(1.0, 0.0, 1.0, 1.0) || keyB != vec4(0.0, 1.0, 1.0, 1.0)) return false;
    bodyColor = texelFetch(Sampler0, ivec2(1, 0), 0).rgb;
    wall = texelFetch(Sampler0, ivec2(4, 0), 0) == vec4(1.0);
    if (texelFetch(Sampler0, ivec2(3, 0), 0) == vec4(1.0)) {
        // Rainbow Pirkko: GameTime is a day fraction (24000 ticks), so this loops every 2 seconds.
        float hue = fract(GameTime * 24000.0 / (20.0 * 2.0));
        bodyColor = clamp(abs(fract(hue + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    }
    return true;
}

// Returns false when the ray misses the figure.
bool pirkkoTrace(vec3 bodyColor, bool wall, out vec3 outColor, out vec3 normalS) {
    // View space from the shared inputs: the transform is affine, so this is the interpolated
    // view-space position and normal.
    vec3 pirkkoViewPos = (ModelViewMat * vec4(entityPos, 1.0)).xyz;
    vec3 pirkkoViewNormal = mat3(ModelViewMat) * entityNormal;
    vec2 texel = texCoord0 * 64.0; // the head model's UVs are always normalised to 64x64
    bool hat = texel.x >= 32.0;
    vec2 t = vec2(hat ? texel.x - 32.0 : texel.x, texel.y);

    // Which face of the 8x8x8 head box are we on, and where does it start in the skin?
    vec2 faceOrigin;
    int kind; // 0 = front/back (u along X), 1 = left/right (u along Z), 2 = top/bottom (v along Z)
    if (t.y < 8.0) {
        faceOrigin = vec2(t.x < 16.0 ? 8.0 : 16.0, 0.0);
        kind = 2;
    } else {
        faceOrigin = vec2(floor(t.x / 8.0) * 8.0, 8.0);
        kind = (t.x < 8.0 || (t.x >= 16.0 && t.x < 24.0)) ? 1 : 0;
    }
    vec2 faceLocal = t - faceOrigin - 4.0;

    // Per-texel view-space tangents from screen-space derivatives.
    vec3 dPdx = dFdx(pirkkoViewPos);
    vec3 dPdy = dFdy(pirkkoViewPos);
    vec2 dUVdx = dFdx(texel);
    vec2 dUVdy = dFdy(texel);
    float det = dUVdx.x * dUVdy.y - dUVdy.x * dUVdx.y;
    if (abs(det) < 1e-6) return false;
    vec3 T = (dPdx * dUVdy.y - dPdy * dUVdx.y) / det;
    vec3 B = (dPdy * dUVdx.x - dPdx * dUVdy.x) / det;
    vec3 N = normalize(pirkkoViewNormal);

    float texelSize = length(T) * (hat ? 8.0 / 8.5 : 1.0);
    float halfSize = (hat ? 4.25 : 4.0) * texelSize;
    vec3 cubeCenter = pirkkoViewPos - T * faceLocal.x - B * faceLocal.y - N * halfSize;

    // Canonical frame: world up, plus the horizontal axis that runs along the head's Z.
    vec3 worldUp = normalize(mat3(ModelViewMat) * vec3(0.0, 1.0, 0.0));
    vec3 up = worldUp;
    vec3 zAxis;
    if (wall) {
        // A wall head's front points away from the wall; the figure's feet are on the wall, so that
        // direction is the figure's up. Work out where the head's front lies on this face
        // (right|front|left|back side strip, or the top/bottom face), then keep the arms horizontal.
        vec3 outward;
        if (t.y >= 8.0) {
            outward = kind == 0 ? (t.x < 16.0 ? N : -N) : (t.x < 8.0 ? normalize(T) : -normalize(T));
        } else {
            outward = t.x < 16.0 ? normalize(B) : -normalize(B);
        }
        up = outward;
        zAxis = normalize(cross(worldUp, outward));
    } else {
        zAxis = kind == 0 ? N : (kind == 1 ? T : B);
        zAxis = normalize(zAxis - up * dot(zAxis, up));
    }
    vec3 xAxis = cross(up, zAxis);

    vec3 localOrigin = vec3(dot(-cubeCenter, xAxis), dot(-cubeCenter, up), dot(-cubeCenter, zAxis)) / texelSize + vec3(0.0, 4.0, 0.0);
    vec3 rayView = normalize(pirkkoViewPos);
    vec3 localDir = vec3(dot(rayView, xAxis), dot(rayView, up), dot(rayView, zAxis));

    float bestT = 1e20;
    vec3 bestNormal = vec3(0.0);
    vec3 invDir = 1.0 / localDir;
    for (int i = 0; i < PIRKKO_BOXES; i++) {
        vec3 t0 = (PIRKKO_MIN[i] * PIRKKO_SCALE - localOrigin) * invDir;
        vec3 t1 = (PIRKKO_MAX[i] * PIRKKO_SCALE - localOrigin) * invDir;
        vec3 tNear = min(t0, t1);
        vec3 tFar = max(t0, t1);
        float enter = max(max(tNear.x, tNear.y), tNear.z);
        float leave = min(min(tFar.x, tFar.y), tFar.z);
        if (enter <= leave && leave > 0.0 && enter > 0.0 && enter < bestT) {
            bestT = enter;
            vec3 sgn = -sign(localDir);
            bestNormal = enter == tNear.x ? vec3(sgn.x, 0.0, 0.0)
                       : (enter == tNear.y ? vec3(0.0, sgn.y, 0.0) : vec3(0.0, 0.0, sgn.z));
        }
    }
    if (bestT > 1e19) return false;

    vec3 normalView = xAxis * bestNormal.x + up * bestNormal.y + zAxis * bestNormal.z;
    normalS = transpose(mat3(ModelViewMat)) * normalView;
    outColor = bodyColor;
    return true;
}

// A Pirkko head: true, and lit is the figure's colour lit by its own normal, which the caller
// finishes the way vanilla finishes a colour; the fragments where the ray misses the figure are
// discarded. Anything else: false (and of a texture that is not 64x65, only its size was read).
bool pirkko_shade(out vec4 lit) {
    vec3 body;
    bool wall;
    if (!pirkkoIsMarker(body, wall)) return false;
    vec3 color;
    vec3 normalS;
    if (!pirkkoTrace(body, wall, color, normalS)) {
        discard;
    }
    #if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
    lit = minecraft_mix_light(Light0_Direction, Light1_Direction, normalS, vec4(color, 1.0));
    #else
    lit = vec4(color, 1.0);
    #endif
    return true;
}
