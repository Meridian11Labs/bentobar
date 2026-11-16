#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Composes the README images from raw captures of a Googlebook.

Capture with a demo layout, so that no personal data shows (./bento debug cfg to save the user's, ./bento debug import
<base64> to load the demo and later restore theirs), and with everything that shows data staged: event stage demo,
weather stage clear, media stage playing, flight show air, network stage wifi (each turned off afterwards). Maximize
BentoBar's settings window behind the captures (the bar is then solid, so no wallpaper shows in it), on the Look page,
whose plain ground is what the menus' glass blurs; the Bar page's black preview would show through as a dark band.

Raw captures in RAW (default ~/.cache/bentobar/shots):
  hero.png                      a screenshot with one menu open, under an item of the Left area
  hero.txt                      that menu window's frame, "frame: x1 y1 x2 y2" (from dumpsys window)
  hero_win.png                  that menu window (./bento winshot "BentoBar menu" FILE): only its alpha is used, as
                                the card's shape; a window capture has the glass's veil but not the blur behind it
  bar_folded.png, bar_open.png  screenshots with hidden items behind ‹ (Look: Behind ‹, on click), folded and revealed
                                (./bento debug chevron right), with nothing in the Middle area
  menu_NAME.png, .txt, _win.png as hero, one menu each: weather, calendar, event, ctx, cpu, tools (the screenshot
                                must not have the mouse pointer inside the menu's frame: move the item or the pointer)
  app_bar.png                   the Bar page with an item's options (./bento winshot app FILE: no caption, no pointer)
  app_add.png                   the Bar page with Add items
  app_look.png                  the Look page

The crops are in the pixels of a 2560 × 1600 screen at 240 dpi, and everything is then scaled
to a 1920 px wide screen, so the images keep the size they always had. Writes docs/images/hero.png, bar.png, menus.png,
settings.png and settings-pages.png.

    python3 tools/readme_images.py [RAW]
"""
import pathlib
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageMath

ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path.home() / ".cache/bentobar/shots")
OUT = ROOT / "docs/images"

BAR_H = 54          # the status bar's height
HERO_RIGHT = 1500   # the hero shows the bar from the left edge to here: the clock, the Left area and the Middle one
BAR_LEFT = 990      # bar.png starts here: room for the Right area's hidden items when they are revealed
HEADER_TOP = 58     # in a settings window capture, where the header row begins (above it the caption window sits)
HEADER_END = 138    # and where the page below the header begins
PANEL = (1886, 316, 2530, 1432)  # the Bar page's panel (Add items) in a maximized window
LOOK = (690, HEADER_END, 1870, 1436)  # the Look page's column, to the end of its Hidden items card


def rounded(im, r):
    im = im.convert("RGBA")
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    im.putalpha(ImageChops.multiply(im.getchannel("A"), mask))
    return im


def shadowed(im, blur=18, offset=(0, 8), alpha=90, pad=40):
    """[im] on a transparent canvas with a soft drop shadow."""
    w, h = im.size
    canvas = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    a = im.getchannel("A").point(lambda v: v * alpha // 255)
    shadow.paste(Image.new("RGBA", im.size, (20, 20, 40, 255)), (pad + offset[0], pad + offset[1]), a)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))
    canvas.alpha_composite(im, (pad, pad))
    return canvas


def frame(name):
    return [int(v) for v in (RAW / f"{name}.txt").read_text().split(":")[1].split()]


def glass(name):
    """
    A menu as the screen showed it, glass and all, cut to the card (the window capture's alpha is the card's shape), and
    as it would be over a plain ground. On screen the glass is the window's own pixels (the window capture, which holds
    them premultiplied) plus the blurred background times what the glass lets through: the screen minus the window
    capture is that share of the background. Where the ground behind was plain, the share is the same everywhere (its most
    common value); where text behind it showed through as a grey smudge, it is lower. Each pixel's share is divided by its
    neighbourhood's (the background's level, which the window's own text can't raise) and multiplied by the plain one.
    """
    x1, y1, x2, y2 = frame(name)
    screen = Image.open(RAW / f"{name}.png").convert("RGB").crop((x1, y1, x2, y2))
    win = Image.open(RAW / f"{name}_win.png").convert("RGBA")
    own = win.convert("RGB")
    share = ImageChops.subtract(screen, own)
    inside = win.getchannel("A").point(lambda v: 255 if v > 250 else 0)
    bands = []
    for p, s in zip(own.split(), share.split()):
        hist = s.histogram(inside)
        plain = max(range(256), key=lambda v: hist[v])
        # Outside the card (and on its rim) the screen is no share of anything: the level there is taken as plain. A share
        # above the plain one (a white card behind, or a live number or chart that changed between the two captures) is
        # left as the screen had it.
        level = Image.composite(s.point(lambda v: min(v, plain)), Image.new("L", s.size, plain), inside)
        l = level.filter(ImageFilter.MaxFilter(15)).filter(ImageFilter.GaussianBlur(6))
        bands.append(ImageMath.lambda_eval(
            lambda a: a["convert"](a["p"] + a["s"] * a["min"](a["max"](plain / a["max"](a["l"], 1.0), 0.8), 1.3), "L"),
            p=p.convert("F"), s=s.convert("F"), l=l.convert("F")))
    card = Image.merge("RGB", bands).convert("RGBA")
    card.putalpha(win.getchannel("A"))
    return card


SCALE = 1920 / 2560


def scaled(im):
    return im.resize((round(im.width * SCALE), round(im.height * SCALE)), Image.LANCZOS)


def save(im, name):
    im.save(OUT / name, optimize=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    # Hero: the bar from the clock to the Middle area, with the Weather menu open under its item. Below the bar, the
    # maximized window's plain ground (as the screen had it beside the menu) instead of the window's header and page.
    shot = Image.open(RAW / "hero.png").convert("RGBA")
    x1, y1, x2, y2 = frame("hero")
    height = y2 + 90
    hero = Image.new("RGBA", (HERO_RIGHT, height), shot.getpixel((30, shot.height - 300)))
    hero.alpha_composite(shot.crop((0, 0, HERO_RIGHT, BAR_H)), (0, 0))
    card = glass("hero")
    pad = 40
    hero.alpha_composite(shadowed(card, blur=22, offset=(0, 10), alpha=70, pad=pad), (x1 - pad, y1 - pad))
    save(rounded(scaled(hero), 18), "hero.png")

    # The bar folded and with its hidden items revealed.
    rows = [Image.open(RAW / n).convert("RGBA").crop((BAR_LEFT, 0, 2560, BAR_H)) for n in ("bar_folded.png", "bar_open.png")]
    rows = [rounded(scaled(r), 10) for r in rows]
    gap = 10
    both = Image.new("RGBA", (rows[0].width, rows[0].height * 2 + gap), (0, 0, 0, 0))
    both.alpha_composite(rows[0], (0, 0))
    both.alpha_composite(rows[1], (0, rows[0].height + gap))
    save(both, "bar.png")

    # Menus in columns: Weather, the month, Next meeting over the right-click menu, CPU, Tools.
    columns = [["menu_weather"], ["menu_calendar"], ["menu_event", "menu_ctx"], ["menu_cpu"], ["menu_tools"]]
    columns = [[scaled(glass(n)) for n in col if (RAW / f"{n}.png").exists()] for col in columns]
    columns = [c for c in columns if c]
    pad, gap = 30, 24
    W = sum(max(c.width for c in col) for col in columns) + gap * (len(columns) - 1) + 2 * pad
    H = max(sum(c.height for c in col) + gap * (len(col) - 1) for col in columns) + 2 * pad
    sheet = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    x = pad
    for col in columns:
        y = pad
        for c in col:
            sheet.alpha_composite(shadowed(c, blur=14, offset=(0, 6), alpha=60, pad=pad), (x - pad, y - pad))
            y += c.height + gap
        x += max(c.width for c in col) + gap
    save(sheet, "menus.png")

    # Settings: the Bar page with an item's options, the whole window below the caption.
    app = Image.open(RAW / "app_bar.png").convert("RGBA")
    page = rounded(scaled(app.crop((0, HEADER_TOP, app.width, app.height))), 16)
    save(shadowed(page, blur=20, alpha=70, pad=36), "settings.png")

    # Adding items (the Bar page's panel) and the Look page's column, side by side at one height.
    add = Image.open(RAW / "app_add.png").convert("RGBA").crop(PANEL)
    look = Image.open(RAW / "app_look.png").convert("RGBA").crop(LOOK)
    h = round(1060 * SCALE)
    parts = [rounded(p.resize((round(p.width * h / p.height), h), Image.LANCZOS), 14) for p in (add, look)]
    pad, gap = 28, 28
    combo = Image.new("RGBA", (sum(p.width for p in parts) + gap + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    x = 0
    for p in parts:
        combo.alpha_composite(shadowed(p, blur=12, alpha=60, pad=pad), (x, 0))
        x += p.width + gap
    save(combo, "settings-pages.png")

    for f in ("hero.png", "bar.png", "menus.png", "settings.png", "settings-pages.png"):
        p = OUT / f
        print(p.relative_to(ROOT), Image.open(p).size, p.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
