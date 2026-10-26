"""Armed Indian converts (mod): the armed / mounted brave pictures with
the convert's look (no feather, blue trousers).  Reads the game's own
images, writes new ones; run from the repo root.

    python convert_art.py <out dir> [preview.png]
"""
import colorsys
import os
import sys

from PIL import Image

U = 'data/default/resources/images/units/'


def hsv(p):
    r, g, b, a = p
    return colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)


def dark(p):
    return p[3] >= 128 and hsv(p)[2] < 0.25


def blue(p):
    return p[3] >= 128 and p[2] > p[0] and p[2] > p[1]


def trousers(p):
    """The convert's bright blue trousers (not his blue-black hair)."""
    h, s, v = hsv(p)
    return blue(p) and s > 0.4 and v > 0.35


def stock(p):
    """The musket's yellow wooden stock."""
    h, s, v = hsv(p)
    return p[3] >= 128 and 0.09 < h < 0.17 and s > 0.5 and v > 0.55


def skin(p):
    h, s, v = hsv(p)
    return p[3] >= 128 and h < 0.12 and s > 0.42 and v >= 0.25


def hair_left(im, rows):
    xs = [x for y in range(rows) for x in range(im.width)
          if dark(im.getpixel((x, y)))]
    return min(xs) if xs else 0


def no_feather(im, scale):
    """Clear the feather: whatever sticks out left of the hair at the
    top of the head (the hair's edge is taken just below the feather)."""
    xs = [x for y in range(6 * scale, 9 * scale) for x in range(im.width)
          if dark(im.getpixel((x, y)))]
    left = (min(xs) if xs else 0) - (scale - 1)
    for y in range(6 * scale):
        for x in range(max(0, left)):
            p = im.getpixel((x, y))
            if p[3] and not skin(p):
                im.putpixel((x, y), (0, 0, 0, 0))


def blue_ramp(convert):
    """The convert's trouser blues, darkest first."""
    bs = sorted({convert.getpixel((x, y)) for x in range(convert.width)
                 for y in range(convert.height)
                 if trousers(convert.getpixel((x, y)))},
                key=lambda p: sum(p[:3]))
    return bs


def to_blue(p, ramp, lo, hi):
    """A cloth pixel to the trouser blue of the same lightness."""
    v = hsv(p)[2]
    t = 0 if hi <= lo else max(0.0, min(1.0, (v - lo) / (hi - lo)))
    return ramp[int(t * (len(ramp) - 1))]


def soldier(scale, ramp):
    """The armed brave: no feather, his loincloth and leggings in the
    convert's blue from the waist to the knees (where the convert's
    trousers are)."""
    sfx = '' if scale == 1 else '.size2'
    out = Image.open(U + 'armedBrave/brave%s.png' % sfx).convert('RGBA')
    conv = Image.open(U + 'civilian/indianConvert%s.png' % sfx).convert('RGBA')
    no_feather(out, scale)
    rows = [y for y in range(conv.height)
            if sum(trousers(conv.getpixel((x, y)))
                   for x in range(conv.width)) > 3 * scale]
    return recolor(out, (0, rows[0], out.width,
                         min(out.height, rows[-1] + 1 + 2 * scale)), ramp)


def recolor(im, box, ramp):
    """Cloth in a box (dull light pixels, not the musket) to blue."""
    x0, y0, x1, y1 = box
    cloth = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            p = im.getpixel((x, y))
            h, s, v = hsv(p)
            # Warm cloth only: the steel barrel is cool grey.
            if (p[3] >= 128 and s < 0.45 and v > 0.3 and not stock(p)
                    and p[0] >= p[2] - 4):
                cloth.append((x, y, v))
    if cloth:
        lo = min(c[2] for c in cloth)
        hi = max(c[2] for c in cloth)
        for x, y, v in cloth:
            im.putpixel((x, y), to_blue(im.getpixel((x, y)), ramp, lo, hi))
    return im


def rider(name, scale, box, ramp):
    """A mounted brave: no feather, the lap in the convert's blue."""
    sfx = '' if scale == 1 else '.size2'
    im = Image.open(U + name + '/brave%s.png' % sfx).convert('RGBA')
    no_feather(im, scale)
    return recolor(im, [v * scale for v in box], ramp)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    conv = Image.open(U + 'civilian/indianConvert.png').convert('RGBA')
    made = []
    # The rider's lap (loincloth, thigh), 1x coordinates.
    LAP = (19, 26, 31, 33)
    for scale in (1, 2):
        sfx = '' if scale == 1 else '.size2'
        ramp = blue_ramp(Image.open(U + 'civilian/indianConvert%s.png' % sfx)
                         .convert('RGBA'))
        for name, im in (
                ('convertSoldier', soldier(scale, ramp)),
                ('convertDragoon', rider('nativeDragoon', scale, LAP, ramp)),
                ('convertScout', rider('mountedBrave', scale, LAP, ramp))):
            p = os.path.join(out, name + sfx + '.png')
            im.save(p)
            made.append(p)
    if len(sys.argv) > 2:
        refs = [U + 'civilian/indianConvert.png', U + 'armedBrave/brave.png',
                os.path.join(out, 'convertSoldier.png'),
                U + 'nativeDragoon/brave.png',
                os.path.join(out, 'convertDragoon.png'),
                os.path.join(out, 'convertScout.png'),
                U + 'dragoon/dragoon.png']
        ims = [Image.open(f).convert('RGBA') for f in refs]
        H = max(i.height for i in ims)
        sheet = Image.new('RGBA', (sum(i.width + 8 for i in ims), H),
                          (235, 228, 210, 255))
        x = 0
        for i in ims:
            sheet.paste(i, (x, H - i.height), i)
            x += i.width + 8
        sheet = sheet.resize((sheet.width * 4, sheet.height * 4),
                             Image.NEAREST)
        sheet.save(sys.argv[2])
    print('\n'.join(made))


main()
