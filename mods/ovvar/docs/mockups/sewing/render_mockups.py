#!/usr/bin/env python3
"""
Renders the stitching-minigame dialog mockups (PNG) into this directory.

Everything is drawn at Minecraft's logical GUI resolution and then scaled up with nearest
neighbour, so 1 px here is 1 GUI px in game. The vanilla font and button sprites come out of the
client jar; the patch art comes out of src/main/resources/art/ovvar/patches.

    unzip -o -q ~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar \
        'assets/minecraft/textures/font/ascii.png' 'assets/minecraft/textures/gui/sprites/widget/button*' -d /tmp/mcassets
    python3 render_mockups.py            # needs Pillow

The placeholder sprites (fabric, needle, thread, scissors) are programmer art: the hand-drawn
ones replace them one for one, at the sizes noted in README.md.
"""
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ASSETS = os.environ.get('MC_ASSETS', '/tmp/mcassets/assets/minecraft')
HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.normpath(os.path.join(HERE, '..', '..', '..', 'src', 'main', 'resources', 'art', 'ovvar'))
SCALE = 3

WHITE = (255, 255, 255, 255)
GRAY = (160, 160, 160, 255)
DARK_GRAY = (85, 85, 85, 255)
GREEN = (85, 255, 85, 255)
GOLD = (255, 170, 0, 255)

THREAD = (250, 236, 120, 255)        # a pale yellow thread reads on both cerise and purple
THREAD_DARK = (170, 150, 50, 255)
NEEDLE = (222, 226, 232, 255)
NEEDLE_DARK = (120, 128, 140, 255)
HOLE = (40, 12, 26, 255)


# ------------------------------------------------------------------ vanilla bits

class Font:
    ROWS = [
        "                ",
        "                ",
        " !\"#$%&'()*+,-./",
        "0123456789:;<=>?",
        "@ABCDEFGHIJKLMNO",
        "PQRSTUVWXYZ[\\]^_",
        "`abcdefghijklmno",
        "pqrstuvwxyz{|}~ ",
    ]

    def __init__(self):
        sheet = Image.open(f'{ASSETS}/textures/font/ascii.png').convert('RGBA')
        self.glyphs = {}
        for r, row in enumerate(self.ROWS):
            for c, ch in enumerate(row):
                g = sheet.crop((c * 8, r * 8, c * 8 + 8, r * 8 + 8))
                w = 0
                for x in range(8):
                    for y in range(8):
                        if g.getpixel((x, y))[3] > 0:
                            w = x + 1
                if ch == ' ':
                    w = 3
                self.glyphs[ch] = (g, w)

    def width(self, text):
        return sum(self.glyphs.get(ch, self.glyphs['?'])[1] + 1 for ch in text)

    def draw(self, img, text, x, y, color=WHITE, shadow=True):
        if shadow:
            self._draw(img, text, x + 1, y + 1, tuple(c // 4 for c in color[:3]) + (255,))
        self._draw(img, text, x, y, color)

    def _draw(self, img, text, x, y, color):
        for ch in text:
            g, w = self.glyphs.get(ch, self.glyphs['?'])
            tinted = Image.new('RGBA', g.size, color)
            tinted.putalpha(g.getchannel('A'))
            img.alpha_composite(tinted, (x, y))
            x += w + 1

    def centred(self, img, text, cx, y, color=WHITE, shadow=True):
        self.draw(img, text, cx - self.width(text) // 2, y, color, shadow)


FONT = Font()


def nine_slice(sprite, w, h, b=3):
    sw, sh = sprite.size
    out = Image.new('RGBA', (w, h))

    def put(sx, sy, sw_, sh_, dx, dy, dw, dh):
        if dw <= 0 or dh <= 0:
            return
        tile = sprite.crop((sx, sy, sx + sw_, sy + sh_))
        for yy in range(dy, dy + dh, sh_):
            for xx in range(dx, dx + dw, sw_):
                t = tile.crop((0, 0, min(sw_, dx + dw - xx), min(sh_, dy + dh - yy)))
                out.alpha_composite(t, (xx, yy))

    put(0, 0, b, b, 0, 0, b, b)
    put(sw - b, 0, b, b, w - b, 0, b, b)
    put(0, sh - b, b, b, 0, h - b, b, b)
    put(sw - b, sh - b, b, b, w - b, h - b, b, b)
    put(b, 0, sw - 2 * b, b, b, 0, w - 2 * b, b)
    put(b, sh - b, sw - 2 * b, b, b, h - b, w - 2 * b, b)
    put(0, b, b, sh - 2 * b, 0, b, b, h - 2 * b)
    put(sw - b, b, b, sh - 2 * b, w - b, b, b, h - 2 * b)
    put(b, b, sw - 2 * b, sh - 2 * b, b, b, w - 2 * b, h - 2 * b)
    return out


BUTTON = {
    'normal': Image.open(f'{ASSETS}/textures/gui/sprites/widget/button.png').convert('RGBA'),
    'hover': Image.open(f'{ASSETS}/textures/gui/sprites/widget/button_highlighted.png').convert('RGBA'),
    'disabled': Image.open(f'{ASSETS}/textures/gui/sprites/widget/button_disabled.png').convert('RGBA'),
}


def vanilla_button(img, x, y, w, label, color=WHITE, state='normal'):
    img.alpha_composite(nine_slice(BUTTON[state], w, 20), (x, y))
    if state == 'disabled':
        color = GRAY
    FONT.centred(img, label, x + w // 2, y + 6, color)


def screen(w, h):
    """An in-world dialog background: the blurred world under a dark gradient."""
    rnd = random.Random(7)
    img = Image.new('RGBA', (w, h), (98, 140, 190, 255))
    d = ImageDraw.Draw(img)
    for _ in range(40):
        cx, cy = rnd.randint(0, w), rnd.randint(h // 3, h)
        r = rnd.randint(20, 60)
        col = rnd.choice([(80, 120, 50), (95, 135, 60), (110, 90, 60), (70, 105, 45)])
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=col + (255,))
    d.rectangle((0, 0, w, h // 3), fill=(120, 165, 210, 255))
    img = img.filter(ImageFilter.GaussianBlur(6))
    shade = Image.new('RGBA', (w, h))
    sd = ImageDraw.Draw(shade)
    for yy in range(h):
        a = int(0xC0 + (0xD0 - 0xC0) * yy / h)
        sd.line((0, yy, w, yy), fill=(16, 16, 16, a))
    img.alpha_composite(shade)
    return img


# ------------------------------------------------------------------ placeholder sprites

def sample_chapter_colour(name):
    im = Image.open(f'{ART}/{name}.png').convert('RGBA')
    px = [p for p in im.getdata() if p[3] == 255 and not (p[0] == 0 and p[1] == 255 and p[2] == 0)]
    px.sort(key=lambda p: -(max(p[:3]) - min(p[:3])))  # the most saturated: the cloth, not the seams
    top = px[:max(1, len(px) // 4)]
    return tuple(sum(c[i] for c in top) // len(top) for i in range(3))


def fabric(w, h, colour, seed=1, lit=False):
    """Woven cloth. A white version tinted by the text colour does the same job in the pack."""
    rnd = random.Random(seed)
    img = Image.new('RGBA', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            n = rnd.randint(-7, 7) + (6 if (x + y) % 2 == 0 else -6) + (14 if lit else 0)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in colour) + (255,)
    return img


def patch(name, scale):
    """The real patch art, scaled up, with a rolled edge (a 1 px darker border) as sewn patches have."""
    art = Image.open(f'{ART}/patches/{name}.png').convert('RGBA')
    big = art.resize((art.width * scale, art.height * scale), Image.NEAREST)
    d = ImageDraw.Draw(big)
    d.rectangle((0, 0, big.width - 1, big.height - 1), outline=(60, 50, 50, 140))
    return big


def dot(d, x, y, colour=HOLE):
    d.rectangle((x - 1, y - 1, x, y), fill=colour)


def cross(d, x, y, colour=THREAD, size=2):
    for i in range(-size, size + 1):
        d.point((x + i, y + i), fill=colour)
        d.point((x - i, y + i), fill=colour)
    d.point((x + size + 1, y + size + 1), fill=THREAD_DARK)
    d.point((x - size - 1, y + size + 1), fill=THREAD_DARK)


def dashed(d, x0, y0, x1, y1, colour=THREAD, on=2, off=2):
    steps = max(abs(x1 - x0), abs(y1 - y0))
    if steps == 0:
        return
    for i in range(steps + 1):
        if i % (on + off) < on:
            d.point((round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps)), fill=colour)


def needle(d, x, y, direction, length=22):
    """A needle pointing along `direction` (+1 right, -1 left) with the tip at (x, y); eye at the back."""
    bx = x - direction * length
    d.line((bx, y + 1, x - direction * 2, y + 1), fill=NEEDLE_DARK)
    d.line((bx, y, x, y), fill=NEEDLE)
    d.line((bx, y - 1, x - direction * 3, y - 1), fill=WHITE)
    d.point((x, y), fill=WHITE)
    ex = sorted((bx + direction * 2, bx + direction * 3))
    d.rectangle((ex[0], y - 1, ex[1], y + 1), fill=HOLE)  # the eye
    d.point((bx + direction * 2, y), fill=THREAD)
    return bx, y


def thread_curl(d, x, y, direction):
    """The loose thread hanging out of the eye behind the needle."""
    pts = [(x, y), (x - direction * 2, y - 2), (x - direction * 4, y - 3), (x - direction * 6, y - 2), (x - direction * 7, y + 1),
           (x - direction * 6, y + 4), (x - direction * 3, y + 5), (x - direction * 1, y + 4)]
    d.line(pts, fill=THREAD)


def scissors(d, x, y):
    """Open scissors, 16 wide, pivot at (x, y)."""
    d.line((x, y, x + 9, y - 4), fill=NEEDLE)
    d.line((x, y, x + 9, y + 4), fill=NEEDLE)
    d.line((x, y + 1, x + 9, y - 3), fill=NEEDLE_DARK)
    d.line((x, y + 1, x + 9, y + 5), fill=NEEDLE_DARK)
    d.ellipse((x - 7, y - 6, x - 1, y - 1), outline=(160, 40, 50, 255))
    d.ellipse((x - 7, y + 1, x - 1, y + 6), outline=(160, 40, 50, 255))
    d.point((x, y), fill=HOLE)


def ribbon_button(img, x, y, w, label, colour=(52, 40, 46, 255)):
    """The exit button as a sprite: a dark cloth band with scissors, text painted on."""
    band = fabric(w, 20, colour[:3], seed=3)
    d = ImageDraw.Draw(band)
    d.rectangle((0, 0, w - 1, 19), outline=(20, 12, 16, 255))
    dashed(d, 2, 10, w - 3, 10, colour=(120, 100, 110, 255), on=3, off=3)
    tw = FONT.width(label)
    d.rectangle((w // 2 - tw // 2 - 3, 4, w // 2 + tw // 2 + 3, 15), fill=colour)
    scissors(d, 14, 10)
    img.alpha_composite(band, (x, y))
    FONT.centred(img, label, x + w // 2, y + 6, (230, 210, 215, 255))


# ------------------------------------------------------------------ frames

W, H = 300, 236
FOOTER_Y = H - 20 - 10
GAP = 2  # between grid buttons


def frame_start(title, body_lines):
    img = screen(W, H)
    FONT.centred(img, title, W // 2, 10)
    y = 26
    for line in body_lines:
        FONT.centred(img, line, W // 2, y)
        y += 9
    return img, y + (4 if body_lines else 0)


def before(done, stitches=6, patch_name='Heart', spot='front, top left'):
    """The dialog as it is now (unicode glyphs approximated with ascii)."""
    img, y = frame_start(f"Sewing on the {patch_name}", [
        "Pull the needle through from the side it is on,",
        f"back and forth down the seam, on the {spot}.",
    ])
    bw = 110
    x0 = W // 2 - (2 * bw + GAP) // 2
    for row in range(stitches):
        left = row % 2 == 0
        if row < done:
            n, o, nc, oc = "X", "- - -", GOLD, DARK_GRAY
        elif row == done:
            n, o, nc, oc = (">> pull the thread" if left else "pull the thread <<"), ".", GREEN, DARK_GRAY
        else:
            n, o, nc, oc = ".", ".", DARK_GRAY, DARK_GRAY
        cells = [(n, nc), (o, oc)] if left else [(o, oc), (n, nc)]
        for i, (label, colour) in enumerate(cells):
            vanilla_button(img, x0 + i * (bw + GAP), y + row * (20 + GAP), bw, label, colour)
    vanilla_button(img, W // 2 - (2 * bw + 10) // 2, FOOTER_Y, 2 * bw + 10, "Cut the thread")
    return img


def concept_a(done, colour, stitches=6, patch_name='heart', spot='front, top left'):
    """A: the same two-column seam, every cell an opaque cloth sprite; the seam is the gap between the columns."""
    img, y = frame_start(f"Sewing on the {patch_name.title()}", [])
    # body: the patch lying on the cloth, four 9 px glyph strips = a 36 px picture
    pw, ph = 222, 36
    body = fabric(pw, ph, colour, seed=11)
    p = patch(patch_name, 7)
    body.alpha_composite(p, (pw // 2 - p.width // 2, ph // 2 - p.height // 2))
    d = ImageDraw.Draw(body)
    d.rectangle((0, 0, pw - 1, ph - 1), outline=(0, 0, 0, 90))
    img.alpha_composite(body, (W // 2 - pw // 2, y))
    FONT.draw(img, f"on the {spot}", W // 2 - pw // 2 + 4, y + ph - 11, WHITE)
    count = f"{done}/{stitches}"
    FONT.draw(img, count, W // 2 + pw // 2 - 4 - FONT.width(count), y + ph - 11, THREAD)
    y += ph + 6

    bw = 110
    x0 = W // 2 - (2 * bw + GAP) // 2
    for row in range(stitches):
        left = row % 2 == 0
        cy = y + row * (20 + GAP)
        for col in range(2):
            is_needle_side = (col == 0) == left
            live = is_needle_side and row == done
            cell = fabric(bw, 20 + GAP, colour, seed=100 + row * 2 + col, lit=live)   # 2 px taller: covers the row gap
            d = ImageDraw.Draw(cell)
            inner = bw - 1 if col == 0 else 0            # the edge on the seam
            seam_dir = 1 if col == 0 else -1              # towards the seam
            # a dark selvedge along the inner edge, so the 2 px grid gap reads as the seam itself
            d.line((inner, 0, inner, 21), fill=(0, 0, 0, 110))
            d.line((inner - seam_dir, 0, inner - seam_dir, 21), fill=(0, 0, 0, 50))
            hx = inner - seam_dir * 6                       # where the needle goes through, this side of the seam
            if row < done:
                if is_needle_side:
                    cross(d, hx, 10)
                    dashed(d, hx + seam_dir * 2, 12, inner, 21, colour=THREAD_DARK)   # on to the next row
                else:
                    dashed(d, inner, 0, inner - seam_dir * 5, 8, colour=THREAD_DARK)
                    dot(d, hx, 10)
            elif row == done:
                if is_needle_side:
                    if row > 0:
                        dashed(d, inner, 0, hx - seam_dir * 6, 9, colour=THREAD)
                    bx, by = needle(d, hx, 10, seam_dir)
                    thread_curl(d, bx, by, seam_dir)
                    dot(d, hx, 10)
                else:
                    dot(d, hx, 10)
            else:
                dot(d, hx, 10)
            img.alpha_composite(cell, (x0 + col * (bw + GAP), cy))
    ribbon_button(img, W // 2 - (2 * bw + GAP) // 2, FOOTER_Y, 2 * bw + GAP, "Cut the thread")
    return img


def concept_b(done, colour, stitches=6, patch_name='heart', spot='front, top left', annotate=False):
    """B: the grid is the picture. Middle column: the patch on the cloth, sliced into 20 px label buttons.
    Side columns: the needle, beside the edge it goes through next. Stitches lace down both edges."""
    img, y = frame_start(f"Sewing on the {patch_name.title()}", [f"Whip-stitch it onto the {spot}."])
    side, mid = 44, 120
    rows = stitches
    grid_h = rows * 20 + (rows - 1) * GAP
    x0 = W // 2 - (2 * side + mid + 2 * GAP) // 2
    mx = x0 + side + GAP

    # the picture, drawn whole and then sliced
    pic = fabric(mid, grid_h, colour, seed=5)   # the six label glyphs, shown joined up as they are in game
    p = patch(patch_name, 20)
    px, py = mid // 2 - p.width // 2, grid_h // 2 - p.height // 2
    pic.alpha_composite(p, (px, py))
    d = ImageDraw.Draw(pic)
    anchors = []
    for r in range(rows):
        cy = r * (20 + GAP) + 10
        cy = max(py + 3, min(py + p.height - 4, cy))
        ax = px + 3 if r % 2 == 0 else px + p.width - 4
        anchors.append((ax, cy))
    # the thread running behind the patch between the stitches, dashed: it is on the underside
    for r in range(1, min(done, rows)):
        dashed(d, *anchors[r - 1], *anchors[r], colour=(255, 255, 255, 120), on=2, off=3)
    for r, (ax, ay) in enumerate(anchors):
        if r < done:
            cross(d, ax, ay)
        else:
            dot(d, ax, ay, HOLE if r > done else (60, 20, 40, 255))
    if 0 < done < rows:  # the thread from the last stitch out to the needle
        ax, ay = anchors[done - 1]
        nx, ny = anchors[done]
        edge = 0 if done % 2 == 0 else mid - 1
        dashed(d, ax, ay, nx, ny, colour=(255, 255, 255, 120), on=2, off=3)
        d.line((nx, ny, edge, ny), fill=THREAD)

    # in game every glyph is 2 px taller and wider than its button, so neighbours meet across the grid gaps
    img.alpha_composite(pic, (mx, y))
    for r in range(rows):
        cy = y + r * (20 + GAP)
        left = r % 2 == 0
        for col, sx in ((0, x0), (1, mx + mid + GAP)):
            live = (col == 0) == left and r == done
            cell = fabric(side + GAP, 20 + (GAP if r < rows - 1 else 0), colour, seed=50 + r * 2 + col, lit=live)
            dd = ImageDraw.Draw(cell)
            if live:
                dirn = 1 if col == 0 else -1
                tip = side if col == 0 else 1
                ny = anchors[r][1] - r * (20 + GAP)
                bx, by = needle(dd, tip, ny, dirn, length=24)
                thread_curl(dd, bx, by, dirn)
                if r > 0:
                    dd.line((tip, ny, side + 1 if col == 0 else 0, ny), fill=THREAD)
            img.alpha_composite(cell, (sx if col == 0 else sx - GAP, cy))
    total = 2 * side + mid + 2 * GAP
    ribbon_button(img, W // 2 - total // 2, FOOTER_Y, total, "Cut the thread")

    if annotate:
        notes = [
            (x0 + side // 2, y + done * (20 + GAP) + 10, "needle button: the only clickable cell", 'left'),
            (mx + mid // 2, y + 10, "6 label buttons = the picture in 20 px slices", 'right'),
            (mx + anchors[0][0], y + anchors[0][1], "stitch = glyph laid over the cloth glyph", 'left'),
            (W // 2, FOOTER_Y + 10, "exit button, skinned the same way", 'right'),
            (W // 2 + 60, 14, "plain title text", 'right'),
        ]
        return img, notes
    return img


def concept_c(done, colour, stitches=6, patch_name='heart', spot='front, top left'):
    """C: one wide picture in the body (the hoop) and a single row of two needle buttons: a rhythm, not a grid."""
    img, y = frame_start(f"Sewing on the {patch_name.title()}", [])
    pw, ph = 240, 99   # 11 lines of 9 px
    pic = fabric(pw, ph, (150, 110, 70), seed=9)   # the table
    d = ImageDraw.Draw(pic)
    cx, cy, R = pw // 2, ph // 2 - 4, 38
    d.ellipse((cx - R - 3, cy - R - 3, cx + R + 3, cy + R + 3), fill=(120, 80, 40, 255))   # hoop
    cloth = fabric(2 * R, 2 * R, colour, seed=4)
    mask = Image.new('L', cloth.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 2 * R - 1, 2 * R - 1), fill=255)
    pic.paste(cloth, (cx - R, cy - R), mask)
    d.ellipse((cx - R - 3, cy - R - 3, cx + R + 3, cy + R + 3), outline=(80, 50, 25, 255), width=2)
    d.ellipse((cx - R, cy - R, cx + R, cy + R), outline=(60, 35, 20, 255))
    p = patch(patch_name, 12)
    px, py = cx - p.width // 2, cy - p.height // 2
    pic.alpha_composite(p, (px, py))
    # six stitch slots around the border: left edge top/mid/bottom on even pulls, right edge on odd pulls
    ys = [py + 5, py + p.height // 2, py + p.height - 6]
    slots = []
    for i in range(stitches):
        r = i // 2
        slots.append((px + 3 if i % 2 == 0 else px + p.width - 4, ys[r % 3]))
    for i in range(1, done):
        dashed(d, *slots[i - 1], *slots[i], colour=(255, 255, 255, 110), on=2, off=3)
    for i, (sx, sy) in enumerate(slots):
        if i < done:
            cross(d, sx, sy)
        else:
            dot(d, sx, sy)
    if done < stitches:
        sx, sy = slots[done]
        dirn = 1 if done % 2 == 0 else -1
        tip = sx - dirn * 6
        if done > 0:
            dashed(d, *slots[done - 1], sx, sy, colour=(255, 255, 255, 110), on=2, off=3)
            d.line((sx, sy, tip, sy), fill=THREAD)
        bx, by = needle(d, tip, sy, dirn, length=18)
        thread_curl(d, bx, by, dirn)
    # spool + thread left: the progress bar, bundle-style
    sx0, sy0 = 10, ph - 20
    d.rectangle((sx0, sy0, sx0 + 10, sy0 + 14), fill=(200, 170, 120, 255), outline=(90, 60, 30, 255))
    left_frac = 1 - done / stitches
    d.rectangle((sx0 + 2, sy0 + 2 + int(10 * (1 - left_frac)), sx0 + 8, sy0 + 12), fill=THREAD)
    for i in range(stitches):
        col = THREAD if i >= done else (70, 50, 40, 255)
        d.rectangle((sx0 + 16 + i * 9, sy0 + 5, sx0 + 16 + i * 9 + 6, sy0 + 9), fill=col, outline=(40, 25, 15, 255))
    label = f"stitch {min(done + 1, stitches)} of {stitches}"
    FONT.draw(pic, label, pw - 8 - FONT.width(label), ph - 16, WHITE)
    d.rectangle((0, 0, pw - 1, ph - 1), outline=(0, 0, 0, 120))
    img.alpha_composite(pic, (W // 2 - pw // 2, y))
    y += ph + 6

    bw = 119
    x0 = W // 2 - (2 * bw + GAP) // 2
    for col in range(2):
        live = (col == 0) == (done % 2 == 0)
        cell = fabric(bw, 20, colour if live else tuple(c // 2 for c in colour), seed=70 + col, lit=live)
        dd = ImageDraw.Draw(cell)
        dirn = 1 if col == 0 else -1
        label = "from the left" if col == 0 else "from the right"
        tx = 6 if col == 0 else bw - 6 - FONT.width(label)
        if live:
            tip = bw - 6 if col == 0 else 5
            bx, by = needle(dd, tip, 10, dirn, length=26)
            thread_curl(dd, bx, by, dirn)
            FONT.draw(cell, label, tx, 6, WHITE)
        else:
            FONT.draw(cell, label, tx, 6, (110, 100, 105, 255), shadow=False)
        dd.rectangle((0, 0, bw - 1, 19), outline=(0, 0, 0, 100))
        img.alpha_composite(cell, (x0 + col * (bw + GAP), y))
    ribbon_button(img, W // 2 - (2 * bw + GAP) // 2, FOOTER_Y, 2 * bw + GAP, "Cut the thread")
    return img


# ------------------------------------------------------------------ output

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


def annotated(img, notes):
    margin = 240
    out = Image.new('RGBA', (W + 2 * margin, H), (24, 24, 24, 255))
    out.alpha_composite(img, (margin, 0))
    d = ImageDraw.Draw(out)
    used = {'left': [], 'right': []}
    for (x, y, text, side) in notes:
        tx = 6 if side == 'left' else W + margin + 8
        ty = y - 4
        while any(abs(ty - u) < 11 for u in used[side]):
            ty += 11
        used[side].append(ty)
        FONT.draw(out, text, tx, ty, (255, 220, 120, 255), shadow=False)
        ex = margin + x
        lx = 6 + FONT.width(text) + 3 if side == 'left' else W + margin + 5
        d.line((lx, ty + 4, ex, y), fill=(255, 220, 120, 200))
        d.rectangle((ex - 1, y - 1, ex + 1, y + 1), fill=(255, 220, 120, 255))
    return out


def main():
    data = sample_chapter_colour('data')
    it = sample_chapter_colour('it')
    print("cloth colours: data", data, "it", it)
    caps = ["start: nothing stitched", "3 of 6 pulled", "the last pull"]

    save(strip([before(0), before(3), before(5)], caps), "0_current.png")
    save(strip([concept_a(0, data), concept_a(3, data), concept_a(5, data)], caps), "a_seam_grid.png")
    save(strip([concept_b(0, data), concept_b(3, data), concept_b(5, data)], caps), "b_lacing.png")
    save(strip([concept_c(0, it, patch_name='kth'), concept_c(3, it, patch_name='kth'), concept_c(5, it, patch_name='kth')], caps), "c_hoop.png")
    img, notes = concept_b(3, it, patch_name='metacraft', spot='right sleeve', annotate=True)
    save(annotated(img, notes), "b_lacing_annotated.png")
    save(strip([concept_a(3, it, patch_name='star'), concept_b(3, it, patch_name='star'), concept_c(3, it, patch_name='star')],
               ["A on the IT ovve", "B on the IT ovve", "C on the IT ovve"]), "abc_side_by_side.png")


if __name__ == '__main__':
    main()
