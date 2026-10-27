"""Nation soldiers (mod): the game's soldier and dragoon pictures dressed
as around 1600-1660, one look per nation (see the gallery "Soldiers of
the eight crowns").  The colonist is militia: his own plain clothes, a
felt hat and the nation's sash.  The veteran wears the nation's kit.
Works on the large (size2) picture and halves it for the small one.
Reads the game's own images, writes new ones; run from the repo root.

    python claude_docs/tools/nation_art.py
        data/default/resources/images/units/nation [preview.png [dutch,...]]

The game picks them through image keys ending in the nation
("image.unit.model.unit.<type>.soldier.dutch", see
ImageLibrary.getUnitTypeImageKey), listed in resources.properties.
"""
import colorsys
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

U = 'data/default/resources/images/units/'
SS = 8                # supersampling for drawn parts
SOFT = 0.4            # blur on drawn parts, in pixels
GRAIN = 0.06          # brightness grain on drawn parts


def hsv(p):
    r, g, b = p[:3]
    return colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)


def brown(p):
    h, s, v = hsv(p)
    return p[3] >= 128 and 0.03 < h < 0.14 and s > 0.35 and v < 0.55


def grey(p):
    """Light grey shading (old highlights and edges), not the outline."""
    h, s, v = hsv(p)
    return p[3] >= 128 and s < 0.15 and 0.4 < v < 0.9


# The two figures, in size2 pixels.  Hats, hair and collar are drawn for
# the man on foot and moved by "head" for the rider.
FOOT = dict(
    dir='soldier', size=(66, 128), head=(0, 0),
    musket=((12, 84), (60, 4)), stock=(24, 55),
    clear=(46, 12, 10),           # hat area: x below, y below, skin from
    hands=30,                     # rows below the face
    sash=((20, 24), (45, 60)), knot=(0, 0),
    belts=(((20, 23), (46, 61)), ((39, 23), (19, 60))),
    hose=(86, 112),
    # brown breeches (colonist picture), with their grey edges below the
    # shirt tails; blue breeches (veteran picture)
    breeches=lambda p, x, y: 70 <= y <= 93 and x >= 24 and (
        brown(p) or (y >= 78 and grey(p))),
    vet_breeches=lambda p, x, y: coat(p) and y >= 72,
    saddle=lambda p, x, y: False)

RIDER = dict(
    dir='dragoon', size=(108, 164), head=(20, 6),
    musket=((22, 86), (75, 7)), stock=(36, 60),
    clear=(62, 18, 15),
    hands=38,
    sash=((34, 33), (55, 66)), knot=(8, 6),
    belts=(((34, 32), (60, 71)), ((55, 31), (31, 68))),
    hose=(88, 116),
    # the thigh only: the horse around it is brown too
    breeches=lambda p, x, y: (73 <= y <= 96 and 32 <= x <= 49 - (y - 73) * 0.2
                              and (brown(p) or (y >= 80 and grey(p)))
                              and not near_musket(x, y, 4)),
    vet_breeches=lambda p, x, y: (coat(p) and 76 <= y <= 97
                                  and 36 <= x <= 62 and hsv(p)[2] > 0.33),
    # the blue saddle cloth, left of and under the rider
    saddle=lambda p, x, y: (y >= 57 and x <= 31) or y >= 76)

F = FOOT              # the figure being dressed


def coat(p):
    """The coat (and the veteran's breeches): blue-grey cloth."""
    if p[3] < 128:
        return False
    h, s, v = hsv(p)
    if 0.5 <= h <= 0.75 and s >= 0.06 and v >= 0.2:
        return True
    # its pale highlights: cool, not the warm shirt or the grey barrel
    return v >= 0.5 and p[2] >= p[0] + 4


def cloth(p, x, y):
    """The rider's coat, not the saddle cloth."""
    return coat(p) and not F['saddle'](p, x, y)


def shirt(p):
    h, s, v = hsv(p)
    return p[3] >= 128 and s < 0.1 and v > 0.8


def skin(p):
    h, s, v = hsv(p)
    return p[3] >= 128 and h < 0.12 and 0.3 < s and v > 0.75


def stockings(p, x, y):
    h, s, v = hsv(p)
    y0, y1 = F['hose']
    # the white and its grey shading (not the dark outline)
    return p[3] >= 128 and y0 <= y <= y1 and s < 0.15 and v > 0.4


def near_musket(x, y, d):
    (x0, y0), (x1, y1) = F['musket']
    dx, dy = x1 - x0, y1 - y0
    n = (dx * dx + dy * dy) ** 0.5
    return abs((x - x0) * dy - (y - y0) * dx) / n <= d


def on_top(base, x, y):
    """What stays in front of anything we paint: musket and hands."""
    p = base.getpixel((x, y))
    if p[3] < 128:
        return False
    if skin(p) or near_musket(x, y, 2.5):
        return True
    # the wide stock near the butt
    h, s, v = hsv(p)
    sx, sy = F['stock']
    return (x < sx and y > sy and near_musket(x, y, 6.5)
            and 0.04 < h < 0.14 and s > 0.45 and v > 0.4)


def ramp(color, v):
    """A colour at the lightness of v (0..1), keeping the folds."""
    r, g, b = color
    t = 0.35 + 0.72 * v
    if t <= 1:
        return (int(r * t), int(g * t), int(b * t), 255)
    t = t - 1
    return (int(r + (255 - r) * t), int(g + (255 - g) * t),
            int(b + (255 - b) * t), 255)


def shade(color, f):
    return tuple(max(0, min(255, int(c * f))) for c in color) + (255,)


def recolor(im, test, color, lift=0.0, base=None):
    """Every pixel passing test(p, x, y) to the colour, same lightness
    (tested on the base picture's pixel, if given)."""
    px = im.load()
    tx = base.load() if base else px
    pts = [(x, y) for y in range(im.height) for x in range(im.width)
           if test(tx[x, y], x, y)]
    if not pts:
        return
    vs = [hsv(px[x, y])[2] for x, y in pts]
    lo, hi = min(vs), max(vs)
    for (x, y), v in zip(pts, vs):
        t = 0 if hi <= lo else (v - lo) / (hi - lo)
        a = px[x, y][3]
        c = ramp(color, min(1.0, t + lift))
        px[x, y] = c[:3] + (a,)


def drawn(paint, off=(0, 0)):
    """Draw at SS times the size, moved by off, then shrink."""
    w, h = F['size']
    big = Image.new('RGBA', (w * SS, h * SS), (0, 0, 0, 0))
    ox, oy = off
    paint(ImageDraw.Draw(big), lambda *v: tuple(
        int((c + (ox if i % 2 == 0 else oy)) * SS) for i, c in enumerate(v)))
    small = big.convert('RGBa').resize((w, h), Image.BOX)
    # The game's figures are soft and painterly: no razor edges, and a
    # little grain in the cloth and steel.
    out = small.filter(ImageFilter.GaussianBlur(SOFT)).convert('RGBA')
    px, rnd = out.load(), random.Random(h * 7919 + len(paint.__name__))
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a:
                f = 1 + rnd.uniform(-GRAIN, GRAIN)
                px[x, y] = (min(255, int(r * f)), min(255, int(g * f)),
                            min(255, int(b * f)), a)
    return out


def band(im, base, color, a, b, width, second=None):
    """A belt or sash along a-b, over cloth only (under musket, hands);
    with a second colour, its lower half in that colour (striped)."""
    px = im.load()
    (x0, y0), (x1, y1) = a, b
    dx, dy = x1 - x0, y1 - y0
    n = (dx * dx + dy * dy) ** 0.5
    for y in range(im.height):
        for x in range(im.width):
            p = base.getpixel((x, y))
            if (not (cloth(p, x, y) or shirt(p))
                    or on_top(base, x, y)):
                continue
            t = ((x - x0) * dx + (y - y0) * dy) / (n * n)
            sd = ((x - x0) * dy - (y - y0) * dx) / n
            d = abs(sd)
            if -0.05 <= t <= 1.05 and d <= width:
                v = hsv(p)[2]
                edge = d > width - 0.8
                c = second if (second and sd > 0) else color
                px[x, y] = ramp(c, (0.45 + 0.55 * v) * (0.8 if edge else 1))


def knot(im, color, second=None):
    """The sash's knot at the left hip, two short tails hanging (the
    second in the second colour, if any)."""
    dark = shade(color, 0.45)
    back = second or tuple(int(c * 0.8) for c in color)
    full = color + (255,)

    def paint_knot(d, s):
        d.ellipse(s(42.2, 58.2, 47.6, 63.4), fill=dark)
        d.ellipse(s(42.8, 58.8, 47.0, 62.6), fill=full)
        d.polygon([s(43, 62), s(45.4, 62), s(44.6, 71), s(42.6, 70)],
                  fill=dark)
        d.polygon([s(43.5, 62), s(45, 62), s(44.2, 70.2), s(43.1, 69.6)],
                  fill=full)
        d.polygon([s(45.2, 62), s(47.4, 61.6), s(48.2, 68), s(46.4, 68.6)],
                  fill=shade(back, 0.5))
        d.polygon([s(45.6, 62.2), s(47, 62), s(47.6, 67.4), s(46.6, 67.8)],
                  fill=back + (255,))
    im.alpha_composite(drawn(paint_knot, F['knot']))


def cuffs(im, base, color):
    """Turned-back cuffs: the sleeve's end, the cloth next to the hands."""
    px = im.load()
    hands = [(x, y) for y in range(F['hands'], im.height)
             for x in range(im.width) if skin(base.getpixel((x, y)))]
    for y in range(F['hands'], im.height):
        for x in range(im.width):
            p = base.getpixel((x, y))
            if not cloth(p, x, y) or on_top(base, x, y):
                continue
            if any(abs(x - hx) <= 3 and abs(y - hy) <= 3 for hx, hy in hands):
                px[x, y] = ramp(color, 0.25 + 0.75 * hsv(p)[2])


def crossbelts(im, base, color):
    """Two belts crossed on the chest: bandolier and sword belt."""
    for a, b in F['belts']:
        band(im, base, color, a, b, 2.0)


def no_flecks(im, base, size=10):
    """Small islands of near-white left inside recoloured cloth (the old
    coat's and stockings' brightest highlights, too pale for the cloth
    tests) take the colour of the cloth around them, a little lighter.
    The shirt and collar are large and stay."""
    px, bx = im.load(), base.load()
    w, h = im.size

    def pale(x, y):
        p = px[x, y]
        hh, ss, vv = hsv(p)
        return p[3] >= 64 and p == bx[x, y] and vv > 0.62 and ss < 0.22

    seen = set()
    for y0 in range(h):
        for x0 in range(w):
            if (x0, y0) in seen or not pale(x0, y0):
                continue
            comp, todo = [], [(x0, y0)]
            seen.add((x0, y0))
            while todo:
                x, y = todo.pop()
                comp.append((x, y))
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        q = (x + dx, y + dy)
                        if (0 <= q[0] < w and 0 <= q[1] < h
                                and q not in seen and pale(*q)):
                            seen.add(q)
                            todo.append(q)
            if len(comp) > 3 * size:
                continue
            ring = {(x + dx, y + dy) for x, y in comp
                    for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - set(comp)
            ring = [q for q in ring if 0 <= q[0] < w and 0 <= q[1] < h
                    and px[q][3] >= 128]
            changed = [px[q] for q in ring if px[q] != bx[q]]
            # a small fleck mostly in cloth, or a longer sliver (a pale
            # edge) almost all round
            need = 0.5 if len(comp) <= size else 0.8
            if not ring or len(changed) < need * len(ring):
                continue
            c = [min(255, int(sum(p[i] for p in changed) / len(changed) * 1.12))
                 for i in range(3)]
            for q in comp:
                px[q] = tuple(c) + (px[q][3],)


def collar(im, base, color=(236, 234, 226)):
    """A plain falling collar (or cravat) over the coat's neck."""
    px = im.load()
    hx, hy = F['head']
    for y in range(20 + hy, 26 + hy):
        for x in range(21 + hx, 38 + hx):
            p = base.getpixel((x, y))
            if coat(p) and abs(x - 29 - hx) <= 9 - (y - 20 - hy):
                v = hsv(p)[2]
                px[x, y] = ramp(color, 0.7 + 0.3 * v)


def clear_hat(im):
    """Take off the tricorne (keep the musket's muzzle on the right)."""
    px = im.load()
    xmax, ymax, skin_from = F['clear']
    for y in range(0, ymax):
        for x in range(0, xmax):
            p = px[x, y]
            if y >= skin_from and skin(p):
                continue
            px[x, y] = (0, 0, 0, 0)


def hair(im, color, length):
    """Long hair behind the head, falling to the collar."""
    dark = shade(color, 0.6)

    def paint(d, s):
        d.polygon([s(21, 8), s(36, 8), s(37.5, 9 + length), s(34.5, 11 + length),
                   s(29, 12), s(23, 11 + length), s(19.5, 9 + length)],
                  fill=dark)
        d.polygon([s(21.8, 8.5), s(35.2, 8.5), s(36.6, 8 + length),
                   s(34.5, 10 + length), s(29, 12), s(23, 10 + length),
                   s(20.5, 8 + length)], fill=color + (255,))
    layer = drawn(paint, F['head'])
    layer.alpha_composite(im)   # behind the head
    im.paste(layer, (0, 0))


# Hats.  Each draws for the man on foot (66 x 128) through s().

def morion(d, s):
    """Steel morion: a round steel dome, a thin high comb arching over
    it, a boat-shaped brim with its points turned up."""
    out, dark, mid = (38, 40, 46, 255), (72, 76, 86, 255), (108, 114, 126, 255)
    light, shine = (142, 148, 160, 255), (172, 178, 190, 255)
    # the comb: a thin crescent fin standing on the dome
    d.chord(s(22.6, 0.2, 33.4, 13), 180, 360, fill=out)
    d.chord(s(23.6, 1.0, 32.4, 13), 180, 360, fill=mid)
    d.arc(s(23.6, 1.0, 32.4, 13), 200, 300, fill=shine, width=SS // 2)
    # the dome, a half ellipse
    d.chord(s(19.6, 3.6, 36.4, 20), 180, 360, fill=out)
    d.chord(s(20.6, 4.6, 35.4, 19.4), 180, 360, fill=mid)
    d.chord(s(27.5, 5.6, 35, 18.4), 270, 360, fill=dark)
    d.chord(s(22, 5.4, 28.5, 16), 180, 270, fill=light)
    d.ellipse(s(23.2, 6.6, 25.4, 8.6), fill=shine)
    # the comb again in front of the dome's top edge
    d.rectangle(s(26.6, 3.4, 29.4, 6.4), fill=mid)
    d.line([s(26.6, 3.6), s(26.6, 6.4)], fill=out, width=SS // 2)
    d.line([s(29.4, 3.6), s(29.4, 6.4)], fill=out, width=SS // 2)
    # brim: one boat shape, the points turned up
    d.polygon([s(15, 8.2), s(19.2, 10.8), s(28, 11.6), s(36.8, 10.8),
               s(41.2, 7.8), s(41, 10.6), s(37.4, 14), s(28, 14.8),
               s(18.6, 14), s(15.2, 10.8)], fill=out)
    d.polygon([s(16.2, 9.4), s(19.4, 11.8), s(28, 12.6), s(36.6, 11.8),
               s(40, 9.2), s(37, 13.2), s(28, 13.8), s(19, 13.2)], fill=mid)
    d.line([s(17, 10.6), s(19.4, 12), s(28, 12.8), s(36.6, 12),
            s(39.4, 10.4)], fill=light, width=SS // 2)
    for x in (22.6, 33.4):
        d.ellipse(s(x - 0.6, 9.2, x + 0.6, 10.4), fill=shine)


def plumed_hat(d, s):
    felt, band, edge = (38, 36, 44, 255), (30, 50, 150, 255), (12, 12, 16, 255)
    d.ellipse(s(10, 3, 25, 10), fill=(120, 118, 112, 255))
    d.ellipse(s(10.6, 3.4, 24.6, 9.4), fill=(244, 242, 236, 255))
    d.ellipse(s(8.5, 6, 16, 12), fill=(226, 224, 218, 255))
    d.ellipse(s(13, 1.5, 22, 6.5), fill=(255, 255, 255, 255))
    d.rounded_rectangle(s(21.5, 2, 35, 11), radius=2 * SS, fill=edge)
    d.rounded_rectangle(s(22.3, 2.7, 34.2, 11), radius=2 * SS, fill=felt)
    d.rectangle(s(22.3, 8, 34.2, 10), fill=band)
    d.line([s(24, 4), s(24, 9)], fill=(70, 68, 80, 255), width=SS)
    d.ellipse(s(12, 8.6, 45, 14.4), fill=edge)
    d.ellipse(s(12.8, 9.2, 44.2, 13.4), fill=felt)
    d.ellipse(s(18, 9.4, 40, 11.6), fill=(64, 62, 72, 255))


def round_hat(d, s):
    """Wentworth's musketeer: a wide black hat, low round crown, the
    brim turned up a little at the back."""
    felt, edge = (34, 32, 36, 255), (8, 8, 10, 255)
    hi = (72, 70, 78, 255)
    d.ellipse(s(21.5, 1.5, 35.5, 13), fill=edge)
    d.ellipse(s(22.3, 2.3, 34.7, 12.5), fill=felt)
    d.ellipse(s(23.5, 3, 28, 8), fill=hi)
    d.polygon([s(11.5, 8), s(16, 9), s(28, 10.5), s(40, 9.4), s(45, 11.2),
               s(41, 14.4), s(28, 15), s(15, 13.4)], fill=edge)
    d.polygon([s(12.8, 8.8), s(16, 9.8), s(28, 11.2), s(40, 10.2), s(43.6, 11.6),
               s(40.4, 13.6), s(28, 14.2), s(15.4, 12.6)], fill=felt)
    d.line([s(15, 10.6), s(28, 12.2), s(41, 11)], fill=hi, width=SS // 2)


def felt_hat(d, s):
    """The militia's own hat: soft brown felt, floppy brim."""
    felt, edge, hi = (112, 82, 54, 255), (46, 32, 20, 255), (150, 116, 80, 255)
    d.rounded_rectangle(s(22, 3.5, 35, 11.5), radius=3 * SS, fill=edge)
    d.rounded_rectangle(s(22.8, 4.2, 34.2, 11.5), radius=3 * SS, fill=felt)
    d.line([s(25, 5.5), s(25, 10)], fill=hi, width=SS)
    d.rectangle(s(22.8, 9, 34.2, 10.4), fill=(70, 50, 32, 255))
    d.polygon([s(14, 11.6), s(18, 9.4), s(28, 9.8), s(39, 9.4), s(43, 11.8),
               s(40, 14), s(28, 13.8), s(17, 14)], fill=edge)
    d.polygon([s(15.2, 11.6), s(18.4, 10.2), s(28, 10.6), s(38.6, 10.2),
               s(41.8, 11.8), s(39.4, 13.2), s(28, 13), s(17.4, 13.2)],
              fill=felt)


def cabasset(d, s):
    """Spanish cabasset: a tall almond-shaped steel helmet with a narrow
    flat brim and a little knob on top."""
    out, dark, mid = (38, 40, 46, 255), (72, 76, 86, 255), (108, 114, 126, 255)
    light, shine = (142, 148, 160, 255), (172, 178, 190, 255)
    bowl = [(21.4, 11.5), (21.6, 6.4), (23.4, 2.6), (26.4, 0.9), (29.6, 0.9),
            (32.6, 2.6), (34.4, 6.4), (34.6, 11.5)]
    d.polygon([s(*p) for p in bowl], fill=out)
    d.polygon([s(x + (0.9 if x < 28 else -0.9), y + (0.8 if y < 11 else 0))
               for x, y in bowl], fill=mid)
    d.polygon([s(28.6, 1.8), s(31.8, 3.2), s(33.4, 6.6), s(33.6, 11.2),
               s(29.6, 11.2)], fill=dark)
    d.polygon([s(23, 10.6), s(23.2, 6.6), s(24.6, 3.6), s(26.4, 2.4),
               s(25.6, 6), s(25.4, 10.6)], fill=light)
    d.ellipse(s(24, 3.6, 25.4, 5.4), fill=shine)
    d.ellipse(s(26.8, -0.4, 29.2, 1.8), fill=out)
    d.ellipse(s(27.3, 0, 28.7, 1.3), fill=light)
    # the narrow brim
    d.ellipse(s(18, 9.4, 38, 14.2), fill=out)
    d.ellipse(s(18.9, 10, 37.1, 13.4), fill=mid)
    d.polygon([s(21.4, 11.2), s(34.6, 11.2), s(34.6, 9.6), s(21.4, 9.6)],
              fill=mid)
    d.line([s(20, 12.4), s(28, 13.2), s(36, 12.4)], fill=light, width=SS // 2)


def wide_hat(felt, band=None, plume=None):
    """A wide felt hat with a round crown, a band and a plume if given."""
    edge = shade(felt, 0.35)
    hi = shade(felt, 1.6)

    def hat(d, s):
        if plume:
            d.ellipse(s(10, 3, 25, 10), fill=shade(plume, 0.5))
            d.ellipse(s(10.6, 3.4, 24.6, 9.4), fill=plume + (255,))
            d.ellipse(s(8.5, 6, 16, 12), fill=shade(plume, 0.9))
            d.ellipse(s(13, 1.5, 22, 6.5), fill=shade(plume, 1.1))
        d.ellipse(s(21.5, 1.5, 35.5, 13), fill=edge)
        d.ellipse(s(22.3, 2.3, 34.7, 12.5), fill=felt + (255,))
        d.ellipse(s(23.5, 3, 28, 8), fill=hi)
        if band:
            d.rectangle(s(22.4, 8, 34.6, 10), fill=band + (255,))
        d.polygon([s(11.5, 8.6), s(16, 9.4), s(28, 10.6), s(40, 9.4),
                   s(45, 10.8), s(41, 14.4), s(28, 15), s(15, 13.6)], fill=edge)
        d.polygon([s(12.8, 9.2), s(16, 10.2), s(28, 11.3), s(40, 10.2),
                   s(43.6, 11.3), s(40.4, 13.6), s(28, 14.2), s(15.4, 12.8)],
                  fill=felt + (255,))
        d.line([s(15, 10.8), s(28, 12.2), s(41, 11)], fill=hi, width=SS // 2)
    return hat


def fur_cap(cloth_color, fur):
    """The strelets' cap: a tall rounded crown, a thick fur band."""
    def cap(d, s):
        d.rounded_rectangle(s(22.4, 0.4, 34, 11), radius=5 * SS,
                            fill=shade(cloth_color, 0.4))
        d.rounded_rectangle(s(23.2, 1.2, 33.2, 11), radius=int(4.4 * SS),
                            fill=cloth_color + (255,))
        d.rounded_rectangle(s(24.4, 2.2, 27.4, 9), radius=int(1.4 * SS),
                            fill=shade(cloth_color, 1.35))
        d.rounded_rectangle(s(20.2, 7.6, 36.4, 14), radius=3 * SS,
                            fill=shade(fur, 0.45))
        d.rounded_rectangle(s(21, 8.3, 35.6, 13.3), radius=int(2.6 * SS),
                            fill=fur + (255,))
        rnd = random.Random(7)
        for _ in range(26):
            x, y = rnd.uniform(21.5, 35), rnd.uniform(8.6, 12.8)
            d.ellipse(s(x - 0.5, y - 0.5, x + 0.5, y + 0.5),
                      fill=shade(fur, rnd.choice((0.6, 1.3))))
    return cap


# coat, breeches, stockings: colours; legs: 'boots' (the veteran
# picture's) or 'hose' (stockings and shoes, the colonist picture's).
LOOKS = {
    # Militia: own clothes in drab colours, the nation's sash.
    ('dutch', 'colonist'): dict(coat=(70, 68, 66), breeches=(92, 70, 48),
                                sash=(236, 136, 38), hat=felt_hat),
    ('french', 'colonist'): dict(coat=(96, 86, 72), breeches=(92, 70, 48),
                                 sash=(244, 242, 236), hat=felt_hat),
    ('english', 'colonist'): dict(coat=(128, 96, 62), breeches=(92, 70, 48),
                                  sash=(196, 30, 36), hat=felt_hat),
    # Veterans: the nation's kit.
    ('dutch', 'veteran'): dict(
        legs='hose', coat=(84, 96, 110), breeches=(178, 52, 34),
        stockings=(196, 84, 52), sash=(236, 136, 38), hat=morion),
    ('french', 'veteran'): dict(
        legs='boots', coat=(46, 70, 168), breeches=(92, 88, 96),
        sash=(244, 242, 236), hat=plumed_hat, hair=((92, 62, 36), 13)),
    ('english', 'veteran'): dict(
        legs='hose', coat=(176, 34, 34), breeches=(206, 202, 190),
        belts=(226, 180, 64), hat=round_hat, hair=((70, 46, 28), 13)),

    # Spain: Spanish black, the red sash with gold; a tercio's cabasset.
    ('spanish', 'colonist'): dict(coat=(92, 80, 64), breeches=(92, 70, 48),
                                  sash=((182, 32, 30), (214, 168, 40)),
                                  hat=felt_hat),
    ('spanish', 'veteran'): dict(
        legs='hose', coat=(56, 54, 60), breeches=(70, 60, 52),
        stockings=(206, 172, 64), sash=((182, 32, 30), (214, 168, 40)),
        hat=cabasset),
    # Portugal: dressed like Spain until 1640; green sash, buff coat.
    ('portuguese', 'colonist'): dict(coat=(104, 92, 70), breeches=(92, 70, 48),
                                     sash=(44, 128, 62), hat=felt_hat),
    ('portuguese', 'veteran'): dict(
        legs='hose', coat=(192, 156, 100), breeches=(52, 70, 54),
        sash=(44, 128, 62), hat=wide_hat((44, 38, 32), band=(44, 128, 62)),
        hair=((62, 40, 26), 10)),
    # Sweden: the Yellow Regiment, a blue sash, grey hat, boots.
    ('swedish', 'colonist'): dict(coat=(90, 90, 86), breeches=(92, 70, 48),
                                  sash=(56, 120, 200), hat=felt_hat),
    ('swedish', 'veteran'): dict(
        legs='boots', coat=(204, 164, 48), breeches=(70, 80, 104),
        sash=(46, 86, 170), hat=wide_hat((88, 88, 94), band=(46, 86, 170)),
        hair=((150, 110, 60), 11)),
    # Denmark: Christian IV's blue and flame-yellow sash for the
    # militia; the 1660 grey coat with red cuffs for the veteran.
    ('danish', 'colonist'): dict(coat=(84, 80, 74), breeches=(92, 70, 48),
                                 sash=((44, 78, 160), (232, 158, 30)),
                                 hat=felt_hat),
    ('danish', 'veteran'): dict(
        legs='hose', coat=(146, 146, 142), cuffs=(176, 36, 36),
        breeches=(54, 52, 56), belts=(200, 162, 100),
        hat=wide_hat((30, 28, 30)), hair=((110, 76, 44), 12)),
    # Russia: the streltsy's long kaftan and fur cap, no sash.
    ('russian', 'colonist'): dict(coat=(112, 86, 62), breeches=(92, 70, 48),
                                  collar=False,
                                  hat=fur_cap((96, 74, 56), (70, 50, 34))),
    ('russian', 'veteran'): dict(
        legs='boots', coat=(48, 100, 56), breeches=(132, 40, 40),
        collar=False, hat=fur_cap((170, 40, 40), (92, 64, 40))),
}

# Output file names: (figure, rank) -> name, and the game picture used.
FILES = {(FOOT['dir'], 'colonist'): ('soldier', 'soldier'),
         (FOOT['dir'], 'veteran'): ('veteranSoldier', 'veteranSoldier'),
         (RIDER['dir'], 'colonist'): ('dragoon', 'dragoon'),
         (RIDER['dir'], 'veteran'): ('veteranDragoon', 'veteranSoldier')}


def sash_colors(look):
    """The sash's colour and second colour (for a two-coloured sash)."""
    c = look.get('sash')
    if not c:
        return None, None
    return (c[0], c[1]) if isinstance(c[0], tuple) else (c, None)


def dress(figure, nation, rank):
    global F
    F = figure
    look = LOOKS[(nation, rank)]
    boots = look.get('legs') == 'boots'
    picture = ('veteranSoldier' if boots
               else 'soldier' if F is FOOT else 'dragoon')
    base = Image.open(U + '%s/%s.size2.png' % (F['dir'], picture)
                      ).convert('RGBA')
    im = base.copy()
    recolor(im, cloth, look['coat'])
    if look.get('breeches'):
        recolor(im, F['vet_breeches'] if boots else F['breeches'],
                look['breeches'], base=base)
    if look.get('stockings'):
        recolor(im, stockings, look['stockings'], lift=0.15)
    sash, second = sash_colors(look)
    if sash:
        band(im, base, sash, *F['sash'], 2.4, second)
    if look.get('cuffs'):
        cuffs(im, base, look['cuffs'])
    if look.get('belts'):
        crossbelts(im, base, look['belts'])
    no_flecks(im, base)
    if look.get('collar', True):
        collar(im, base)
    if sash:
        knot(im, sash, second)
    clear_hat(im)
    if look.get('hair'):
        hair(im, *look['hair'])
    im.alpha_composite(drawn(look['hat'], F['head']))
    return im


def half(im):
    """The small picture: half size, smooth (premultiplied alpha)."""
    return (im.convert('RGBa').resize((im.width // 2, im.height // 2),
                                      Image.LANCZOS).convert('RGBA'))


def main():
    out = sys.argv[1]
    for figure in (FOOT, RIDER):
        for nation, rank in LOOKS:
            big = dress(figure, nation, rank)
            name = FILES[(figure['dir'], rank)][0]
            stem = os.path.join(out, nation, name)
            os.makedirs(os.path.dirname(stem), exist_ok=True)
            big.save(stem + '.size2.png')
            half(big).save(stem + '.png')
    if len(sys.argv) > 2:
        nations = (sys.argv[3].split(',') if len(sys.argv) > 3
                   else sorted({n for n, _ in LOOKS}))
        preview(out, sys.argv[2], nations)


def preview(out, path, nations):
    """The game's own, then each nation's colonist and veteran, on foot
    and mounted, big and on a grass tile at map size."""
    from PIL import ImageFont
    tile = Image.open('data/default/resources/images/terrain/grassland/'
                      'center0.png').convert('RGBA')
    try:
        font = ImageFont.truetype('georgia.ttf', 22)
        small = ImageFont.truetype('georgia.ttf', 15)
    except OSError:
        font = small = ImageFont.load_default()
    cols = [('Game now (1770s)', None)] + [
        (n.capitalize(), n) for n in nations]
    Z, TS = 2, 1.6
    tw, th = int(tile.width * TS), int(tile.height * TS)
    cw = max(2 * 108 * Z + 30, 2 * tw + 30)
    rows = [(FOOT, 128 * Z), (RIDER, 164 * Z)]
    height = 60 + sum(h + 30 + th + 30 for _, h in rows)
    sheet = Image.new('RGBA', (len(cols) * cw + 20, height),
                      (236, 226, 200, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, n) in enumerate(cols):
        x = 20 + i * cw
        d.text((x, 20), name, font=font, fill=(60, 38, 20))
        y = 60
        for figure, h in rows:
            for j, rank in enumerate(('colonist', 'veteran')):
                mine, game = FILES[(figure['dir'], rank)]
                f = (U + '%s/%s' % (figure['dir'], game) if n is None
                     else os.path.join(out, n, mine))
                big = Image.open(f + '.size2.png').convert('RGBA')
                sheet.alpha_composite(big.resize(
                    (big.width * Z, big.height * Z), Image.NEAREST),
                    (x + j * (108 * Z + 10), y))
                d.text((x + j * (108 * Z + 10) + 40, y + h + 2), rank,
                       font=small, fill=(110, 80, 50))
                s = Image.open(f + '.png').convert('RGBA')
                t = tile.copy()
                t.alpha_composite(s, ((t.width - s.width) // 2,
                                      max(0, t.height - s.height - 4)))
                sheet.alpha_composite(t.resize((tw, th), Image.NEAREST),
                                      (x + j * (tw + 12), y + h + 26))
            y += h + 30 + th + 30
    sheet.save(path)


main()
