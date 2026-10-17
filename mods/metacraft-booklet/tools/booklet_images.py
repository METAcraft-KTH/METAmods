#!/usr/bin/env python3
"""Turns BookletRenders' output into the pictures that sit beside a page's text.

    METACRAFT_BOOKLET_RENDER=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
    python3 mods/metacraft-booklet/tools/booklet_images.py

A picture beside text is drawn as a font: the picture is cut into tiles of 16×9 UI px (the height of a
line of text), each tile a glyph, four image pixels to a UI pixel — the detail a 1080p screen at GUI
scale 4 shows. This writes, into src/main/resources/assets/metacraft/:
  textures/font/beside/<name>.png   the picture, padded to whole tiles
  font/beside.json                  one bitmap provider per picture, plus the -1 space between tiles
  beside/index.json                 per picture: its width in UI px and the text of each row
Beside.java reads the index and lays each row next to a line of the page's text.

A render is cut to what was drawn and scaled to fit SIZE (UI px); a screenshot cut-out (the stash
menu, taken at GUI scale 1) is enlarged 4× pixel for pixel.
"""
import json
import shutil
from pathlib import Path
from PIL import Image

MODULE = Path(__file__).resolve().parent.parent
SRC = MODULE / "build/booklet-renders"
ASSETS = MODULE / "src/main/resources/assets/metacraft"
SIZE = {"ovvar/chapters": (150, 110), "ovvar/stand": (150, 120), "decorating/canvas": (130, 110),
        "decorating/corner": (150, 110), "decorating/rope": (150, 110), "decorating/lantern": (130, 100),
        "decorating/lead": (150, 80), "decorating/trowel": (110, 100),
        "qol/rift": (140, 120), "qol/concrete": (120, 100), "qol/muffler": (150, 100),
        "brewing/barrel": (140, 110), "brewing/barrel_frame": (130, 110), "brewing/distilling": (110, 110), "brewing/drinks": (150, 80),
        "brewing/chapter_drinks": (150, 80)}
DEFAULT_SIZE = (100, 120)
AS_IS = {"ovvar/stash"}   # and every recipe/*
SCALE, TILE_W, TILE_H = 4, 16, 9          # UI px per tile
TW, TH = TILE_W * SCALE, TILE_H * SCALE   # image px per tile
SPACER = ""                           # advance -1: glyphs advance their width + 1
FIRST = 0xE000

shutil.rmtree(ASSETS / "textures/font/beside", ignore_errors=True)
providers = [{"type": "space", "advances": {SPACER: -1}}]
index = {}
code = FIRST
for src in sorted(SRC.rglob("*.png")):
    name = src.relative_to(SRC).with_suffix("").as_posix()
    if name.startswith("probe/"):
        continue
    im = Image.open(src).convert("RGBA")
    if name in AS_IS or name.startswith("recipe/"):
        im = im.resize((im.width * SCALE, im.height * SCALE), Image.NEAREST)
    else:
        box = im.getbbox()
        if box is None:
            raise SystemExit(f"{name}: the render is empty")
        im = im.crop(box)
        bw, bh = SIZE.get(name, DEFAULT_SIZE)
        s = min(bw * SCALE / im.width, bh * SCALE / im.height)
        im = im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)
    cols, rows = -(-im.width // TW), -(-im.height // TH)
    sheet = Image.new("RGBA", (cols * TW, rows * TH))
    sheet.alpha_composite(im, ((cols * TW - im.width) // 2, (rows * TH - im.height) // 2))
    # Minecraft sizes a bitmap glyph by its rightmost drawn column; a fully transparent pixel is not
    # drawn, so a tile with clear edges would advance less than its 16 px and the rows would come out
    # ragged. Alpha 1 is invisible but counts as drawn (Booklet does the same for its images).
    px = sheet.load()
    for y in range(sheet.height):
        for x in range(sheet.width):
            if px[x, y][3] == 0:
                px[x, y] = (0, 0, 0, 1)
    tex = ASSETS / f"textures/font/beside/{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(tex, optimize=True)
    chars = []
    for _ in range(rows):
        chars.append("".join(chr(code + i) for i in range(cols)))
        code += cols
    providers.append({"type": "bitmap", "file": f"metacraft:font/beside/{name}.png", "height": TILE_H, "ascent": 7, "chars": chars})
    # The spacer goes before each tile, not after: a text widget breaks a line the moment its running
    # width passes the limit, and a tile measured before its -1 would overshoot by one.
    index[name] = {"width": cols * TILE_W, "rows": ["".join(SPACER + c for c in row) for row in chars]}
    print(f"{name}: {cols}x{rows} tiles, {cols * TILE_W}x{rows * TILE_H} UI px")

(ASSETS / "font").mkdir(parents=True, exist_ok=True)
(ASSETS / "font/beside.json").write_text(json.dumps({"providers": providers}, ensure_ascii=False, indent=1), encoding="utf-8")
(ASSETS / "beside").mkdir(parents=True, exist_ok=True)
(ASSETS / "beside/index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1), encoding="utf-8")
# The old way (Booklet's own image pipeline, a wide transparent image) is gone.
shutil.rmtree(ASSETS / "textures/booklet/image/beside", ignore_errors=True)
