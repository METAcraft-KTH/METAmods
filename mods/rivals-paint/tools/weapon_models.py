#!/usr/bin/env python3
"""Build the four weapon models from Julle's delivery, and solve the ink LED's place on each.

    python3 mods/rivals-paint/tools/weapon_models.py [--report]

Reads `tools/julle/*/resourcepack/assets/splat/models/item/*.json`, rewrites their texture ids to our
shared pair, keeps everything else — geometry, UVs, tint indices, `gui_light` and above all the
`display` transforms — verbatim, and appends one element: the ink meter's data LED.

The LED used to be the interesting half: a free-floating slip of a box placed so that the weapon's *own*
first-person transform landed it at the bottom centre of the screen, inside the hotbar. That is over. The
hand is not fixed on screen — the view bob moves the hand pose by up to a tenth of the screen height per
walk cycle, and the sprint FOV change moves it too — so the solved box left the frame every other step
and the ink on the screen blinked in walking rhythm. `rivals_shaders/item.vsh` (RIVALS_LED_PIN) now pins
the LED in screen space instead: it recognises an LED vertex by the sprite's marker alpha and emits a
fixed quad at the bottom centre, ignoring the model's coordinates entirely.

So the element's position no longer matters, and this script no longer has to be re-run when Julle moves
a display transform. The solve below is kept because it still puts the box somewhere sane inside the
item's own space — the camera arithmetic is left in because `--report` reads well and because it is the
only written record of 26.3's first-person chain we have — but nothing downstream depends on where it
lands. All a model owes the LED is one element whose six faces are all `#led` at tint index 1.

The chain below is 26.3-rc-1's, each step read off the client with `javap`:

  Camera.calculateHudFov              the hand pass is a 70 deg VERTICAL fov, whatever the fov slider says
  Projection.setupPerspective         -> Matrix4f.setPerspective(fovy, aspect, 0.05, far)
  FirstPersonHandsAndItemsRenderer
      .submitHandsWithItems           two view-bob rotations, both zero when the view is still
      .applyItemArmTransform          translate(+-0.56, -0.52 + equip*-0.6, -0.72); equip is 0 once settled
  ItemTransform.apply                 translate(t) ; rotate(rotationXYZ(r)) ; scale(s) ; translate(-0.5)
                                      with t = display.translation/16, and vertices at element/16

Checked against Julle's own in-game screenshot, tools/julle/splat_roller_models/previews/
minecraft_roller_firstperson.png: this arithmetic puts the roller's green grip heel at x 1162..1225,
y 907..980 of that 1600x1000 frame, which is where it is. `--report` prints the same table for the LED.

Stdlib only.
"""
import collections
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MODULE = os.path.dirname(HERE)
JULLE = os.path.join(HERE, "julle")
OUT = os.path.join(MODULE, "src/main/resources/assets/rivals-paint/models/item")

SOURCES = {
    "paint_gun": "splat_shooter_charger_slosher_models/resourcepack/assets/splat/models/item/shooter.json",
    "charger": "splat_shooter_charger_slosher_models/resourcepack/assets/splat/models/item/charger.json",
    "slosher": "splat_shooter_charger_slosher_models/resourcepack/assets/splat/models/item/slosher.json",
    "roller": "splat_roller_models/resourcepack/assets/splat/models/item/roller.json",
}

TEXTURES = {"splat:item/shooter_body": "rivals-paint:item/julle_body",
            "splat:item/shooter_ink": "rivals-paint:item/julle_ink"}

CREDIT = ("Original fan-made geometry by Julle (tools/julle/), Splatoon inspired; "
          "ink faces take tint index 0, the data LED tint index 1")

FOV_Y = 70.0                 # Camera.calculateHudFov
ARM = (0.56, -0.52, -0.72)   # applyItemArmTransform, right hand, equip progress 0

# Where the solve aims the box. Kept for the report and so the element lands somewhere sane; the vertex
# shader pins the LED to its own quad whatever comes out of here, and the headroom this used to leave
# under the hotbar for the view bob is no longer what saves it.
TARGET_Y_SHARE = 9.0 / 1080.0
# How far in front of the eye. Nearer and the box comes out too tall for the hotbar; further and the
# model coordinates run past the [-16, 32] a model element may use.
DEPTH = 1.5
# Half the box, in model pixels: square across, a thin slice deep. Depth is what the perspective divide
# magnifies, and this far below the middle of the screen a whole pixel of it smeared the LED half as
# tall again. The centre is snapped to a 64th so every corner is an exact double and the box is exactly
# one model pixel across, which is what the game test measures.
HALF = (0.5, 0.5, 0.125)
SNAP = 64.0


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def rotation(rx, ry, rz):
    """JOML's Quaternionf.rotationXYZ as a matrix: x, then y, then z."""
    ax, ay, az = math.radians(rx), math.radians(ry), math.radians(rz)
    cx, sx, cy, sy, cz, sz = (math.cos(ax), math.sin(ax), math.cos(ay),
                              math.sin(ay), math.cos(az), math.sin(az))
    return mul(mul([[1, 0, 0], [0, cx, -sx], [0, sx, cx]],
                   [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]),
               [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])


def inverse(m):
    a, b, c = m[0]
    d, e, f = m[1]
    g, h, i = m[2]
    det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    return [[(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det],
            [(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det],
            [(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det]]


def camera_point(model_px, display):
    """A model-pixel coordinate in camera space, for the right hand with the equip animation done."""
    t = [c / 16.0 for c in display["translation"]]
    s = display["scale"]
    v = apply(rotation(*display["rotation"]),
              [(model_px[i] / 16.0 - 0.5) * s[i] for i in range(3)])
    return [ARM[i] + t[i] + v[i] for i in range(3)]


def screen(v, width, height):
    """Pixels, x from the left and y from the BOTTOM. None behind the eye."""
    if v[2] >= -1.0e-6:
        return None
    tan = math.tan(math.radians(FOV_Y) / 2.0)
    return (((v[0] / (width / height * tan)) / -v[2] + 1.0) * 0.5 * width,
            ((v[1] / tan) / -v[2] + 1.0) * 0.5 * height)


def led_centre(display):
    """The model-pixel centre that lands on the target, snapped to a 64th."""
    tan = math.tan(math.radians(FOV_Y) / 2.0)
    ndc_y = 2.0 * TARGET_Y_SHARE - 1.0
    target = (0.0, ndc_y * tan * DEPTH, -DEPTH)
    t = [c / 16.0 for c in display["translation"]]
    s = display["scale"]
    u = apply(inverse(rotation(*display["rotation"])),
              [target[i] - ARM[i] - t[i] for i in range(3)])
    centre = [round(16.0 * (u[i] / s[i] + 0.5) * SNAP) / SNAP for i in range(3)]
    for i in range(3):
        # Parenthesised: without the brackets this is `(not lower) and upper`, which passes a box that
        # has run off the low end and fails one that has not.
        if not (-16.0 <= centre[i] - HALF[i] and centre[i] + HALF[i] <= 32.0):
            raise SystemExit("LED corner %r is outside the [-16, 32] a model element may use; "
                             "try a smaller DEPTH" % (centre,))
    return centre


def led_element(centre):
    faces = collections.OrderedDict()
    for side in ("north", "south", "east", "west", "up", "down"):
        faces[side] = collections.OrderedDict(
            [("uv", [0, 0, 16, 16]), ("texture", "#led"), ("tintindex", 1)])
    return collections.OrderedDict([
        ("name", "data_led"),
        ("from", [centre[i] - HALF[i] for i in range(3)]),
        ("to", [centre[i] + HALF[i] for i in range(3)]),
        ("faces", faces),
    ])


# The roller's second pose: what it looks like while the button is held and the head is on the floor.
# `items/roller.json` switches to `item/roller_rolling` on minecraft:using_item, which is true only while
# the client is using the item — which it now is (PaintWeapon.HELD_USE), and which is the whole reason
# this pose can exist at all.
#
# How it was chosen: the model's drum is its -z end and its grip is its +z end, and the base
# firstperson pose is rotation [0, -15, -5], translation [-1, -1, -5], scale 0.43. Rolling pitches it
# nose-down (a NEGATIVE x rotation lowers the -z end), pushes it further ahead and further down, and
# scales it up about a third, so the head reads as pressed against the floor rather than carried. The
# numbers were solved against the same first-person camera chain `--report` walks: at 1920x1080 they put
# the drum at y -34..173 px above the bottom edge — sitting on the edge, slightly clipped, which is what
# "on the floor" looks like from inside the head — with the grip above it at y 230..289 and the whole
# thing ahead of the player rather than off to the side.
#
# The third person had to be worked out rather than nudged. A display translation is in SIXTEENTHS of a
# block, so the first attempt — 0.6 down, 2.2 ahead — moved the roller half a pixel and was invisible.
#
# The frame it lands in, checked in game rather than derived: in the item's own third-person axes -z
# runs down the hanging arm, +y points AHEAD of the player and -y behind (a -45 pitch put the drum in
# the ground behind the player, which settled the sign). Julle's base pose is [90, 0, 0], which sends
# the drum — the model's -z end — to +y: carried out in front, level, which is why lowering it by a
# fraction of a block did nothing anyone could see. A +45 rotation puts that end at (0, +0.7, -0.7):
# ahead and down, which is where a roller being pushed along the floor is. The translation then carries
# it about 1.3 blocks ahead of the hand and down to the floor, and the scale goes to 1 because a drum on
# the ground is a drum, not a prop.
ROLLING_DISPLAY = collections.OrderedDict([
    ("thirdperson_righthand", {"rotation": [45, 0, 0], "translation": [0, 10, -3], "scale": [1.0, 1.0, 1.0]}),
    ("thirdperson_lefthand", {"rotation": [45, 0, 0], "translation": [0, 10, -3], "scale": [1.0, 1.0, 1.0]}),
    ("firstperson_righthand", {"rotation": [-45, -10, -5], "translation": [-5, -3, -9], "scale": [0.58, 0.58, 0.58]}),
    ("firstperson_lefthand", {"rotation": [-45, 10, 5], "translation": [-5, -3, -9], "scale": [0.58, 0.58, 0.58]}),
])
# Which weapons get a rolling variant, and what it is called.
ROLLING = {"roller": "roller_rolling"}


def rolling(name):
    """Write the held-down pose as a child model: same geometry, different display.

    A child model inherits its parent's elements and textures, and its own `display` entries override
    the parent's one perspective at a time — so this file is four transforms and nothing else, and the
    geometry, the tints and the data LED element stay in exactly one place. The LED needs no solving of
    its own any more either: item.vsh pins it to a fixed screen quad and throws its model position away.
    """
    model = collections.OrderedDict([
        ("credit", CREDIT + "; rolling pose (held right click), display only"),
        ("parent", "rivals-paint:item/" + name),
        ("display", ROLLING_DISPLAY),
    ])
    with open(os.path.join(OUT, ROLLING[name] + ".json"), "w") as handle:
        json.dump(model, handle, indent="\t")
        handle.write("\n")
    print("%-10s rolling pose -> %s.json" % (name, ROLLING[name]))


def convert(name, path, report):
    with open(path) as handle:
        source = json.load(handle, object_pairs_hook=collections.OrderedDict)
    textures = collections.OrderedDict(
        (key, TEXTURES.get(value, value)) for key, value in source["textures"].items())
    textures["led"] = "rivals-paint:item/data_led"
    display = source["display"]
    centre = led_centre(display["firstperson_righthand"])
    model = collections.OrderedDict([
        ("credit", CREDIT),
        ("texture_size", source["texture_size"]),
        ("textures", textures),
        ("gui_light", source["gui_light"]),
        ("display", display),
        ("elements", list(source["elements"]) + [led_element(centre)]),
    ])
    with open(os.path.join(OUT, name + ".json"), "w") as handle:
        json.dump(model, handle, indent="\t")
        handle.write("\n")
    if report:
        led = model["elements"][-1]
        for width, height in ((1920.0, 1080.0), (2560.0, 1440.0), (1280.0, 720.0)):
            box = [screen(camera_point([(led["from"] if corner & 1 << axis else led["to"])[axis]
                                        for axis in range(3)], display["firstperson_righthand"]),
                          width, height) for corner in range(8)]
            print("%-10s %dx%d  x %.1f..%.1f (centre %.0f)  %.1f..%.1f px above the bottom"
                  % (name, width, height, min(p[0] for p in box), max(p[0] for p in box), width / 2,
                     min(p[1] for p in box), max(p[1] for p in box)))
    print("%-10s %d elements, LED centre %r" % (name, len(model["elements"]), centre))
    if name in ROLLING:
        rolling(name)


report = "--report" in sys.argv
for name, relative in SOURCES.items():
    convert(name, os.path.join(JULLE, relative), report)
