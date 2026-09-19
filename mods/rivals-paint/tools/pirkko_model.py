#!/usr/bin/env python3
"""Import Julle's Pirkko — the IT chapter's mascot — from the delivery into our own pack.

    python3 mods/rivals-paint/tools/pirkko_model.py

Pirkko is not a weapon, so this is not {@code weapon_models.py}: there is no ink LED to solve and no
shared body/ink atlas to fold her into. She is one dyeable figure on one texture of her own, and all
this script does is copy the three files out of `tools/julle/pirkko_models/resourcepack/assets/splat/`
and rewrite every `splat:` id to ours. Geometry, per-face UVs, the tint indices that carry the team
dye, `texture_size`, `gui_light` and Julle's `display` transforms all come across verbatim — the same
rule the weapons are imported under, so a redelivery is a re-run rather than a merge.

What the squid display does with her is in `SquidDisplay`: it renders her in the `NONE` context, so
the `fixed` transform here (which stands her upright for an item frame) is deliberately not the one
the floor figure uses. Nothing downstream depends on any transform in the file; they are kept because
they are what she looks like in a hand, on the ground and in the GUI, and because throwing away an
artist's work on the way in is how a redelivery stops matching what ships.

Stdlib only.
"""
import collections
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
MODULE = os.path.dirname(HERE)
SOURCE = os.path.join(HERE, "julle/pirkko_models/resourcepack/assets/splat")
ASSETS = os.path.join(MODULE, "src/main/resources/assets/rivals-paint")

NAMESPACE = "rivals-paint"
NAME = "pirkko"
TEXTURE = "pirkko_ink"

CREDIT = ("Julle's Pirkko, the IT chapter's mascot: original cuboid adaptation of the reference at "
          "https://pirkkopower.com/, imported from tools/julle/pirkko_models/ by tools/pirkko_model.py. "
          "Every face takes tint index 0, which is where the team dye lands")


def ids(value):
    """A `splat:` id in our namespace. Anything already ours, or vanilla's, is left alone."""
    return value.replace("splat:", NAMESPACE + ":") if value.startswith("splat:") else value


def write(relative, model):
    path = os.path.join(ASSETS, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        json.dump(model, handle, indent="\t")
        handle.write("\n")
    print("%-24s -> %s" % (NAME, relative))


def item_definition():
    """The item definition: one model, one dye tint, our id in place of `splat:item/pirkko`."""
    with open(os.path.join(SOURCE, "items", NAME + ".json")) as handle:
        source = json.load(handle, object_pairs_hook=collections.OrderedDict)
    model = source["model"]
    model["model"] = ids(model["model"])
    write("items/" + NAME + ".json", source)


def geometry():
    """The model: textures re-namespaced, everything else — including the display transforms — kept."""
    with open(os.path.join(SOURCE, "models/item", NAME + ".json")) as handle:
        source = json.load(handle, object_pairs_hook=collections.OrderedDict)
    textures = collections.OrderedDict((key, ids(value)) for key, value in source["textures"].items())
    model = collections.OrderedDict([("credit", CREDIT)])
    for key, value in source.items():
        # Our own credit line replaces the delivery's, which it says the same things as and more; every
        # other key, in the delivery's own order, comes across untouched.
        if key == "credit":
            continue
        model[key] = textures if key == "textures" else value
    write("models/item/" + NAME + ".json", model)


def texture():
    """The grayscale sheet, byte for byte: the dye multiplies it, so it must stay exactly as painted."""
    relative = "textures/item/" + TEXTURE + ".png"
    destination = os.path.join(ASSETS, relative)
    os.makedirs(os.path.dirname(destination), exist_ok=True)
    shutil.copyfile(os.path.join(SOURCE, relative), destination)
    print("%-24s -> %s (%d bytes)" % (NAME, relative, os.path.getsize(destination)))


item_definition()
geometry()
texture()
