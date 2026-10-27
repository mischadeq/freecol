"""Nation soldiers (mod): the game's soldier pictures dressed as around
1600-1660, one look per nation (see the gallery "Soldiers of the eight
crowns").  The colonist soldier is militia: his own plain clothes, a
felt hat and the nation's sash.  The veteran wears the nation's kit.
Works on the large (size2) picture and halves it for the small one.
Reads the game's own images, writes new ones; run from the repo root.

    python claude_docs/tools/nation_art.py         data/default/resources/images/units/nation [preview.png]

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
WAIST = 61            # size2 rows: the belt
SS = 8                # supersampling for drawn parts
SOFT = 0.4            # blur on drawn parts, in pixels
GRAIN = 0.06          # brightness grain on drawn parts
MUSKET = ((12, 84), (60, 4))   # size2: butt to muzzle


def hsv(p):
    r, g, b = p[:3]
    return colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)


def coat(p):
    """The coat (and the veteran's breeches): blue-grey cloth."""
    if p[3] < 128:
        return False
    h, s, v = hsv(p)
    if 0.5 <= h <= 0.75 and s >= 0.06 and v >= 0.2:
        return True
    # its pale highlights: cool, not the warm shirt or the grey barrel
    return v >= 0.5 and p[2] >= p[0] + 4


def shirt(p):
    h, s, v = hsv(p)
    return p[3] >= 128 and s < 0.1 and v > 0.8


def skin(p):
    h, s, v = hsv(p)
    return p[3] >= 128 and h < 0.12 and 0.3 < s and v > 0.75


def breeches(p, x, y):
    """The colonist's brown breeches (not the musket stock left of them)."""
    h, s, v = hsv(p)
    return (p[3] >= 128 and y >= 70 and y <= 93 and x >= 24
            and 0.03 < h < 0.14 and s > 0.35 and v < 0.55)


def stockings(p, x, y):
    h, s, v = hsv(p)
    return p[3] >= 128 and 89 <= y <= 112 and s < 0.12 and v > 0.6


def near_musket(x, y, d):
    (x0, y0), (x1, y1) = MUSKET
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
    return (x < 24 and y > 55 and near_musket(x, y, 6.5)
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


def recolor(im, test, color, lift=0.0):
    """Every pixel passing test(p, x, y) to the colour, same lightness."""
    px = im.load()
    pts = [(x, y) for y in range(im.height) for x in range(im.width)
           if test(px[x, y], x, y)]
    if not pts:
        return
    vs = [hsv(px[x, y])[2] for x, y in pts]
    lo, hi = min(vs), max(vs)
    for (x, y), v in zip(pts, vs):
        t = 0 if hi <= lo else (v - lo) / (hi - lo)
        a = px[x, y][3]
        c = ramp(color, min(1.0, t + lift))
        px[x, y] = c[:3] + (a,)


def drawn(paint, h=128):
    """Draw at SS times the size, then shrink: smooth edges."""
    big = Image.new('RGBA', (66 * SS, h * SS), (0, 0, 0, 0))
    paint(ImageDraw.Draw(big), lambda *v: tuple(int(c * SS) for c in v))
    small = big.convert('RGBa').resize((66, h), Image.BOX)
    # The game's figures are soft and painterly: no razor edges, and a
    # little grain in the cloth and steel.
    out = small.filter(ImageFilter.GaussianBlur(SOFT)).convert('RGBA')
    px, rnd = out.load(), random.Random(h * 7919 + len(paint.__name__))
    for y in range(h):
        for x in range(66):
            r, g, b, a = px[x, y]
            if a:
                f = 1 + rnd.uniform(-GRAIN, GRAIN)
                px[x, y] = (min(255, int(r * f)), min(255, int(g * f)),
                            min(255, int(b * f)), a)
    return out


def band(im, base, color, a, b, width):
    """A belt or sash along a-b, over cloth only (under musket, hands)."""
    px = im.load()
    (x0, y0), (x1, y1) = a, b
    dx, dy = x1 - x0, y1 - y0
    n = (dx * dx + dy * dy) ** 0.5
    for y in range(16, 68):
        for x in range(8, 58):
            p = base.getpixel((x, y))
            if not (coat(p) or shirt(p)) or on_top(base, x, y):
                continue
            t = ((x - x0) * dx + (y - y0) * dy) / (n * n)
            d = abs((x - x0) * dy - (y - y0) * dx) / n
            if -0.05 <= t <= 1.05 and d <= width:
                v = hsv(p)[2]
                edge = d > width - 0.8
                px[x, y] = ramp(color, (0.45 + 0.55 * v) * (0.8 if edge else 1))


def sash(im, base, color):
    """A sash from the right shoulder to the left hip, knotted there."""
    band(im, base, color, (20, 24), (45, 60), 2.4)
    dark = shade(color, 0.45)
    full = color + (255,)

    def knot(d, s):
        d.ellipse(s(42.2, 58.2, 47.6, 63.4), fill=dark)
        d.ellipse(s(42.8, 58.8, 47.0, 62.6), fill=full)
        d.polygon([s(43, 62), s(45.4, 62), s(44.6, 71), s(42.6, 70)],
                  fill=dark)
        d.polygon([s(43.5, 62), s(45, 62), s(44.2, 70.2), s(43.1, 69.6)],
                  fill=full)
        d.polygon([s(45.2, 62), s(47.4, 61.6), s(48.2, 68), s(46.4, 68.6)],
                  fill=dark)
        d.polygon([s(45.6, 62.2), s(47, 62), s(47.6, 67.4), s(46.6, 67.8)],
                  fill=shade(color, 0.8))
    im.alpha_composite(drawn(knot))


def crossbelts(im, base, color):
    """Two belts crossed on the chest: bandolier and sword belt."""
    band(im, base, color, (20, 23), (46, 61), 2.0)
    band(im, base, color, (39, 23), (19, 60), 2.0)


def collar(im, base, color=(236, 234, 226)):
    """A plain falling collar (or cravat) over the coat's neck."""
    px = im.load()
    for y in range(20, 26):
        for x in range(21, 38):
            p = base.getpixel((x, y))
            if coat(p) and abs(x - 29) <= 9 - (y - 20):
                v = hsv(p)[2]
                px[x, y] = ramp(color, 0.7 + 0.3 * v)


def clear_hat(im):
    """Take off the tricorne (keep the musket's muzzle on the right)."""
    px = im.load()
    for y in range(0, 12):
        for x in range(0, 46):
            p = px[x, y]
            if y >= 10 and skin(p):
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
    layer = drawn(paint)
    layer.alpha_composite(im)   # behind the head
    im.paste(layer, (0, 0))


# Hats.  Each draws on a 66 x 128 canvas through s().

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


# coat: colour of the coat above the belt; skirt: below it (None: the
# coat colour); breeches; legs: 'boots' (the veteran picture's) or
# 'hose' (stockings and shoes, the colonist picture's).
LOOKS = {
    # Militia: own clothes in drab colours, the nation's sash.
    ('dutch', 'soldier'): dict(coat=(70, 68, 66), breeches=(92, 70, 48),
                               sash=(236, 136, 38), hat=felt_hat),
    ('french', 'soldier'): dict(coat=(96, 86, 72), breeches=(92, 70, 48),
                                sash=(244, 242, 236), hat=felt_hat),
    ('english', 'soldier'): dict(coat=(128, 96, 62), breeches=(92, 70, 48),
                                 sash=(196, 30, 36), hat=felt_hat),
    # Veterans: the nation's kit.
    ('dutch', 'veteranSoldier'): dict(
        legs='hose', coat=(84, 96, 110), breeches=(178, 52, 34),
        stockings=(196, 84, 52), sash=(236, 136, 38), hat=morion),
    ('french', 'veteranSoldier'): dict(
        legs='boots', coat=(46, 70, 168), breeches=(92, 88, 96),
        sash=(244, 242, 236), hat=plumed_hat, hair=((92, 62, 36), 13)),
    ('english', 'veteranSoldier'): dict(
        legs='hose', coat=(176, 34, 34), cuffs=(40, 40, 52),
        breeches=(206, 202, 190),
        belts=(226, 180, 64), hat=round_hat, hair=((70, 46, 28), 13)),
}


def dress(nation, rank):
    look = LOOKS[(nation, rank)]
    base_name = 'veteranSoldier' if look.get('legs') == 'boots' else 'soldier'
    base = Image.open(U + 'soldier/%s.size2.png' % base_name).convert('RGBA')
    im = base.copy()
    top = lambda p, x, y: coat(p) and y < WAIST
    low = lambda p, x, y: coat(p) and y >= WAIST
    recolor(im, top, look['coat'])
    recolor(im, low, look.get('skirt') or look['coat'])
    if look.get('breeches'):
        if base_name == 'soldier':
            recolor(im, breeches, look['breeches'])
        else:
            recolor(im, lambda p, x, y: coat(p) and y >= 72, look['breeches'])
    if look.get('stockings'):
        recolor(im, stockings, look['stockings'], lift=0.15)
    collar(im, base)
    if look.get('sash'):
        sash(im, base, look['sash'])
    if look.get('belts'):
        crossbelts(im, base, look['belts'])
    clear_hat(im)
    if look.get('hair'):
        hair(im, *look['hair'])
    im.alpha_composite(drawn(look['hat']))
    return im


def half(im):
    """The small picture: half size, smooth (premultiplied alpha)."""
    return (im.convert('RGBa').resize((im.width // 2, im.height // 2),
                                      Image.LANCZOS).convert('RGBA'))


def main():
    out = sys.argv[1]
    for nation, rank in LOOKS:
        big = dress(nation, rank)
        stem = os.path.join(out, nation, rank)
        os.makedirs(os.path.dirname(stem), exist_ok=True)
        big.save(stem + '.size2.png')
        half(big).save(stem + '.png')
    if len(sys.argv) > 2:
        preview(out, sys.argv[2])


def preview(out, path):
    """The game's soldiers, then each nation's colonist and veteran,
    big and on a grass tile at map size."""
    from PIL import ImageFont
    tile = Image.open('data/default/resources/images/terrain/grassland/'
                      'center0.png').convert('RGBA')
    try:
        font = ImageFont.truetype('georgia.ttf', 22)
        small = ImageFont.truetype('georgia.ttf', 15)
    except OSError:
        font = small = ImageFont.load_default()
    cols = [('Game now (1770s)', None)] + [
        (n.capitalize(), n) for n in ('dutch', 'french', 'english')]
    Z, TS = 3, 1.6
    cw, H = 2 * 66 * Z + 40, 128 * Z
    tw, th = int(tile.width * TS), int(tile.height * TS)
    sheet = Image.new('RGBA', (len(cols) * cw + 20, 70 + H + 40 + th + 20),
                      (236, 226, 200, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, n) in enumerate(cols):
        x = 20 + i * cw
        d.text((x, 20), name, font=font, fill=(60, 38, 20))

        def get(rank, sfx):
            f = (U + 'soldier/%s%s.png' % (rank, sfx) if n is None
                 else os.path.join(out, n, rank + sfx + '.png'))
            return Image.open(f).convert('RGBA')
        for j, rank in enumerate(('soldier', 'veteranSoldier')):
            sheet.alpha_composite(get(rank, '.size2').resize(
                (66 * Z, 128 * Z), Image.NEAREST), (x + j * (66 * Z + 10), 60))
            d.text((x + j * (66 * Z + 10) + 70, 60 + H + 2),
                   ('colonist', 'veteran')[j], font=small, fill=(110, 80, 50))
            t = tile.copy()
            s = get(rank, '')
            t.alpha_composite(s, ((t.width - s.width) // 2,
                                  t.height - s.height - 4))
            sheet.alpha_composite(t.resize((tw, th), Image.NEAREST),
                                  (x + j * (tw + 12), 60 + H + 36))
    sheet.save(path)


main()
