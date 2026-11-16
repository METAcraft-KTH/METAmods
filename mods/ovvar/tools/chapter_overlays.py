#!/usr/bin/env python3
"""Draws the chapter ovvar's skin overlays and icons, once, checked in as the source of truth.

Every KTH chapter's ovve is the same garment in the chapter's own colour. Edvin drew the white
rolled-down ovve (tools/chapter_template_down.png); the white top-up ovve is IT's purple top-up
overlay greyed to match it. Each chapter's overlay is the white one dyed to its colour, so the
template's most common cloth shade is the colour exactly, with a second colour on the stripes or
placket and cuffs for the three chapters that wear one. The B-frack is Media's frack without
Media's emblem on the back. Pure green in an overlay means "erase the skin here", as in all of
ovvar's overlays. The icons are icon.png's ovve with its three cloth shades in the chapter's colour.

Inputs: src/main/resources/art/ovvar/{it,it-nercabbad,mediafrack,icon,icon_mediafrack}.png and
tools/chapter_template_down.png.
Outputs, in src/main/resources/art/ovvar/: <id>.png (top up), <id>-nercabbad.png (rolled down),
bfrack.png, icon_<id>.png for every ovve chapter and icon_bfrack.png.

Run from anywhere: python3 mods/ovvar/tools/chapter_overlays.py
"""
import colorsys
import os
import shutil
from collections import Counter

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.join(HERE, "..", "src", "main", "resources", "art", "ovvar")
TEMPLATE_DOWN = os.path.join(HERE, "chapter_template_down.png")
GREEN = (0, 255, 0, 255)

# id, name, garment, colour, source
CHAPTERS = [
    ("arkitektur", "Arkitektur (A)", "ovve", 0xA43AA8, "NCS S 3055-R50B, saturated to pop"),
    ("bygg", "Bygg (IsB / IsF / TT)", "ovve", 0xA0522D, "red ochre, estimate"),
    ("bfrack", "B-frack (Flyg, Maskin, Bergs)", "bfrack", 0x302C2C, "Media frack, black, no logo"),
    ("cl", "Civing & Lärare (CL)", "ovve", 0x1A1A1A, "black, red stripes + cuffs"),
    ("doktorand", "Doktorand (Dr)", "ovve", 0x1A1A1A, "black, gold front placket + cuffs"),
    ("elektro", "Elektro (E)", "ovve", 0xF5F5F5, "white, estimate"),
    ("fysik", "Fysik (F)", "ovve", 0xFF642B, "NCS S 0565-Y70R / CMYK"),
    ("indek", "Industriell ekonomi (I)", "ovve", 0x4A2C1D, "porter brown, estimate"),
    ("kemi", "Kemi (K)", "ovve", 0xFFCB05, "Edvin, NCS S 0580-Y10R"),
    ("mit", "Medicinsk teknik (MiT)", "ovve", 0x8EC9EB, "light blue, estimate"),
    ("open", "Öppen ingång (OPEN)", "ovve", 0xC62C22, "NCS S 1580-Y80R"),
    ("samhall", "Samhällsbyggnad (S)", "ovve", 0x294630, "NCS S 7020-G10Y"),
    ("tbas", "Tekniskt basår (TBas)", "ovve", 0xA2EE8D, "#A2EE8D, black #2C2C2C stripes + cuffs"),
    ("energi", "Energi och miljö (W)", "ovve", 0x006B71, "NCS S 4050-B40G"),
]


def load(path):
    return Image.open(path).convert("RGBA")


def rgb(c):
    return (c >> 16) & 255, (c >> 8) & 255, c & 255


def dominant(im, skip=(GREEN,)):
    c = Counter(p for p in im.getdata() if p[3] > 0 and p not in skip)
    return c.most_common(1)[0][0]


def lum(p):
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]


def whiten_top(it, it_down, t_down):
    """The purple IT top-up overlay as white cloth: fit grey = a*V + b on the rolled-down pair
    (IT purple -> Edvin's white), apply to the purple pixels of the top-up overlay."""
    xs, ys = [], []
    for p, q in zip(it_down.getdata(), t_down.getdata()):
        if p[3] and p != GREEN and q != GREEN and q[3]:
            xs.append(max(p[:3]) / 255)
            ys.append(q[0])
    n = len(xs)
    mx, my = sum(xs) / n, sum(ys) / n
    a = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / max(1e-9, sum((x - mx) ** 2 for x in xs))
    b = my - a * mx
    purple_h = colorsys.rgb_to_hsv(*[v / 255 for v in dominant(it)[:3]])[0]
    out = it.copy()
    px = out.load()
    for y in range(it.height):
        for x in range(it.width):
            p = px[x, y]
            if not p[3] or p == GREEN:
                continue
            h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in p[:3]])
            if s > 0.25 and min(abs(h - purple_h), 1 - abs(h - purple_h)) < 0.08:
                g = max(0, min(255, round(a * v + b)))
                px[x, y] = (g, g, g, p[3])
    return out


def dye(template, colour):
    """Multiply the cloth by the colour, scaled so the template's most common cloth shade is the
    colour exactly. Green (erase) pixels and dark details (belt, buttons) are kept."""
    target = rgb(colour)
    ref = lum(dominant(template))
    out = template.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if not p[3] or p == GREEN:
                continue
            h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in p[:3]])
            if s > 0.2 or v < 0.35:  # not white cloth: keep as drawn
                continue
            f = lum(p) / ref
            px[x, y] = tuple(max(0, min(255, round(t * f))) for t in target) + (p[3],)
    return out


def strip_logo(frack):
    """Media's frack in its own black, without Media's emblem on the back."""
    out = frack.copy()
    px = out.load()
    def emblem(p):
        h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in p[:3]])
        return p[3] and p != GREEN and (s > 0.35 or lum(p) > 100)
    holes = [(x, y) for y in range(20, 32) for x in range(32, 40) if emblem(frack.getpixel((x, y)))]
    for x, y in sorted(holes, key=lambda q: -min(abs(q[0] - 35.5), abs(q[1] - 25.5))):
        for r in range(1, 6):
            near = [px[i, j] for j in range(y - r, y + r + 1) for i in range(x - r, x + r + 1)
                    if 32 <= i < 40 and 20 <= j < 32 and (i, j) not in holes and px[i, j][3] and px[i, j] != GREEN]
            if near:
                px[x, y] = Counter(near).most_common(1)[0][0]
                break
    return out


# Second colours. Stripes: one texel column down the outer face of each leg and arm (right leg x1,
# left leg x26, right arm x41, left arm x42). Placket: the body front's centre column (x23).
# Cuffs: the lowest cloth texel of every column of the leg and arm faces.
TRIMS = {
    "cl": (0xC8102E, ("stripes", "cuffs")),
    "doktorand": (0xC9A227, ("placket", "cuffs")),
    "tbas": (0x2C2C2C, ("stripes", "cuffs")),
}
LIMB_FACES = [(0, 20, 16, 12), (16, 52, 16, 12), (40, 20, 16, 12), (32, 52, 16, 12)]  # legs, arms (all 4 faces)


def trim(dyed, template, colour, parts):
    target = rgb(colour)
    out = dyed.copy()
    px, tp = out.load(), template.load()

    def cloth(x, y):
        p = tp[x, y]
        if not p[3] or p == GREEN:
            return False
        h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in p[:3]])
        return s <= 0.2 and v >= 0.35

    def paint(x, y):
        if cloth(x, y):
            f = lum(tp[x, y]) / lum(dominant(template))
            px[x, y] = tuple(max(0, min(255, round(t * f))) for t in target) + (255,)

    if "stripes" in parts:
        for x, y0 in ((1, 20), (26, 52), (41, 20), (42, 52)):
            for y in range(y0, y0 + 12):
                paint(x, y)
    if "placket" in parts:
        for y in range(20, 32):
            paint(23, y)
    if "cuffs" in parts:
        for fx, fy, w, h in LIMB_FACES:
            for x in range(fx, fx + w):
                low = [y for y in range(fy, fy + h) if cloth(x, y)]
                if low:
                    paint(x, max(low))
    return out


# The icons: icon.png's ovve with its cloth in the chapter's colour.
ICON_SHADES = {(166, 67, 180): 0, (200, 101, 214): 1, (233, 135, 247): 2}   # icon.png's cloth: dark, mid, light
CUFFS = [(1, 10), (2, 10), (3, 10), (1, 11), (2, 11), (3, 11), (12, 11), (13, 11), (14, 11), (12, 12), (13, 12), (14, 12),
         (7, 13), (8, 13), (9, 13), (10, 13), (8, 14), (9, 14), (10, 14)]  # the three ends' dark pixels


def shades(colour):
    """The icon's dark, mid and light cloth for a chapter colour."""
    c = rgb(colour)
    if lum(c) < 48:  # black cloth must stay off the black outline
        return rgb(0x3A3A3A), rgb(0x4A4A4A), rgb(0x5A5A5A)
    if lum(c) > 200:  # white must keep its shading
        steps = (-67, -34, 0)
    else:
        steps = (0, 34, 67)
    return tuple(tuple(max(0, min(255, v + d)) for v in c) for d in steps)


def icon(base, colour, cuff=None):
    out = base.copy()
    px = out.load()
    tones = shades(colour)
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] and p[:3] in ICON_SHADES:
                px[x, y] = tones[ICON_SHADES[p[:3]]] + (p[3],)
    if cuff is not None:
        for x, y in CUFFS:
            px[x, y] = rgb(cuff) + (255,)
    return out


def main():
    art = lambda name: os.path.join(ART, name)
    t_down = load(TEMPLATE_DOWN)
    it, it_down, frack = load(art("it.png")), load(art("it-nercabbad.png")), load(art("mediafrack.png"))
    base_icon = load(art("icon.png"))
    t_up = whiten_top(it, it_down, t_down)
    for cid, name, garment, colour, source in CHAPTERS:
        if garment == "bfrack":
            strip_logo(frack).save(art(f"{cid}.png"))
            shutil.copyfile(art("icon_mediafrack.png"), art(f"icon_{cid}.png"))
            continue
        up, down = dye(t_up, colour), dye(t_down, colour)
        cuff = None
        if cid in TRIMS:
            tc, parts = TRIMS[cid]
            up, down = trim(up, t_up, tc, parts), trim(down, t_down, tc, parts)
            cuff = tc
        up.save(art(f"{cid}.png"))
        down.save(art(f"{cid}-nercabbad.png"))
        icon(base_icon, colour, cuff).save(art(f"icon_{cid}.png"))


if __name__ == "__main__":
    main()
