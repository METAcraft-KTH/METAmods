#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// The ink meter's data pixel. There is no way for a server to speak to a post effect, so it writes the
// number into the frame instead — and it has to be in the frame before the effect runs. 26.3's
// GameRenderer.render does renderLevel(), then applyPostEffects(), and only then GuiRenderer.render(),
// so nothing on the HUD is on this target; the held item is, drawn inside renderLevel by
// renderItemInHand. So every paint weapon carries a one-model-pixel data LED, the item shader hands its
// custom_model_data tint to the frame unlit and exact, and this pass goes looking for it: red at full
// with green under 16, which nothing in a rendered world is for two samples in a row.
//
// Where it looks is no longer a search at all. The LED used to be a box in the model, solved so that the
// weapon's own firstperson_righthand transform landed it under the hotbar, and this pass swept 1344
// samples of the bottom of the frame hunting for it. That placement was not stable: the hand is bobbed
// by GameRenderer.bobView and moved by the sprint FOV change, about a tenth of the screen height
// between them, so the LED dropped off the bottom edge every other step and the ink blinked in walking
// rhythm. It is now pinned in screen space by item.vsh (RIVALS_LED_PIN), which ignores the model's
// position for LED vertices and emits a fixed 8x8-pixel quad at the bottom centre of the screen —
// under the hotbar, which the GUI draws after this pass has read the frame, so the player never sees
// it. So this pass reads three pixels of a quad it knows the address of.
//
// This pass renders to a 1x1 target, so everything below runs once per frame for the whole screen rather
// than once per pixel.

/** The middle of item.vsh's quad, in pixels from the bottom-left: eight tall, lifted one off the edge. */
const float LED_Y = 5.0;
/** The confirmation samples, a couple of pixels either side: still well inside the eight-pixel quad. */
const float CONFIRM = 2.0;

/** The signature: red at full, green below 16 (green carries the team index in its low nibble). */
bool marker(vec2 pixel, out vec3 data) {
    vec4 frame = texture(InSampler, pixel / InSize);
    data = frame.rgb;
    return frame.r > 0.99 && frame.g < 0.0627;
}

/** A second sample carrying the same amount: one red pixel of the world is not a flat run of them. */
bool confirms(vec2 at, float hop, vec3 here) {
    vec3 there;
    if (!marker(at + vec2(hop, 0.0), there)) return false;
    return abs(there.b - here.b) <= 0.002;
}

void main() {
    vec2 at = vec2(InSize.x * 0.5, LED_Y);
    vec3 here;
    // Both confirmations, not either: the quad is eight pixels wide and centred, so two pixels to each
    // side of its middle are inside it by construction. Anything that matches on one side only is not
    // the LED, and the signature is cheap enough to be worth spending on being sure.
    if (marker(at, here) && confirms(at, CONFIRM, here) && confirms(at, -CONFIRM, here)) {
        // amount, and the team index the ink pass picks its colour by. Where the LED is matters to
        // nobody: the ink pass has nothing to cover, because the hotbar covers it.
        fragColor = vec4(here.b, here.g, 0.0, 1.0);
        return;
    }
    // No weapon in view, or an idle LED (which the item shader discards outright): no ink, and the ink
    // pass hands the frame straight through.
    fragColor = vec4(0.0);
}
