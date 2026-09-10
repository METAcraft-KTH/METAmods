#!/usr/bin/env python3
"""
Renders the stitching dialog from the *generated* font (src/main/generated) the way the client
would draw it: each button's label is walked codepoint by codepoint, spaces advance the cursor,
bitmap glyphs are blitted at textY + 7 - ascent, and the label starts at button.x + 2. The labels
themselves are composed here with the same rules as SewingGame/Seam (cloth cell per button, the
last cell draws the patch, marks and needle), so what you see is what the font data produces.

    ./gradlew :mods:ovvar:runDatagen && python3 render_asbuilt.py

Needs Pillow and the vanilla font extracted as for render_mockups.py.
"""
import json
import math
import os

from PIL import Image

from render_mockups import FONT, screen, SCALE, W, H, FOOTER_Y, vanilla_button

HERE = os.path.dirname(os.path.abspath(__file__))
GEN = os.path.normpath(os.path.join(HERE, '..', '..', '..', 'src', 'main', 'generated'))
ART = os.path.normpath(os.path.join(HERE, '..', '..', '..', 'src', 'main', 'resources', 'art', 'ovvar', 'patches'))

CELL, GAP, COLS, ROWS = 20, 2, 7, 7
PITCH = CELL + GAP
PICTURE_W, PICTURE_H = COLS * PITCH, ROWS * PITCH
BAND = COLS * CELL + (COLS - 1) * GAP
LABEL_INSET, OVERHANG = 2, 1
MARK, NEEDLE_LENGTH, NEEDLE_WIDTH = 7, 26, 9
OUT, IN = 4, 4


# ------------------------------------------------------------------ the font, as the client loads it

class SewingFontData:
    def __init__(self):
        with open(os.path.join(GEN, 'assets', 'ovvar', 'font', 'sewing.json')) as f:
            providers = json.load(f)['providers']
        self.glyphs = {}        # char -> (image, ascent, advance)
        self.spaces = {}        # char -> advance
        self.by_name = {}       # texture name -> [(top, char)]
        for p in providers:
            if p['type'] == 'space':
                self.spaces.update(p['advances'])
                continue
            path = p['file'].split(':', 1)[1]
            img = Image.open(os.path.join(GEN, 'assets', 'ovvar', 'textures', path)).convert('RGBA')
            ch = p['chars'][0]
            # the client's advance: rightmost column with any alpha, plus one
            adv = 0
            alpha = img.getchannel('A')
            for x in range(img.width - 1, -1, -1):
                if any(alpha.getpixel((x, y)) for y in range(img.height)):
                    adv = x + 1
                    break
            self.glyphs[ch] = (img, p['ascent'], adv + 1)
            name = os.path.splitext(os.path.basename(path))[0]
            self.by_name.setdefault(name, []).append((14 - p['ascent'], ch))

    def char(self, name, top=0):
        for t, ch in self.by_name[name]:
            if t == top:
                return ch
        raise KeyError(f'{name} has no variant at top {top}')

    def size(self, name):
        """The art's size: the texture is padded below with transparent rows for the ascent rule."""
        img = self.glyphs[self.by_name[name][0][1]][0]
        bbox = img.getchannel('A').getbbox()
        return img.width, (bbox[3] if bbox else img.height)

    def move(self, dx):
        out = ''
        size = abs(dx)
        for bit in range(8):
            if size >> bit & 1:
                for ch, adv in self.spaces.items():
                    if adv == (-(1 << bit) if dx < 0 else (1 << bit)):
                        out += ch
                        break
                else:
                    raise KeyError(f'no space for {dx}')
        return out

    def width(self, text):
        return sum(self.spaces[c] if c in self.spaces else self.glyphs[c][2] for c in text)

    def draw(self, img, text, x, text_y):
        """The client: glyphs at (cursor, textY + 7 - ascent), then advance."""
        for c in text:
            if c in self.spaces:
                x += self.spaces[c]
                continue
            glyph, ascent, adv = self.glyphs[c]
            img.alpha_composite(glyph, (x, text_y + 7 - ascent))
            x += adv


FONT_DATA = SewingFontData()


class Label:
    def __init__(self, button_width):
        self.w = button_width
        self.text = ''
        self.pos = LABEL_INSET + OVERHANG

    def at(self, name, x, top):
        self.text += FONT_DATA.move(x - self.pos) + FONT_DATA.char(name, top)
        self.pos = x + FONT_DATA.size(name)[0] + 1
        return self

    def cell(self, name):
        return self.at(name, 0, 0)

    def build(self):
        end = LABEL_INSET + OVERHANG + self.w - 2 * LABEL_INSET
        return self.text + FONT_DATA.move(end - self.pos)


def overlay_x(px):
    return px - (COLS - 1) * PITCH


def overlay_top(py):
    return py - (ROWS - 1) * PITCH


# ------------------------------------------------------------------ the seam, as Seam.java computes it

with open(os.path.join(GEN, 'ovvar', 'outlines.json')) as f:
    OUTLINES = json.load(f)


def patch_geometry(cells):
    scale = 20 if cells == 1 else 12
    w, h = 4 * cells * scale, 4 * scale
    return scale, w, h, (PICTURE_W - w) // 2, (PICTURE_H - h) // 2


def holes(patch, cells, stitches):
    segs = OUTLINES[patch]
    scale, w, h, x0, y0 = patch_geometry(cells)
    out = []
    for i in range(stitches):
        d = len(segs) * (i + 0.5) / stitches
        idx = int(math.floor(d)) % len(segs)
        f = d - math.floor(d)
        sx0, sy0, sx1, sy1, nx, ny = segs[idx]
        px, py = sx0 + (sx1 - sx0) * f, sy0 + (sy1 - sy0) * f
        outside = i % 2 == 0
        shift = OUT if outside else -IN
        x = round(x0 + px * scale + nx * shift)
        y = round(y0 + py * scale + ny * shift)
        frm = ('LEFT' if nx < 0 else 'RIGHT') if abs(nx) >= abs(ny) else ('ABOVE' if ny < 0 else 'BELOW')
        m = MARK // 2
        out.append((max(m, min(PICTURE_W - 1 - m, x)), max(m, min(PICTURE_H - 1 - m, y)), outside, frm))
    return out


def on_the_cloth(label, patch, cells, hs, done):
    scale, w, h, x0, y0 = patch_geometry(cells)
    label.at('patch_' + patch, overlay_x(x0), overlay_top(y0))
    for i, (x, y, _, _) in enumerate(hs):
        label.at('cross' if i < done else 'hole', overlay_x(x - MARK // 2), overlay_top(y - MARK // 2))
    if done >= len(hs):
        return
    x, y, _, frm = hs[done]
    across, tail = NEEDLE_WIDTH // 2, NEEDLE_LENGTH - 1
    name, gx, gy = {
        'LEFT': ('needle_r', x - tail, y - across),
        'RIGHT': ('needle_l', x, y - across),
        'ABOVE': ('needle_d', x - across, y - tail),
        'BELOW': ('needle_u', x - across, y),
    }[frm]
    gw, gh = FONT_DATA.size(name)
    label.at(name, overlay_x(max(0, min(PICTURE_W - gw, gx))), overlay_top(max(0, min(PICTURE_H - gh, gy))))


# ------------------------------------------------------------------ the dialog

def dialog(chapter, patch, cells, stitches, done, spot='front, top left', show_buttons=False):
    img = screen(W, H)
    name = patch.title()
    FONT.centred(img, f"Sewing on the {name}", W // 2, 10)
    FONT.centred(img, f"Whip-stitch it onto the {spot}: click the needle to pull it through.", W // 2, 26)
    hs = holes(patch, cells, stitches)
    nxt = hs[done] if done < len(hs) else None
    grid_w = COLS * CELL + (COLS - 1) * GAP
    gx0, gy0 = W // 2 - grid_w // 2, 40
    for row in range(ROWS):
        for col in range(COLS):
            bx, by = gx0 + col * PITCH, gy0 + row * PITCH
            label = Label(CELL).cell('cloth_' + chapter)
            if row == ROWS - 1 and col == COLS - 1:
                on_the_cloth(label, patch, cells, hs, done)
            text = label.build()
            width = FONT_DATA.width(text)
            assert width == CELL - 2 * LABEL_INSET, (width, row, col)
            if show_buttons:   # what is underneath: the vanilla buttons the glyphs hide
                vanilla_button(img, bx, by, CELL, '')
            else:
                # the client's centring: (minX + maxX)/2 - width/2 with inset 2 → button.x + 2
                FONT_DATA.draw(img, text, bx + LABEL_INSET, by + 6)
    band = Label(BAND).cell('band').build()
    assert FONT_DATA.width(band) == BAND - 2 * LABEL_INSET
    FONT_DATA.draw(img, band, W // 2 - BAND // 2 + LABEL_INSET, FOOTER_Y + 6)
    if nxt is not None:
        # the clickable cell, for the reader (the game shows nothing extra: the needle is the cue)
        pass
    return img


def strip(frames, captions):
    out = Image.new('RGBA', (len(frames) * (W + 8) - 8, H + 14), (24, 24, 24, 255))
    for i, (f, cap) in enumerate(zip(frames, captions)):
        out.alpha_composite(f, (i * (W + 8), 14))
        FONT.draw(out, cap, i * (W + 8) + 2, 3, (200, 200, 200, 255), shadow=False)
    return out


def save(img, name):
    big = img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST)
    big.convert('RGB').save(os.path.join(HERE, name))
    print("wrote", name, big.size)


def main():
    save(strip([dialog('data', 'heart', 1, 6, 0), dialog('data', 'heart', 1, 6, 3), dialog('data', 'heart', 1, 6, 5)],
               ["start", "3 of 6 pulled", "the last pull"]), "asbuilt_heart_data.png")
    save(strip([dialog('it', 'star', 1, 12, 7), dialog('it_kisel', 'kth', 1, 16, 15), dialog('media', 'chapter', 2, 8, 4, spot='seat')],
               ["star, 12 stitches, 7 pulled (IT)", "KTH, 16 stitches, the last (kisel)", "seat patch, 8 stitches, 4 pulled (media)"]), "asbuilt_variants.png")
    save(strip([dialog('data', 'heart', 1, 6, 3, show_buttons=True), dialog('data', 'heart', 1, 6, 3)],
               ["the 49 vanilla buttons underneath", "what the glyphs make of them"]), "asbuilt_under_the_hood.png")


if __name__ == '__main__':
    main()
