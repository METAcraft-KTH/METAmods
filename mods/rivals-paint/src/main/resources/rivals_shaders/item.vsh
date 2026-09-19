#version 330
#extension GL_ARB_separate_shader_objects : require

// RIVALS: globals, for ScreenSize — the data LED is pinned to a fixed quad in screen pixels, so the
// vertex stage needs the size of the screen. The Globals block is bound for every ITEM pipeline:
// ITEM_SNIPPET is built on MATRICES_FOG_LIGHT_DIR_SNIPPET → GLOBALS_SNIPPET.
#include <minecraft:globals.glsl>
#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
#ifdef GLINT_SPECIAL
layout(location = 5) in vec2 UV3;
#endif
layout(location = 6) in vec3 Normal;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif
layout(location = 2) out vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec4 overlayColor;
#endif

layout(location = 5) out vec2 texCoord0;
#ifdef GLINT
layout(location = 6) out vec2 texCoordGlint;
#endif
// RIVALS: the two things the paint gloss and the data LED need and vanilla's item pair does not carry.
// Declared for every variant (the alpha-only OIT phase discards paint too, so it runs the same border
// test). viewPos is view space, for the gloss's normal (from its derivatives) and view vector; rawColor
// is the vertex tint before any lighting, which is both the LED's exact colour for the post effect to
// read back and the paint's own colour without the directional term the chunks never get.
layout(location = 7) out vec3 viewPos;
layout(location = 8) out vec4 rawColor;

// RIVALS_LED_PIN: the data LED is not drawn where the model puts it. It is drawn on a fixed quad in
// screen pixels, under the hotbar, at the bottom centre of the screen.
//
// Why: the LED used to be a box in each weapon model solved so that the weapon's own
// firstperson_righthand transform landed it dead centre a hair above the bottom edge. But the hand is
// not fixed on screen — GameRenderer.bobView translates the hand pose by up to ~0.1 units per walk
// cycle, and the sprint FOV change moves it too, together about a tenth of the screen height. The LED
// dropped off the bottom edge every other step, the probe found nothing, and the overlay blinked in
// walking rhythm. Moving it up would put it on screen; widening the probe's search would be a bandage.
// Pinning it here is none of those: the quad is the same eight by eight pixels on every frame, at every
// resolution, at every GUI scale, whatever the hand is doing. The one thing it has to get right is the
// depth: 26.3 clips to [0, 1] and runs depth REVERSED, so the near plane is 1 and the far plane is 0 —
// verified in GameRenderer, which clears the depth texture to 0.0 before renderItemInHand and tests
// GREATER, and in Projection.setupPerspective, which asks JOML for zZeroToOne.
//
// The element in the model is now just somewhere to hang the sprite; its coordinates no longer matter.
//
// How an LED vertex is recognised is the other thing that had to be found out the hard way. The first
// version read the atlas here — a vertex texture fetch for the sprite's marker alpha, the same 246 the
// fragment stage keys on — and on the real client it never produced a quad at all. What works is the
// vertex TINT: the LED's colour is the signature the server wrote and the probe hunts for, red at full
// with green under 16, and nothing else in the frame is that. The corner comes from gl_VertexIndex,
// which must be spelled exactly that way: 26.3's renderpearl backend parses Vulkan-flavoured GLSL, and
// the plain GL spelling of it (the one ending in ID) does not compile there — a shader that fails to
// compile takes the whole pack down with it, and this one took a client with it on the way to finding
// that out. A game test asserts the spelling, and asserts the other one is nowhere in the file.

/** The quad, in screen pixels: eight across, eight tall, centred, one pixel up from the bottom edge. */
const float LED_QUAD = 8.0;
const float LED_LIFT = 1.0;
/** The signature the LED's tint carries, and the probe reads back: red at full, green under 16. */
const float LED_RED = 0.99;
const float LED_GREEN = 0.0627;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // An LED vertex is one whose tint carries the signature: red at full with green under 16, which is
    // the very value the server wrote and the probe reads back out of the frame. An idle LED fails it,
    // and so does every other player's weapon, which is handed the idle value — neither is pinned, and
    // the fragment stage discards them anyway.
    bool isLed = Color.r > LED_RED && Color.g < LED_GREEN;
    if (isLed) {
        // Which corner of the quad this vertex is. Items are drawn as quads, four consecutive vertices
        // per face, so the vertex's index in the draw says which corner it is without asking the atlas
        // anything. The x is mirrored on the faces pointing the other way, so that whichever winding the
        // pipeline culls, one face of the LED box survives to cover the quad.
        int corner = gl_VertexIndex & 3;
        vec2 ledAt = corner == 0 ? vec2(0.0, 0.0)
                : corner == 1 ? vec2(1.0, 0.0)
                : corner == 2 ? vec2(1.0, 1.0) : vec2(0.0, 1.0);
        if (Normal.x + Normal.y + Normal.z < 0.0) ledAt.x = 1.0 - ledAt.x;
        // Only in the world. The GUI draws hotbar icons and the inventory through this same pipeline
        // under an orthographic matrix, whose [2][3] is zero where a perspective one's is -1; there the
        // LED must vanish outright rather than be pinned over the hotbar, so it is sent to a z outside
        // the clip volume — 2.0 with w 1.0 is outside [0, 1] at either end of the convention — and
        // clipped away.
        if (ProjMat[2][3] == 0.0) {
            gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
        } else {
            // Position is ignored: the quad is built from ScreenSize alone. The corner's y is flipped
            // because a quad's corners run round it the way a texture's rows run down. All six faces of
            // the LED box land on this same quad, and they are all the same eight by eight pixels of the
            // same flat colour, so it does not matter which of them survives the culling.
            vec2 pixel = vec2(
                    ScreenSize.x * 0.5 + (ledAt.x - 0.5) * LED_QUAD,
                    LED_LIFT + (1.0 - ledAt.y) * LED_QUAD);
            // In front of everything, and unprojected: w is 1, so this is straight NDC.
            //
            // The depth is the part that has to be right, and the first attempt at it drew nothing at
            // all. 26.3's clip space is not OpenGL's classic [-1, 1]: Projection.setupPerspective builds
            // the matrix with JOML's setPerspective(..., zZeroToOne = true), so the volume is [0, 1] —
            // and the depth is REVERSED, which GameRenderer shows by calling clearDepthTexture(depth,
            // 0.0) before renderItemInHand and testing GREATER. Zero is the far plane and one is the
            // near one. A z just inside OpenGL's classic near plane — a whisker above minus one, which
            // is what this was — is simply outside this volume, and every LED vertex was clipped:
            // the probe found nothing, the ink pass handed the frame through, and the screen stayed
            // clean however much health was missing. 0.9999 is as near as makes no difference to the
            // near plane, which is what "in front of everything" means here.
            gl_Position = vec4(pixel / ScreenSize * 2.0 - 1.0, 0.9999, 1.0);
        }
    }

    #ifndef OIT_ALPHA_ONLY
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    #endif
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    rawColor = Color;
    #ifndef OIT_ALPHA_ONLY
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    #endif

    texCoord0 = UV0;
    #ifdef GLINT
    #ifdef GLINT_SPECIAL
    texCoordGlint = (TextureMat * vec4(UV3, 0.0, 1.0)).xy;
    #else
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    #endif
    #endif

    // RIVALS: view space, for the gloss's normal (from its derivatives) and view vector — the same two
    // uses terrain.vsh's viewPos has. There is deliberately no world position here: for an entity or a
    // display, Position is whatever the render PoseStack left in the buffer (the camera rotation is
    // already baked in), not a camera-relative world offset, so no arithmetic on it recovers one. The
    // paint's in-cell coordinate comes off the sprite instead — see item.fsh.
    viewPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
}
