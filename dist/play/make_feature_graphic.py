"""
Play Store feature graphic, drawn to exactly 1024x500.

Rendered at 2x and downsampled, which is what gives the type its edges; a
browser screenshot could not be held to an exact pixel size and Play rejects
anything that is not 1024x500 on the nose.

Everything here is the app's own Ledger vocabulary: flat ground, 2px rules
instead of shadows, no radii anywhere, Archivo, and the accent used exactly
once.
"""
from PIL import Image, ImageDraw, ImageFont

S = 2                      # supersample factor
W, H = 1024 * S, 500 * S

BG      = "#F3F2F2"
TEXT    = "#201E1D"
RULE    = "#201E1D"
ACCENT  = "#EC3013"
N400    = "#BAB6B6"
N700    = "#605D5D"

FONT_DIR = r"E:\DocKeep\app\src\main\res\font"


def font(name, size):
    return ImageFont.truetype(rf"{FONT_DIR}\archivo_{name}.ttf", int(size * S))


f_black   = font("black", 92)
f_kicker  = font("bold", 15)
f_tagline = font("medium", 25)
f_tag_b   = font("bold", 25)
f_label   = font("bold", 12.5)
f_tile    = font("black", 23)
f_name    = font("bold", 15.5)
f_meta    = font("medium", 11.5)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)


def tracked(xy, text, fnt, fill, em):
    """Draw with letter-spacing, which PIL has no notion of.

    Used only for the small uppercase labels, where tracking carries the
    style and losing pair kerning costs nothing. The wordmark is drawn as
    one string so its kerning survives.
    """
    x, y = xy
    extra = em * fnt.size
    for ch in text:
        d.text((x, y), ch, font=fnt, fill=fill)
        x += d.textlength(ch, font=fnt) + extra
    return x


def rule(x0, y0, x1, y1):
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=RULE)


# ── Left: the pitch ──────────────────────────────────────────────────
L = 60 * S
COL_W = 470 * S
y = 96 * S

tracked((L, y), "DOCUMENT VAULT", f_kicker, ACCENT, 0.22)
y += 15 * S + 20 * S

d.text((L, y), "DocKeep", font=f_black, fill=TEXT)
y += 88 * S + 24 * S

rule(L, y, L + COL_W, y + 3 * S)
y += 3 * S + 24 * S

d.text((L, y), "Your documents. On your phone.", font=f_tagline, fill=TEXT)
y += 34 * S
d.text((L, y), "Nowhere else.", font=f_tag_b, fill=TEXT)
y += 34 * S + 28 * S

# Three verbs in a ruled strip.
strip_h = 42 * S
rule(L, y, L + COL_W, y + 2 * S)
rule(L, y + strip_h - 2 * S, L + COL_W, y + strip_h)
third = COL_W // 3
for i, word in enumerate(("SCAN", "SEARCH", "LOCK")):
    cx = L + i * third
    if i:
        rule(cx, y, cx + 2 * S, y + strip_h)
    tracked((cx + 15 * S, y + 14 * S), word, f_label, N700, 0.13)


# ── Right: a shelf of documents ──────────────────────────────────────
SHELF_X = 604 * S
rule(SHELF_X, 0, SHELF_X + 3 * S, H)

cell_w = (W - SHELF_X - 3 * S) // 2
cell_h = H // 2
cells = [
    ("A", "#FFEB3B", "#000000", "Aadhaar Card",   "4 PAGES",  False),
    ("P", "#2196F3", "#FFFFFF", "Passport",       "LOCKED",   True),
    ("D", "#4CAF50", "#FFFFFF", "Driving Licence", "2 PAGES", False),
    ("M", "#9C27B0", "#FFFFFF", "Marksheets",     "9 PAGES",  False),
]

for i, (letter, tile_bg, tile_fg, name, meta, is_accent) in enumerate(cells):
    col, row = i % 2, i // 2
    x0 = SHELF_X + 3 * S + col * cell_w
    y0 = row * cell_h

    if col == 0:
        rule(x0 + cell_w, y0, x0 + cell_w + 2 * S, y0 + cell_h)
    if row == 0:
        rule(x0, y0 + cell_h, x0 + cell_w, y0 + cell_h + 2 * S)

    px, py = x0 + 26 * S, y0 + 30 * S
    tile = 46 * S
    d.rectangle([px, py, px + tile, py + tile], fill=tile_bg)
    bb = d.textbbox((0, 0), letter, font=f_tile)
    d.text(
        (px + (tile - (bb[2] - bb[0])) / 2 - bb[0],
         py + (tile - (bb[3] - bb[1])) / 2 - bb[1]),
        letter, font=f_tile, fill=tile_fg
    )

    by = y0 + cell_h - 62 * S
    d.text((px, by), name, font=f_name, fill=TEXT)
    tracked((px, by + 24 * S), meta, f_meta, ACCENT if is_accent else N400, 0.11)


out = r"E:\DocKeep\dist\play\feature-graphic-1024x500.png"
import os
os.makedirs(os.path.dirname(out), exist_ok=True)
img.resize((1024, 500), Image.LANCZOS).save(out, "PNG")

check = Image.open(out)
print("wrote", out)
print("size:", check.size, "mode:", check.mode)
