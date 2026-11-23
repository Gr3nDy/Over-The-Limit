"""Build Minecraft GUI-scale PNG sprites for OTL's module desk."""
from pathlib import Path
import re
from PIL import Image, ImageDraw, ImageFont

out = Path(__file__).parents[1] / "src/main/resources/assets/otl/textures/menu/ui.png"
out.parent.mkdir(parents=True, exist_ok=True)
im = Image.new("RGBA", (256, 160))
d = ImageDraw.Draw(im)

# The first skin is the only translucent surface. Cards and controls stay solid.
skins = [
    ((23, 28, 27, 144), (80, 94, 83, 200)),  # menu backdrop
    ((35, 41, 37, 255), (74, 85, 76, 255)),   # profile rail
    ((20, 25, 22, 255), (63, 74, 66, 255)),   # profile list
    ((37, 45, 41, 255), (75, 89, 80, 255)),   # card
    ((49, 60, 53, 255), (127, 153, 133, 255)), # hovered card
    ((47, 65, 64, 255), (71, 94, 88, 255)),   # artwork well
    ((41, 50, 45, 255), (78, 91, 81, 255)),   # control
    ((69, 79, 69, 255), (125, 148, 126, 255)), # hovered control
    ((60, 128, 88, 255), (104, 166, 122, 255)), # enabled
    ((159, 57, 71, 255), (190, 90, 98, 255)), # disabled
    ((101, 107, 103, 255), (136, 145, 139, 255)), # locked
    ((51, 60, 53, 255), (255, 186, 116, 255)), # selected profile
    ((52, 130, 209, 255), (117, 176, 240, 255)), # selected category
]
for i, (fill, border) in enumerate(skins):
    x, y = i % 8 * 24, 40 + i // 8 * 24
    d.rounded_rectangle((x, y, x + 23, y + 23), radius=4, fill=fill, outline=border, width=1)
    # One-pixel corner and edge detail; the nine-slice stretches only its middle pixels.
    d.line((x + 5, y + 2, x + 18, y + 2), fill=(255, 255, 255, 12), width=1)

white = (239, 241, 233, 255)
muted = (182, 196, 181, 255)
accent = (255, 186, 116, 255)

# The wordmark traces the OTL paths from the approved HTML mockup.
def skew(points):
    return [(round(x + 12 - 8 * y / 30), y) for x, y in points]

d.polygon(skew([(4, 2), (24, 2), (27, 5), (27, 24), (23, 28), (3, 28), (0, 25), (0, 6)]), fill=white)
d.polygon(skew([(8, 9), (19, 9), (19, 21), (8, 21)]), fill=(0, 0, 0, 0))
d.polygon(skew([(30, 2), (56, 2), (56, 9), (47, 9), (47, 28), (39, 28), (39, 9), (30, 9)]), fill=white)
d.polygon(skew([(59, 2), (67, 2), (67, 21), (83, 21), (83, 28), (59, 28)]), fill=white)
d.polygon([(82, 2), (94, 2), (92, 8), (80, 8)], fill=accent)
d.text((5, 29), "OVER THE LIMIT", font=ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 9),
       fill=accent)

def glyph(index, y, ink):
    x = index * 16
    if index == 0:  # gear
        d.rectangle((x + 4, y + 4, x + 11, y + 11), fill=ink)
        d.rectangle((x + 6, y + 6, x + 9, y + 9), fill=(0, 0, 0, 0))
        for xx, yy in ((6, 1), (6, 12), (1, 6), (12, 6)):
            d.rectangle((x + xx, y + yy, x + xx + 3, y + yy + 2), fill=ink)
        for xx, yy in ((3, 3), (10, 3), (3, 10), (10, 10)):
            d.rectangle((x + xx, y + yy, x + xx + 2, y + yy + 2), fill=ink)
    elif index == 1:  # single sheet
        d.polygon([(x + 3, y + 1), (x + 10, y + 1), (x + 13, y + 4),
                   (x + 13, y + 14), (x + 3, y + 14)], outline=ink)
        d.line((x + 10, y + 1, x + 10, y + 4, x + 13, y + 4), fill=ink)
        for yy in (7, 10):
            d.line((x + 5, y + yy, x + 11, y + yy), fill=ink)
    elif index == 2:  # pencil
        d.line((x + 3, y + 11, x + 10, y + 4), fill=ink, width=3)
        d.polygon([(x + 2, y + 11), (x + 2, y + 14), (x + 5, y + 14)], fill=ink)
        d.line((x + 11, y + 3, x + 13, y + 5), fill=ink, width=2)
    elif index == 3:  # close
        d.line((x + 4, y + 4, x + 11, y + 11), fill=ink, width=2)
        d.line((x + 11, y + 4, x + 4, y + 11), fill=ink, width=2)
    elif index == 4:  # search
        d.ellipse((x + 2, y + 2, x + 10, y + 10), outline=ink, width=2)
        d.line((x + 10, y + 10, x + 14, y + 14), fill=ink, width=2)
    elif index == 5:  # delete
        d.rectangle((x + 5, y + 5, x + 11, y + 13), outline=ink, width=1)
        d.line((x + 3, y + 4, x + 13, y + 4), fill=ink, width=2)
        d.line((x + 6, y + 2, x + 10, y + 2), fill=ink, width=2)
    elif index == 6:  # gear with return arrow, module reset
        d.arc((x + 3, y + 3, x + 12, y + 12), 20, 300, fill=ink, width=2)
        d.rectangle((x + 7, y + 1, x + 9, y + 3), fill=ink)
        d.rectangle((x + 12, y + 6, x + 14, y + 8), fill=ink)
        d.rectangle((x + 7, y + 12, x + 9, y + 14), fill=ink)
        d.line((x + 3, y + 7, x + 3, y + 11, x + 7, y + 11), fill=ink, width=2)
    elif index == 7:  # back
        d.line((x + 10, y + 3, x + 5, y + 8, x + 10, y + 13), fill=ink, width=2)
    elif index == 8:  # add
        d.line((x + 8, y + 2, x + 8, y + 13), fill=ink, width=2)
        d.line((x + 2, y + 8, x + 13, y + 8), fill=ink, width=2)
    elif index == 9:  # import
        d.rectangle((x + 2, y + 10, x + 13, y + 14), outline=ink)
        d.line((x + 8, y + 1, x + 8, y + 10), fill=ink, width=2)
        d.line((x + 4, y + 6, x + 8, y + 10, x + 12, y + 6), fill=ink, width=2)

for row, color in ((0, muted), (1, white)):
    for idx in range(10):
        glyph(idx, 96 + 16 * row, accent if row and idx == 6 else color)

for hover in (False, True):
    for on in (True, False):
        x = (64 if hover else 0) + (0 if on else 32)
        bg = "#47825d" if on else "#9f3947"
        d.rounded_rectangle((x, 128, x + 25, 139), radius=5, fill=bg,
                            outline="#91d4a2" if on else "#d47782")
        d.ellipse((x + (15 if on else 2), 130, x + (23 if on else 10), 138),
                  fill="#f4f1e8")

assert im.getpixel((12, 50))[3] == 144
assert im.getpixel((84, 50))[3] == 255
assert im.getpixel((0, 20))[3] == 0 and im.getpixel((10, 12))[3] != 0
im.save(out, optimize=True)
labels = out.parent / "labels"
labels.mkdir(exist_ok=True)
font = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 11)

def label(filename, text, color):
    image = Image.new("RGBA", (len(text) * 7, 13))
    painter = ImageDraw.Draw(image)
    for i, char in enumerate(text):
        painter.text((i * 7, -1), char, fill=color, font=font)
    # The game magnifies GUI pixels; keep the letters sharp rather than half-transparent.
    image.putalpha(image.getchannel("A").point(lambda a: 255 if a > 100 else 0))
    assert image.getbbox(), filename
    image.save(labels / (filename + ".png"), optimize=True)

modules = {}
source = out.parents[5] / "java/otl/client/modules"
for path in source.glob("*.java"):
    match = re.search(r'super\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"', path.read_text("utf-8"))
    if match:
        modules[match.group(1)] = match.group(2)
# shortcut: the shipped menu has 14 modules; update this check if discovery gains another.
assert len(modules) == 14, modules
for module_id, name in modules.items():
    label(module_id, name, white)
for name in ("ENABLED", "DISABLED", "LOCKED"):
    label(name.lower(), name, white)
for word in ("MODULES", "SETTINGS"):
    label("nav_" + word.lower(), word, muted)
    label("nav_" + word.lower() + "_active", word, accent)

small = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 9)
for filename, caption, icon in (("profile_new", "New profile", 8),
                                ("profile_import", "Import profile", 9)):
    for hover in (False, True):
        button = Image.new("RGBA", (100, 20))
        painter = ImageDraw.Draw(button)
        painter.rounded_rectangle((0, 0, 99, 19), radius=3,
                                  fill="#454f45" if hover else "#303933",
                                  outline="#7d947e" if hover else "#4e5b50")
        button.alpha_composite(im.crop((icon * 16, 112 if hover else 96,
                                        icon * 16 + 16, 128 if hover else 112)), (3, 2))
        painter.text((21, 3), caption, font=small, fill=white)
        button.save(out.parent / (filename + ("_hover" if hover else "") + ".png"), optimize=True)
print(out)
