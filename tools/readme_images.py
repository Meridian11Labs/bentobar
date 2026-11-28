#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Composes the README images from raw captures of BentoBar on an emulator.

The look is the Play listing's: a flat, warm off-white ground, and what the screen shows inside one plain display (a
thin black bezel with a camera dot, no brand). No caption is drawn into a picture: the README says it in text.

The captures are whole screens (2560 × 1600) of the debug build on a desktop emulator, never of anyone's own device:
desktop windowing, 320 dpi, a made-up wallpaper (`python3 tools/readme_images.py wallpaper FILE` writes it; nothing of
a device's own shows), an emptied home screen, the system's demo status bar (Wi-Fi, a full battery, the clock held at
one time) and a demo layout (./bento debug import <base64>) with items in all three areas and four more behind ‹ in
Right. Everything that shows data is staged, so no personal data shows: event stage demo, weather stage clear, media
stage playing, flight show air. No picture shows the Now playing or the Flight menu: their staged data names a
player and an airline.

Raw captures in RAW (default ~/.cache/bentobar/shots):
  weather.png, weather.txt      the Weather menu open under its item in the Left area (./bento debug open weather),
                                and that menu window's frame, "x1 y1 x2 y2" (from dumpsys window, "BentoBar menu")
  calendar, event, memory,      the same for the Date item's menu, Next meeting's, Memory's and Tools', and for the
  tools, ctx (.png and .txt)    right-click menu of an item (./bento debug ctx TYPE)
  bar_folded.png, bar_open.png  the bar with hidden items behind ‹ (Look: Behind ‹, on click), folded and revealed
                                (./bento debug chevron right), no menu open
  app_bar.png                   BentoBar's settings window, freeform over the wallpaper: the Bar page with an item's
                                options (am start … --es item ID)
  app_add.png, app_look.png     the Bar page with Add items, and the Look page, both at 240 dpi (wm density 240, and
                                wm density reset afterwards) in a window nearly as tall as the screen, so a page shows
                                what it does on a Googlebook: PANEL and LOOK below are that window's crops

Writes docs/images/hero.png, bar.png, menus.png, settings.png and settings-pages.png.

    python3 tools/readme_images.py [RAW]
    python3 tools/readme_images.py wallpaper FILE
"""
import math
import pathlib
import random
import re
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path.home() / ".cache/bentobar/shots")
OUT = ROOT / "docs/images"

GROUND = (243, 242, 239)  # the pictures' ground
BEZEL = (14, 15, 17)      # the display around a screen
INK = (18, 20, 26)        # what a shadow is made of
# Shadows as CSS box-shadows say them (offset down, blur, spread, strength): a display's, and a card's.
SHADOW = ((1, 2, 0, .08), (10, 22, -8, .16), (34, 70, -18, .26))
SHADOW_SMALL = ((1, 2, 0, .08), (8, 16, -6, .16), (22, 44, -12, .24))

CLOSE = 0.75        # a close-up of a corner of the display: this much of a capture's size
WHOLE = 0.62        # the display's whole width in a picture
MENUS = 0.6         # the menus, side by side
EDGE = 64           # the ground left free beside what a picture shows, and 56 above it
TOP = 56
MENU_RADIUS = 46    # a menu card's corner, in a capture's pixels
HERO_VIEW = 1500    # the hero shows the screen from its left edge to here: the gap between the Middle and Right areas
PANEL = (1730, 316, 2388, 1498)  # app_add.png: the Bar page's panel (Add items)
LOOK = (690, 148, 1870, 1500)    # app_look.png: the Look page's column, below the header


def rounded_mask(size, radius, ss=4):
    w, h = size
    mask = Image.new("L", (w * ss, h * ss), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w * ss - 1, h * ss - 1), radius * ss, fill=255)
    return mask.resize(size, Image.LANCZOS)


def cast(canvas, box, radius, layers):
    """The soft shadows of a rounded box on [canvas], as CSS box-shadows would fall."""
    x1, y1, x2, y2 = box
    ink = Image.new("RGB", canvas.size, INK)
    for down, blur, spread, strength in layers:
        mask = Image.new("L", canvas.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            (x1 - spread, y1 - spread + down, x2 + spread, y2 + spread + down), max(1, radius + spread),
            fill=round(255 * strength))
        canvas.paste(ink, (0, 0), mask.filter(ImageFilter.GaussianBlur(blur / 2)))


def display(capture, s):
    """A capture in the plain display, at [s] of its size: bezel, camera dot and screen."""
    def r(v):
        return round(v * s)
    side, top, bottom = r(28), r(36), r(28)
    sw, sh = round(capture.width * s), round(capture.height * s)
    body = Image.new("RGBA", (sw + 2 * side, sh + top + bottom), BEZEL + (255,))
    body.putalpha(rounded_mask(body.size, r(54)))
    body.paste(capture.convert("RGB").resize((sw, sh), Image.LANCZOS), (side, top), rounded_mask((sw, sh), r(16)))
    cam, ss = max(5, r(13)), 8
    dot = Image.new("RGBA", (cam * ss, cam * ss), (0, 0, 0, 0))
    draw = ImageDraw.Draw(dot)
    draw.ellipse((0, 0, cam * ss - 1, cam * ss - 1), fill=(21, 23, 26, 255))
    draw.ellipse((ss, ss, (cam - 1) * ss - 1, (cam - 1) * ss - 1), fill=(7, 8, 10, 255))
    draw.ellipse((cam * ss * 0.26, cam * ss * 0.24, cam * ss * 0.50, cam * ss * 0.48), fill=(54, 60, 70, 255))
    dot = dot.resize((cam, cam), Image.LANCZOS)
    body.alpha_composite(dot, ((body.width - cam) // 2, (top - cam) // 2))
    return body


def stand(canvas, capture, s, x, y):
    """Puts a capture's display on [canvas] with its corner at (x, y); what lies outside the canvas runs off it."""
    dev = display(capture, s)
    cast(canvas, (x, y, x + dev.width, y + dev.height), round(54 * s), SHADOW)
    canvas.paste(dev, (x, y), dev)


def ground(w, h):
    return Image.new("RGB", (w, h), GROUND)


def card(im, radius):
    """A picture with rounded corners, the rest see-through: it stands on GitHub's light and dark pages alike."""
    im = im.convert("RGBA")
    im.putalpha(rounded_mask(im.size, radius))
    return im


def raw(name):
    return Image.open(RAW / f"{name}.png").convert("RGB")


def frame(name):
    return [int(v) for v in re.findall(r"-?\d+", (RAW / f"{name}.txt").read_text())[-4:]]


def menu(name):
    """A menu as the screen shows it, glass over the wallpaper, cut to its card."""
    x1, y1, x2, y2 = frame(name)
    size = (round((x2 - x1) * MENUS), round((y2 - y1) * MENUS))
    return raw(name).crop((x1, y1, x2, y2)).resize(size, Image.LANCZOS)


def save(im, name):
    im.save(OUT / name, optimize=True)


def hero():
    """The display's top left corner: the clock, the Left and Middle areas, and the Weather menu open under its item."""
    side, top = round(28 * CLOSE), round(36 * CLOSE)
    w = EDGE + side + round(HERO_VIEW * CLOSE)
    h = TOP + top + round(frame("weather")[3] * CLOSE) + TOP
    canvas = ground(w, h)
    stand(canvas, raw("weather"), CLOSE, EDGE, TOP)
    save(card(canvas, 24), "hero.png")


def bar():
    """The display's top right corner twice, one above the other: the bar folded, and with its hidden items revealed."""
    w, h, gap = EDGE + round(28 * CLOSE) + round(HERO_VIEW * CLOSE), 216, 16
    both = Image.new("RGBA", (w, 2 * h + gap), (0, 0, 0, 0))
    for i, name in enumerate(("bar_folded", "bar_open")):
        canvas = ground(w, h)
        capture = raw(name)
        stand(canvas, capture, CLOSE, w - EDGE - round(capture.width * CLOSE) - 2 * round(28 * CLOSE), TOP)
        both.alpha_composite(card(canvas, 24), (0, i * (h + gap)))
    save(both, "bar.png")


def sheet(columns, gap, radius):
    """Columns of cards, each one under the other, side by side on the ground with their shadows."""
    w = sum(max(c.width for c in col) for col in columns) + gap * (len(columns) - 1) + 2 * EDGE
    h = max(sum(c.height for c in col) + gap * (len(col) - 1) for col in columns) + 2 * TOP
    canvas = ground(w, h)
    x = EDGE
    for col in columns:
        y = TOP
        for c in col:
            cast(canvas, (x, y, x + c.width, y + c.height), radius, SHADOW_SMALL)
            canvas.paste(c, (x, y), rounded_mask(c.size, radius))
            y += c.height + gap
        x += max(c.width for c in col) + gap
    return card(canvas, 24)


def menus():
    """Menus in columns: Weather, the month, Next meeting over the right-click menu, Memory, Tools."""
    columns = [["weather"], ["calendar"], ["event", "ctx"], ["memory"], ["tools"]]
    columns = [[menu(n) for n in col if (RAW / f"{n}.png").exists()] for col in columns]
    save(sheet([c for c in columns if c], gap=32, radius=round(MENU_RADIUS * MENUS)), "menus.png")


def settings():
    """The display's whole width with the settings window: the Bar page and an item's options. It runs off below."""
    capture = raw("app_bar")
    side = round(28 * WHOLE)
    w = round(capture.width * WHOLE) + 2 * side + 2 * EDGE
    canvas = ground(w, 970)
    stand(canvas, capture, WHOLE, EDGE, TOP)
    save(card(canvas, 24), "settings.png")


def settings_pages():
    """Adding items (the Bar page's panel) and the Look page's column, side by side at one height."""
    h = 960
    parts = [raw("app_add").crop(PANEL), raw("app_look").crop(LOOK)]
    parts = [p.resize((round(p.width * h / p.height), h), Image.LANCZOS) for p in parts]
    save(sheet([[p] for p in parts], gap=40, radius=18), "settings-pages.png")


def wallpaper(path):
    """
    A made-up, calm wallpaper (2560 × 1600): a deep teal dusk with a mint glow at the lower right and a warm note at
    the lower left, dark along the top so the status bar's text is white over it. Worked out per pixel in floating
    point and dithered, so the slow gradients show no bands. No photo, nobody's artwork.
    """
    w, h = 2560, 1600
    top, bottom = (16, 52, 58), (38, 96, 100)
    # soft ellipses laid over the gradient: centre, radii, colour, strength, edge softness
    blobs = [
        ((1500, -300), (900, 620), (30, 84, 96), 0.45, 0.30),
        ((2300, 1400), (1050, 950), (120, 190, 176), 0.60, 0.34),
        ((200, 1650), (950, 800), (232, 176, 140), 0.42, 0.34),
        ((2400, 1450), (520, 470), (196, 226, 206), 0.40, 0.36),
    ]
    rng = random.Random(11)
    out = bytearray(w * h * 3)
    i = 0
    for y in range(h):
        t = (y / h) ** 1.3
        base = [top[k] + (bottom[k] - top[k]) * t for k in range(3)]
        for x in range(w):
            rgb = list(base)
            for (cx, cy), (rx, ry), col, a, soft in blobs:
                d = math.hypot((x - cx) / rx, (y - cy) / ry)
                if d < 1 + 3 * soft:
                    al = a / (1 + math.exp((d - 1) / (soft * 0.5)))
                    rgb = [v + (c - v) * al for v, c in zip(rgb, col)]
            for v in rgb:
                out[i] = max(0, min(255, int(v + rng.random())))
                i += 1
    Image.frombytes("RGB", (w, h), bytes(out)).save(path)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    hero()
    bar()
    menus()
    settings()
    settings_pages()
    for f in ("hero.png", "bar.png", "menus.png", "settings.png", "settings-pages.png"):
        p = OUT / f
        print(p.relative_to(ROOT), Image.open(p).size, p.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "wallpaper":
        wallpaper(sys.argv[2])
    else:
        main()
