#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;
uniform sampler2D ProbeSampler;
uniform sampler2D Ink1Sampler;
uniform sampler2D Ink2Sampler;
uniform sampler2D Ink3Sampler;
uniform sampler2D Ink4Sampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Ink on the glass, drawn from four overlay textures an artist paints rather than from a field of
// signed-distance blobs. Two flat tones and a procedural shape had no depth and did not read as pixel
// art; a drawing does, and a drawing can be edited without touching a shader.
//
// The contract with the textures (see textures/effect/README.md): 320x180 RGBA, alpha is coverage and is
// either 0 or 255, RGB is a greyscale SHADING map. This pass maps that luminance to four tones of the
// team's colour with hard steps, so one drawing serves both teams, and samples at texel centres with no
// filtering at all, so every texel comes out as a fat block of screen pixels — the texture is the pixel
// grid, which is why the pass no longer snaps to a screen-pixel grid of its own.
//
// State 1 is a little ink at the edges and state 4 is nearly covered, and the amount runs through them
// continuously rather than in four steps: see the crossfade in main().

/** The overlays' own size. Sampling is snapped to this grid, so it is the pixel art's resolution. */
const vec2 SHEET = vec2(320.0, 180.0);
/** No ink within this of the middle of the screen, in screen heights: the reticle stays readable. */
const float CLEAR = 0.09;
/** The two team inks, #BD3754 and #8A57BD, indexed by the team byte in the data pixel. */
const vec3 DATA_INK = vec3(0.7412, 0.2157, 0.3294);
const vec3 IT_INK = vec3(0.5412, 0.3412, 0.7412);
/** The four tones, as steps on the overlay's luminance. */
const float TONE_SHADOW = 0.3;
const float TONE_BASE = 0.6;
const float TONE_LIGHT = 0.85;

/** The overlay for a state, sampled at the centre of the texel under {@code uv}. */
vec4 overlay(int state, vec2 uv) {
    // The overlays are drawn top row first, the way an image file and every paint program are: row 0 of
    // the PNG is the top of the screen. This pass's texCoord is the post chain's, and there y = 0 is the
    // BOTTOM of the frame — so sampling it straight showed every drawing upside down, drips running up.
    // Flip the v: an artist paints the overlays the right way up and gets them the right way up.
    vec2 flipped = vec2(uv.x, 1.0 - uv.y);
    // Snap to texel centres: NEAREST is asked for in the chain too (bilinear false), and this makes the
    // pass correct whatever the sampler is set to. The overlay is stretched across the whole screen, so
    // on a window that is not 16:9 the texels come out as rectangles rather than squares — which is the
    // right trade for ink that has to reach every edge.
    vec2 at = (floor(flipped * SHEET) + 0.5) / SHEET;
    if (state <= 1) return texture(Ink1Sampler, at);
    if (state == 2) return texture(Ink2Sampler, at);
    if (state == 3) return texture(Ink3Sampler, at);
    return texture(Ink4Sampler, at);
}

/**
 * The overlays are cumulative — every texel of state n is ink in state n+1 as well — so the texels the
 * next state ADDS are exactly the ones in n+1 and not in n, and those are the ones that fade.
 *
 * Nothing else fades: the tones stay quantised to the shader's four, the alpha edges stay hard, and the
 * layer already on the glass stays fully opaque. Only the arriving layer's opacity is continuous, the
 * way powder snow's frost fades in, so losing health grows the next round of blobs in over the quarter
 * instead of popping it onto the screen whole.
 */
void main() {
    vec3 frame = texture(InSampler, texCoord).rgb;
    vec4 probe = texture(ProbeSampler, vec2(0.5));
    float amount = probe.r;
    if (amount <= 0.0) {
        fragColor = vec4(frame, 1.0);
        return;
    }
    // The probe's green is the enemy team's index, and nothing else: the LED's position used to ride in
    // the same byte so this pass could paint over it, and there is nothing left to paint over — the LED
    // sits under the hotbar now, and the hotbar is drawn after this pass.
    vec3 ink = floor(probe.g * 255.0 + 0.5) < 0.5 ? DATA_INK : IT_INK;
    // The amount is a quarter of your health each: s = 0..4, n is the layer that is fully on the glass
    // and t is how far into the next one the player has been hurt. At n = 0 the whole of state 1 is
    // still fading in from a clean screen; at n = 4 state 4 is solid and there is nothing left to add.
    float s = amount * 4.0;
    int n = int(floor(s));
    float t = fract(s);

    vec4 settled = n >= 1 ? overlay(min(n, 4), texCoord) : vec4(0.0);
    vec4 arriving = n < 4 ? overlay(n + 1, texCoord) : vec4(0.0);
    vec4 sheet;
    float opacity;
    if (settled.a >= 0.5) {
        // Ink that landed a quarter of health ago: full strength, and its own drawing wins.
        sheet = settled;
        opacity = 1.0;
    } else if (arriving.a >= 0.5) {
        // What the next state adds on top of this one. smoothstep so the fade eases in and out rather
        // than ramping, which is what makes it read as paint arriving instead of a dissolve.
        sheet = arriving;
        opacity = smoothstep(0.0, 1.0, t);
    } else {
        fragColor = vec4(frame, 1.0);
        return;
    }
    // The middle of the screen is where the player is aiming: ink there is a blindfold, not a nuisance.
    // The overlays keep their own clear island, so this is a guard against one that does not.
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 p = (texCoord - 0.5) * vec2(aspect, 1.0);
    if (length(p) < CLEAR) {
        fragColor = vec4(frame, 1.0);
        return;
    }
    // Four tones of the team's colour, with hard steps. The overlay's grey says which: shadow, base,
    // light, highlight. Nothing is interpolated — that is the whole of the pixel-art look.
    float lum = dot(sheet.rgb, vec3(0.299, 0.587, 0.114));
    vec3 tone = lum < TONE_SHADOW ? ink * 0.55
            : lum < TONE_BASE ? ink
            : lum < TONE_LIGHT ? mix(ink, vec3(1.0), 0.25)
            : mix(ink, vec3(1.0), 0.6);
    // The only continuous thing in the pass: how much of the arriving layer is there yet.
    fragColor = vec4(mix(frame, tone, opacity), 1.0);
}
